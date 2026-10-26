package dev.itonk.languagemod.client;

import dev.itonk.languagemod.LanguageMod;
import dev.itonk.languagemod.challenge.ChallengeManager;
import dev.itonk.languagemod.client.voice.VoiceInput;
import dev.itonk.languagemod.network.HudPayloads;
import dev.itonk.languagemod.progress.ProgressStore;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;

import java.nio.file.Path;
import java.util.List;

/**
 * Runs the same rules (ChallengeManager) inside this game when the server doesn't have the
 * mod. Decides a couple of seconds after joining, once the server has announced its mod
 * channels. Progress is kept per server under config/languagemod/servers/. Client thread only.
 */
public final class ClientOnlyMode {
	/** Ticks to wait after joining before deciding (the server's channel list arrives just after join). */
	private static final int DECIDE_AFTER_TICKS = 40;

	private ChallengeManager rules;
	private ClientChallengePlayer player;
	private ClientPlayerEntity lastEntity;
	private int ticksSinceJoin = -1;
	private long clock;

	public boolean active() {
		return rules != null;
	}

	void onJoin() {
		ticksSinceJoin = 0;
	}

	void onDisconnect() {
		if (rules != null) rules.onLeave(player);
		rules = null;
		player = null;
		lastEntity = null;
		ticksSinceJoin = -1;
		LanguageModClient.VOICE.setRoute(VoiceInput.TO_SERVER);
	}

	void tick(MinecraftClient client) {
		if (client.player == null || client.getNetworkHandler() == null || client.isPaused()) return;
		if (rules == null) {
			if (ticksSinceJoin < 0 || ++ticksSinceJoin < DECIDE_AFTER_TICKS) return;
			ticksSinceJoin = -1;
			if (ClientPlayNetworking.canSend(HudPayloads.DictionaryRequest.ID)) return; // the server runs the mod
			start(client);
		}
		if (client.player != lastEntity) {
			// A new player entity means we died and respawned: the open question is void.
			if (lastEntity != null) rules.onRespawn(player);
			lastEntity = client.player;
		}
		rules.tick(++clock, List.of(player));
	}

	private void start(MinecraftClient client) {
		ChallengeManager shared = LanguageMod.challenges();
		rules = new ChallengeManager(shared.config(), shared.dictionary());
		rules.setStore(new ProgressStore(serverDir(client)));
		player = new ClientChallengePlayer(client);
		lastEntity = client.player;
		LanguageModClient.VOICE.setRoute(new VoiceInput.AnswerRoute() {
			@Override public void answer(String text) {
				if (rules != null) rules.submitAnswer(player, text);
			}

			@Override public void busy(boolean busy) {
				if (rules != null) rules.setVoiceBusy(player.uuid(), busy);
			}
		});
		LanguageMod.LOG.info("[client-only] server has no LanguageMod; running the rules in this game ({})", serverDir(client));
		rules.onJoin(player);
	}

	/** A plain chat line while a word is asked is the answer, and never reaches the server. */
	boolean onChat(String message) {
		if (rules == null || !rules.config().chatAnswers || !rules.isFrozen(player.uuid())) return false;
		rules.submitAnswer(player, message);
		return true;
	}

	List<HudPayloads.DictionaryEntry> snapshot() {
		return rules.snapshot(player.uuid());
	}

	/** For the bot test. */
	public int snapshotSize() {
		return rules == null ? 0 : snapshot().size();
	}

	private static Path serverDir(MinecraftClient client) {
		ServerInfo info = client.getCurrentServerEntry();
		String name = info != null ? info.address : "local";
		return FabricLoader.getInstance().getConfigDir().resolve("languagemod").resolve("servers")
				.resolve(name.replaceAll("[^A-Za-z0-9._-]", "_"));
	}
}
