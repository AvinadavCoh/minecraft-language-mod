package dev.itonk.languagemod;

import dev.itonk.languagemod.challenge.ChallengeManager;
import dev.itonk.languagemod.command.LanguageCommands;
import dev.itonk.languagemod.config.ModConfig;
import dev.itonk.languagemod.dictionary.TranslationDictionary;
import dev.itonk.languagemod.network.HudPayloads;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/** Wiring only: loads data files and connects Fabric events to the ChallengeManager. */
public final class LanguageMod implements ModInitializer {
	public static final Logger LOG = LoggerFactory.getLogger("languagemod");
	private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("languagemod");

	private static ChallengeManager challenges;
	private static volatile MinecraftServer server;

	@Override
	public void onInitialize() {
		try {
			ModConfig config = ModConfig.load(CONFIG_DIR);
			challenges = new ChallengeManager(config, TranslationDictionary.load(CONFIG_DIR, config.targetLanguage));
			ModConfig.copyDefaultIfMissing("es.json", CONFIG_DIR.resolve("es.json")); // second language, ready to switch to
		} catch (IOException e) {
			throw new IllegalStateException("[LanguageMod] could not load config/dictionary", e);
		}

		HudPayloads.register();

		ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);

		ServerTickEvents.END_SERVER_TICK.register(s -> challenges.tick(s));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> challenges.onJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> challenges.onLeave(handler.player));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> challenges.onRespawn(newPlayer));

		// While frozen the player can't place, use or break with the item either.
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> blockIfFrozen(player));
		UseItemCallback.EVENT.register((player, world, hand) -> blockIfFrozen(player));
		AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> blockIfFrozen(player));

		// Voice answers from the client's speech-to-text. Hand off to the server thread explicitly.
		ServerPlayNetworking.registerGlobalReceiver(HudPayloads.VoiceAnswer.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> challenges.submitAnswer(player, payload.text()));
		});

		// While a challenge is open, a plain chat line is the answer (not broadcast to others).
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			if (!challenges.config().chatAnswers || !challenges.isFrozen(sender)) return true;
			challenges.submitAnswer(sender, message.getSignedContent());
			return false;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> LanguageCommands.register(dispatcher));
	}

	private static ActionResult blockIfFrozen(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity sp && challenges.isFrozen(sp)) return ActionResult.FAIL;
		return ActionResult.PASS;
	}

	public static ChallengeManager challenges() {
		return challenges;
	}

	/** True only on the logical server's main thread (never the Netty or client thread). */
	public static boolean onServerThread() {
		MinecraftServer s = server;
		return s != null && s.isOnThread();
	}

	/** Re-reads config.json and the dictionary. Runs on the server thread (from a command). */
	public static String reload() {
		try {
			ModConfig config = ModConfig.load(CONFIG_DIR);
			TranslationDictionary dictionary = TranslationDictionary.load(CONFIG_DIR, config.targetLanguage);
			challenges.reload(config, dictionary);
			return "Reloaded: " + config.targetLanguageName + " (" + config.targetLanguage + ")";
		} catch (IOException | RuntimeException e) {
			LOG.error("Reload failed", e);
			return "Reload failed: " + e.getMessage();
		}
	}
}
