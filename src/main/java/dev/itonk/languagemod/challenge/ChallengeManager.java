package dev.itonk.languagemod.challenge;

import dev.itonk.languagemod.config.ModConfig;
import dev.itonk.languagemod.dictionary.LetterDiff;
import dev.itonk.languagemod.dictionary.TranslationDictionary;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The game rules: which items need naming, the timer, verify / punish.
 *
 * Threading contract: every method except {@link #submitAnswerFromAnyThread} must run on
 * the server thread. Fabric's tick events, command callbacks and the interaction
 * callbacks all already do, so no locking is needed.
 */
public final class ChallengeManager {
	private final Map<UUID, PlayerChallengeState> states = new HashMap<>();
	private ModConfig config;
	private TranslationDictionary dictionary;

	public ChallengeManager(ModConfig config, TranslationDictionary dictionary) {
		this.config = config;
		this.dictionary = dictionary;
	}

	public void reload(ModConfig config, TranslationDictionary dictionary) {
		this.config = config;
		this.dictionary = dictionary;
	}

	public ModConfig config() {
		return config;
	}

	public PlayerChallengeState state(ServerPlayerEntity player) {
		return states.computeIfAbsent(player.getUuid(), id -> new PlayerChallengeState());
	}

	public boolean isFrozen(ServerPlayerEntity player) {
		PlayerChallengeState state = states.get(player.getUuid());
		return state != null && state.isFrozen();
	}

	// ---- lifecycle -----------------------------------------------------------------

	public void onJoin(ServerPlayerEntity player) {
		PlayerChallengeState state = state(player);
		if (!config.challengeItemsHeldOnJoin) {
			// Baseline: whatever they log in with counts as already known.
			PlayerInventory inv = player.getInventory();
			for (int i = 0; i < inv.size(); i++) {
				ItemStack stack = inv.getStack(i);
				if (!stack.isEmpty()) state.verified.add(InventoryOps.idOf(stack));
			}
		}
	}

	public void onLeave(ServerPlayerEntity player) {
		FreezeControl.unfreeze(player);
		PlayerChallengeState state = states.get(player.getUuid());
		if (state != null) {
			// Don't let a disconnect dodge the question: re-ask after rejoin.
			if (state.active != null) state.queue.addFirst(state.active.itemId());
			state.active = null;
		}
	}

	/** After death the items dropped, so the open question is void. */
	public void onRespawn(ServerPlayerEntity newPlayer) {
		PlayerChallengeState state = state(newPlayer);
		state.active = null;
		state.queue.clear();
		FreezeControl.unfreeze(newPlayer);
	}

	// ---- tick ----------------------------------------------------------------------

	public void tick(MinecraftServer server) {
		long now = server.getTicks();
		boolean scan = now % Math.max(1, config.scanIntervalTicks) == 0;
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			if (!player.isAlive()) continue;
			if (config.skipCreativeAndSpectator && (player.isCreative() || player.isSpectator())) continue;

			PlayerChallengeState state = state(player);
			if (scan) scanInventory(player, state);
			if (state.active == null) startNext(player, state, now);
			else if (now >= state.active.deadlineTick()) fail(player, state, "Time's up!");
		}
	}

	/** Loops every inventory slot and queues item types the player hasn't named yet. */
	private void scanInventory(ServerPlayerEntity player, PlayerChallengeState state) {
		PlayerInventory inv = player.getInventory();
		for (int slot = 0; slot < inv.size(); slot++) {
			ItemStack stack = inv.getStack(slot);
			if (stack.isEmpty()) continue;
			Identifier id = InventoryOps.idOf(stack);
			if (state.isKnown(id)) continue;
			if (!dictionary.hasEntry(id) && config.ignoreItemsMissingFromDictionary) {
				state.verified.add(id); // can't be checked, so don't punish
				continue;
			}
			state.queue.addLast(id);
		}
	}

	private void startNext(ServerPlayerEntity player, PlayerChallengeState state, long now) {
		PlayerInventory inv = player.getInventory();
		while (!state.queue.isEmpty()) {
			Identifier id = state.queue.pollFirst();
			int slot = InventoryOps.findSlot(inv, id);
			if (slot < 0) continue; // used up / gone before we got to it; ask again if it comes back
			int duration = config.timeoutSeconds * 20;
			String word = dictionary.exampleAnswer(id).orElse("?");
			state.active = new PlayerChallengeState.Challenge(id, slot, now + duration, word);
			if (config.freezeMovement) FreezeControl.freeze(player);
			ChallengeEffects.prompt(player, inv.getStack(slot), word, config.targetLanguageName, duration);
			return;
		}
	}

	// ---- answers -------------------------------------------------------------------

	/** Gateway for /verify, plain chat and speech. Returns false if the player had nothing to answer. */
	public boolean submitAnswer(ServerPlayerEntity player, String input) {
		PlayerChallengeState state = state(player);
		PlayerChallengeState.Challenge challenge = state.active;
		if (challenge == null) return false;

		if (dictionary.matches(challenge.itemId(), input)) {
			int slot = locate(player.getInventory(), challenge);
			ItemStack shown = slot >= 0 ? player.getInventory().getStack(slot) : ItemStack.EMPTY;
			state.verified.add(challenge.itemId());
			clear(player, state);
			ChallengeEffects.success(player, shown);
		} else if (config.wrongAnswerPunishes) {
			fail(player, state, "\"" + input + "\" is wrong!");
		} else {
			// Retry until the timer runs out; show which letters were right.
			ChallengeEffects.attempt(player, input.trim(), challenge.word(), LetterDiff.matchMask(challenge.word(), input));
		}
		return true;
	}

	/**
	 * Hook for a speech-to-text engine (Whisper etc.) that delivers results on its own
	 * thread. Never touch game state from that thread: hand the work to the server
	 * thread with server.execute(...), which queues it for the start of the next tick.
	 */
	public void submitAnswerFromAnyThread(MinecraftServer server, UUID playerId, String transcript) {
		server.execute(() -> {
			ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
			if (player != null) submitAnswer(player, transcript);
		});
	}

	// ---- punishment ----------------------------------------------------------------

	private void fail(ServerPlayerEntity player, PlayerChallengeState state, String reason) {
		PlayerChallengeState.Challenge challenge = state.active;
		int slot = locate(player.getInventory(), challenge);
		ItemStack removed = slot >= 0 ? InventoryOps.deleteStack(player, slot) : ItemStack.EMPTY;

		// The item type stays unverified: picking it up again asks again.
		clear(player, state);
		ChallengeEffects.failure(player, removed, reason,
				dictionary.exampleAnswer(challenge.itemId()).orElse(null));
		player.damage(player.getEntityWorld(), player.getDamageSources().generic(), config.damage);
	}

	/** The challenged slot, or wherever the item ended up if it moved (e.g. hotbar swap). */
	private static int locate(PlayerInventory inv, PlayerChallengeState.Challenge challenge) {
		if (InventoryOps.slotHolds(inv, challenge.slot(), challenge.itemId())) return challenge.slot();
		return InventoryOps.findSlot(inv, challenge.itemId());
	}

	private static void clear(ServerPlayerEntity player, PlayerChallengeState state) {
		state.active = null;
		FreezeControl.unfreeze(player);
	}
}
