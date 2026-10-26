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
import dev.itonk.languagemod.challenge.InventoryOps;
import dev.itonk.languagemod.challenge.ServerChallengePlayer;
import dev.itonk.languagemod.progress.ProgressStore;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.WorldSavePath;
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
	/** False = act like a server without the mod (bot test for client-only mode). */
	private static volatile boolean serverRules = true;

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

		ServerLifecycleEvents.SERVER_STARTED.register(s -> {
			server = s;
			challenges.setStore(new ProgressStore(s.getSavePath(WorldSavePath.ROOT)));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(s -> challenges.saveAll());
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);

		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (serverRules) challenges.tick(s);
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, s) -> {
			if (serverRules) challenges.onJoin(new ServerChallengePlayer(handler.player));
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, s) -> challenges.onLeave(new ServerChallengePlayer(handler.player)));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> challenges.onRespawn(new ServerChallengePlayer(newPlayer)));

		// While a word is asked, the asked item can't be placed or used up (that would dodge the question).
		// Everything else (mining, other items) keeps working.
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> blockIfAskedItem(player, hand));
		UseItemCallback.EVENT.register((player, world, hand) -> blockIfAskedItem(player, hand));

		registerReceivers();

		// While a challenge is open, a plain chat line is the answer (not broadcast to others).
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			if (!serverRules || !challenges.config().chatAnswers || !challenges.isFrozen(sender)) return true;
			challenges.submitAnswer(sender, message.getSignedContent());
			return false;
		});

		CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> LanguageCommands.register(dispatcher));
	}

	/** The mod's C2S channels; a client sees them in the server's channel list and knows the server runs the mod. */
	private static void registerReceivers() {
		// Voice answers from the client's speech-to-text. Hand off to the server thread explicitly.
		ServerPlayNetworking.registerGlobalReceiver(HudPayloads.VoiceAnswer.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> challenges.submitAnswer(player, payload.text()));
		});

		ServerPlayNetworking.registerGlobalReceiver(HudPayloads.VoiceBusy.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> challenges.setVoiceBusy(player, payload.busy()));
		});
		ServerPlayNetworking.registerGlobalReceiver(HudPayloads.DictionaryRequest.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			context.server().execute(() -> ServerPlayNetworking.send(player,
					new HudPayloads.DictionarySnapshot(challenges.snapshot(player))));
		});
	}

	/**
	 * Test hook: off = this server behaves as if it had no LanguageMod (no rules, no channels),
	 * so a client joining a new world afterwards runs client-only mode.
	 */
	public static void setServerRules(boolean on) {
		if (on == serverRules) return;
		serverRules = on;
		if (on) {
			registerReceivers();
		} else {
			ServerPlayNetworking.unregisterGlobalReceiver(HudPayloads.VoiceAnswer.ID.id());
			ServerPlayNetworking.unregisterGlobalReceiver(HudPayloads.VoiceBusy.ID.id());
			ServerPlayNetworking.unregisterGlobalReceiver(HudPayloads.DictionaryRequest.ID.id());
		}
	}

	private static ActionResult blockIfAskedItem(PlayerEntity player, Hand hand) {
		if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
		var challenge = challenges.state(sp).active();
		if (challenge == null) return ActionResult.PASS;
		ItemStack held = player.getStackInHand(hand);
		return !held.isEmpty() && InventoryOps.idOf(held).equals(challenge.itemId()) ? ActionResult.FAIL : ActionResult.PASS;
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
