package dev.itonk.languagemod.challenge;

import dev.itonk.languagemod.LanguageMod;
import dev.itonk.languagemod.config.ModConfig;
import dev.itonk.languagemod.dictionary.LetterDiff;
import dev.itonk.languagemod.dictionary.TranslationDictionary;
import dev.itonk.languagemod.network.HudPayloads;
import dev.itonk.languagemod.progress.ProgressStore;
import dev.itonk.languagemod.progress.WordProgress;
import dev.itonk.languagemod.progress.WordRecord;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The game rules: which word to ask next, the timer, verify / punish, and keeping each
 * player's dictionary (WordProgress) up to date and saved.
 *
 * Side-agnostic: players come in as {@link ChallengePlayer}. On a server with the mod the
 * server owns one instance (server thread only); in client-only mode the client owns its own
 * instance (client thread only). Either way one thread, so no locking is needed.
 */
public final class ChallengeManager {
	private static final int SAVE_EVERY_TICKS = 60 * 20;

	private final Map<UUID, PlayerChallengeState> states = new HashMap<>();
	private ModConfig config;
	private TranslationDictionary dictionary;
	private ProgressStore store;
	private boolean seedCarriedOnNextTick;
	/** Tick clock of the side that owns this manager (server ticks, or client ticks). */
	private long now;

	public ChallengeManager(ModConfig config, TranslationDictionary dictionary) {
		this.config = config;
		this.dictionary = dictionary;
	}

	public void reload(ModConfig config, TranslationDictionary dictionary) {
		if (!config.targetLanguage.equals(this.config.targetLanguage)) {
			// Each language has its own saved dictionary: store the old one, start the new one fresh.
			saveAll();
			states.clear();
			// Everything carried is new in the new language: space it out like on join, not all at once.
			seedCarriedOnNextTick = true;
		}
		this.config = config;
		this.dictionary = dictionary;
	}

	public ModConfig config() {
		return config;
	}

	public TranslationDictionary dictionary() {
		return dictionary;
	}

	/** Set when a world starts; progress lives in that world's save folder. */
	public void setStore(ProgressStore store) {
		this.store = store;
		states.clear();
	}

	public PlayerChallengeState state(UUID player) {
		return states.computeIfAbsent(player,
				id -> new PlayerChallengeState(store != null ? store.load(id, config.targetLanguage) : new WordProgress()));
	}

	public boolean isFrozen(UUID player) {
		PlayerChallengeState state = states.get(player);
		return state != null && state.isFrozen();
	}

	// Server-player shortcuts (wiring, commands, mixin, tests).
	public PlayerChallengeState state(ServerPlayerEntity player) { return state(player.getUuid()); }
	public boolean isFrozen(ServerPlayerEntity player) { return isFrozen(player.getUuid()); }
	public boolean submitAnswer(ServerPlayerEntity player, String input) { return submitAnswer(new ServerChallengePlayer(player), input); }
	public void setVoiceBusy(ServerPlayerEntity player, boolean busy) { setVoiceBusy(player.getUuid(), busy); }
	public List<HudPayloads.DictionaryEntry> snapshot(ServerPlayerEntity player) { return snapshot(player.getUuid()); }

	// ---- lifecycle -----------------------------------------------------------------

	public void onJoin(ChallengePlayer player) {
		int carried = seedCarried(player);
		boolean off = config.skipCreativeAndSpectator && player.isCreativeOrSpectator();
		player.welcome(config.targetLanguageName, carried, config.reviewGapSeconds, off);
	}

	/** Returns how many carried item types were queued as spaced-out reviews. */
	private int seedCarried(ChallengePlayer player) {
		PlayerChallengeState state = state(player.uuid());
		int carried = 0;
		if (!config.challengeItemsHeldOnJoin) {
			// Items carried in that were never asked: don't flood the player on join, bring
			// them in as reviews; the review gap spaces them out one by one.
			for (Identifier id : inventoryIds(player)) {
				String key = id.toString();
				if (!state.progress.has(key)) {
					state.progress.seenLater(key, 5 * 20L);
					carried++;
				}
			}
			state.dirty = true;
		}
		return carried;
	}

	public void onLeave(ChallengePlayer player) {
		player.unfreeze();
		PlayerChallengeState state = states.remove(player.uuid());
		// An open question stays due in the saved progress, so it comes back after rejoin.
		if (state != null) save(player.uuid(), state);
	}

	/** After death the items dropped, so the open question is void. */
	public void onRespawn(ChallengePlayer player) {
		PlayerChallengeState state = state(player.uuid());
		state.active = null;
		state.newPickups.clear();
		player.unfreeze();
	}

	public void saveAll() {
		states.forEach(this::save);
	}

	private void save(UUID id, PlayerChallengeState state) {
		if (store == null) return;
		try {
			store.save(id, config.targetLanguage, state.progress);
			state.dirty = false;
		} catch (Exception e) {
			LanguageMod.LOG.error("Could not save language progress for {}", id, e);
		}
	}

	// ---- tick ----------------------------------------------------------------------

	public void tick(MinecraftServer server) {
		List<ChallengePlayer> players = new ArrayList<>();
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) players.add(new ServerChallengePlayer(p));
		tick(server.getTicks(), players);
	}

	public void tick(long now, Iterable<? extends ChallengePlayer> players) {
		this.now = now;
		boolean scan = now % Math.max(1, config.scanIntervalTicks) == 0;
		boolean seed = seedCarriedOnNextTick;
		seedCarriedOnNextTick = false;
		for (ChallengePlayer player : players) {
			if (seed) seedCarried(player);
			PlayerChallengeState state = state(player.uuid());
			state.progress.playTicks++;
			if (state.dirty && state.progress.playTicks % SAVE_EVERY_TICKS == 0) save(player.uuid(), state);

			if (!player.isAlive()) continue;
			if (config.skipCreativeAndSpectator && player.isCreativeOrSpectator()) continue;

			if (scan) scanInventory(player, state);
			if (state.active == null) {
				startNext(player, state);
			} else if (now >= state.active.deadlineTick()) {
				// Time's up, but if the player is mid-word or Whisper is still working, wait for that answer.
				boolean waiting = state.voiceBusy && now < state.active.deadlineTick() + config.voiceGraceSeconds * 20L;
				if (!waiting) fail(player, state, "Time's up!");
			}
		}
	}

	/** Item types in the inventory that the dictionary can ask about. */
	private Set<Identifier> inventoryIds(ChallengePlayer player) {
		Set<Identifier> ids = new LinkedHashSet<>();
		PlayerInventory inv = player.inventory();
		for (int slot = 0; slot < inv.size(); slot++) {
			ItemStack stack = inv.getStack(slot);
			if (stack.isEmpty()) continue;
			Identifier id = InventoryOps.idOf(stack);
			if (dictionary.hasEntry(id) || !config.ignoreItemsMissingFromDictionary) ids.add(id);
		}
		return ids;
	}

	/** Loops every inventory slot and queues item types this player has never been asked. */
	private void scanInventory(ChallengePlayer player, PlayerChallengeState state) {
		for (Identifier id : inventoryIds(player)) {
			String key = id.toString();
			if (state.progress.has(key)) continue;
			state.progress.seen(key);
			state.newPickups.addLast(id);
			state.dirty = true;
		}
	}

	/** New pickups first; then, after a short gap, the highest-priority due review in the inventory. */
	private void startNext(ChallengePlayer player, PlayerChallengeState state) {
		PlayerInventory inv = player.inventory();
		while (!state.newPickups.isEmpty()) {
			Identifier id = state.newPickups.pollFirst();
			int slot = InventoryOps.findSlot(inv, id);
			if (slot >= 0) {
				start(player, state, id, slot, false);
				return;
			}
			// Gone before we got to it: it stays due in progress and comes back as a review.
		}
		if (state.progress.playTicks - state.lastEndPlayTick < config.reviewGapSeconds * 20L) return;
		List<String> held = new ArrayList<>();
		for (Identifier id : inventoryIds(player)) held.add(id.toString());
		state.progress.nextReview(held).ifPresent(key -> {
			Identifier id = Identifier.of(key);
			int slot = InventoryOps.findSlot(inv, id);
			if (slot >= 0) start(player, state, id, slot, true);
		});
	}

	private void start(ChallengePlayer player, PlayerChallengeState state, Identifier id, int slot, boolean review) {
		int duration = config.timeoutSeconds * 20;
		String word = dictionary.exampleAnswer(id).orElse("?");
		state.active = new PlayerChallengeState.Challenge(id, slot, now + duration, word, review);
		state.wrongTries = 0;
		state.voiceBusy = false;
		if (config.freezeMovement) player.freeze();
		player.prompt(player.inventory().getStack(slot), word, config.targetLanguageName, config.targetLanguage, duration);
	}

	// ---- answers -------------------------------------------------------------------

	/** Gateway for /verify, plain chat and speech. Returns false if the player had nothing to answer. */
	public boolean submitAnswer(ChallengePlayer player, String input) {
		PlayerChallengeState state = state(player.uuid());
		PlayerChallengeState.Challenge challenge = state.active;
		if (challenge == null) return false;

		if (dictionary.matches(challenge.itemId(), input, config.fuzzyMatch)) {
			int slot = locate(player.inventory(), challenge);
			ItemStack shown = slot >= 0 ? player.inventory().getStack(slot).copy() : ItemStack.EMPTY;
			state.progress.onCorrect(challenge.itemId().toString(), state.wrongTries, reviewSteps());
			endChallenge(player, state);
			player.success(shown);
			return true;
		}

		state.wrongTries++;
		if (config.wrongAnswerPunishes) {
			fail(player, state, "\"" + input + "\" is wrong!");
		} else if (now >= challenge.deadlineTick()) {
			// The answer we were waiting for (voice grace) came in wrong: now it's a miss.
			player.attempt(input.trim(), challenge.word(), LetterDiff.matchMask(challenge.word(), input));
			fail(player, state, "\"" + input.trim() + "\" is wrong!");
		} else {
			// Retry until the timer runs out; show which letters were right.
			player.attempt(input.trim(), challenge.word(), LetterDiff.matchMask(challenge.word(), input));
		}
		return true;
	}

	/** The client's speech-to-text is busy (player talking or Whisper working). */
	public void setVoiceBusy(UUID player, boolean busy) {
		PlayerChallengeState state = state(player);
		if (state.active != null) state.voiceBusy = busy;
	}

	// ---- punishment ----------------------------------------------------------------

	private void fail(ChallengePlayer player, PlayerChallengeState state, String reason) {
		PlayerChallengeState.Challenge challenge = state.active;
		boolean keep = !config.deleteItemOnFail;
		int slot = keep ? -1 : locate(player.inventory(), challenge);
		// Kept items wait retryDelaySeconds before coming back (otherwise: asked again at once,
		// every 15 s); a destroyed item is due again as soon as the player holds one.
		long retryTicks = keep ? config.retryDelaySeconds * 20L : 0;
		state.progress.onMiss(challenge.itemId().toString(), state.wrongTries, retryTicks);

		endChallenge(player, state);
		player.punish(slot, keep, reason, dictionary.exampleAnswer(challenge.itemId()).orElse(null), config.damage);
	}

	/** The challenged slot, or wherever the item ended up if it moved (e.g. hotbar swap). */
	private static int locate(PlayerInventory inv, PlayerChallengeState.Challenge challenge) {
		if (InventoryOps.slotHolds(inv, challenge.slot(), challenge.itemId())) return challenge.slot();
		return InventoryOps.findSlot(inv, challenge.itemId());
	}

	private void endChallenge(ChallengePlayer player, PlayerChallengeState state) {
		state.active = null;
		state.voiceBusy = false;
		state.lastEndPlayTick = state.progress.playTicks;
		state.dirty = true;
		save(player.uuid(), state);
		player.unfreeze();
	}

	private long[] reviewSteps() {
		long[] steps = new long[config.reviewStepsMinutes.length];
		for (int i = 0; i < steps.length; i++) steps[i] = (long) (config.reviewStepsMinutes[i] * 60 * 20);
		return steps;
	}

	// ---- dictionary screen -----------------------------------------------------------

	/** Everything this player has met, for the dictionary screen. */
	public List<HudPayloads.DictionaryEntry> snapshot(UUID player) {
		PlayerChallengeState state = state(player);
		WordProgress p = state.progress;
		List<HudPayloads.DictionaryEntry> out = new ArrayList<>();
		List<String> ids = new ArrayList<>(p.ids());
		ids.sort(p.priority());
		for (String key : ids) {
			Identifier id = Identifier.tryParse(key);
			if (id == null || !dictionary.hasEntry(id)) continue;
			WordRecord r = p.get(key);
			int dueIn = (int) Math.max(0, (r.dueAt - p.playTicks) / 20);
			out.add(new HudPayloads.DictionaryEntry(key, dictionary.exampleAnswer(id).orElse("?"),
					p.status(key).ordinal(), r.box, r.correct, r.misses, r.wrongTries, dueIn));
		}
		return out;
	}
}
