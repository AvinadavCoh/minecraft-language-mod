package dev.itonk.languagemod.test;

import dev.itonk.languagemod.LanguageMod;
import dev.itonk.languagemod.client.LanguageModClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bot playthrough of the challenge, using the real controls where a player would:
 * chat for /verify, the drop key, the walk key. Each step asserts the server-side result
 * and saves a screenshot to run/screenshots/.
 */
public class ChallengeClientGameTest implements FabricClientGameTest {
	/** Damage the player took, recorded on the server thread (peaceful regen would hide it from getHealth()). */
	private static volatile float lastDamage;
	/** Bumped by the test-only ParticleManagerMixin for every item-crack particle the client spawns. */
	public static final AtomicInteger crackParticles = new AtomicInteger();
	private static final List<String> heardSounds = new CopyOnWriteArrayList<>();
	/** PlayerScreenHandler slot index of hotbar slot 0. */
	private static final int HOTBAR_0 = 36;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayerEntity) lastDamage = taken;
		});
		ctx.runOnClient(c -> c.getSoundManager().registerListener(
				(sound, set, range) -> heardSounds.add(sound.getId().toString())));
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			sp.getServer().runCommand("gamemode survival @a");
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("difficulty peaceful");
			ctx.waitTicks(20);

			// 1. New item -> prompt + freeze
			sp.getServer().runCommand("give @p minecraft:oak_log 8");
			ctx.waitTicks(25);
			check(sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))), "oak log starts a challenge");
			double speed = sp.getServer().computeOnServer(s -> player(s).getAttributeValue(EntityAttributes.MOVEMENT_SPEED));
			check(speed > 0.0, "movement is NOT frozen by default (speed " + speed + ")");
			ctx.takeScreenshot("01-prompt");

			// 2. Walking still works while the word is asked (freezeMovement=false is the default)
			Vec3d before = sp.getServer().computeOnServer(s -> player(s).getEntityPos());
			ctx.getInput().holdKeyFor(o -> o.forwardKey, 30);
			Vec3d after = sp.getServer().computeOnServer(s -> player(s).getEntityPos());
			check(horizontal(before, after) > 1.0, "W key moves the player during a challenge (moved " + horizontal(before, after) + ")");

			// 3. Drop key (Q) is blocked: select the log's hotbar slot first
			ctx.runOnClient(c -> c.player.getInventory().setSelectedSlot(0));
			ctx.waitTicks(2);
			ctx.getInput().pressKey(o -> o.dropKey);
			ctx.waitTicks(10);
			check(count(sp, Items.OAK_LOG) == 8, "Q does not drop the challenged item (have " + count(sp, Items.OAK_LOG) + ")");

			// 3b. Swap-hands key (F) is blocked
			ctx.getInput().pressKey(o -> o.swapHandsKey);
			ctx.waitTicks(10);
			check(sp.getServer().computeOnServer(s -> player(s).getOffHandStack().isEmpty()
					&& player(s).getInventory().getStack(0).isOf(Items.OAK_LOG)), "F does not move the item to the offhand");

			// 3c. Inventory screen: picking the stack up and Ctrl+Q-throwing it are both blocked
			ctx.getInput().pressKey(o -> o.inventoryKey);
			ctx.waitForScreen(InventoryScreen.class);
			ctx.runOnClient(c -> c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId,
					HOTBAR_0, 0, SlotActionType.PICKUP, c.player));
			ctx.waitTicks(5);
			ctx.runOnClient(c -> c.interactionManager.clickSlot(c.player.currentScreenHandler.syncId,
					HOTBAR_0, 1, SlotActionType.THROW, c.player));
			ctx.waitTicks(10);
			ctx.takeScreenshot("01b-inventory-locked");
			check(sp.getServer().computeOnServer(s -> player(s).currentScreenHandler.getCursorStack().isEmpty()
					&& player(s).getInventory().getStack(0).getCount() == 8), "inventory clicks can't pick up or throw the item");
			check(ctx.computeOnClient(c -> c.player.getInventory().getStack(0).getCount() == 8
					&& c.player.currentScreenHandler.getCursorStack().isEmpty()), "client view resynced (no ghost stack on cursor)");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(5);

			// 4. Correct answer through chat, with diacritics and capitals
			chat(ctx, "/verify Buștean de STEJAR");
			ctx.waitTicks(5);
			check(!sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))), "correct answer unfreezes");
			check(count(sp, Items.OAK_LOG) == 8, "correct answer keeps the logs");
			ctx.takeScreenshot("02-correct");

			// 5. Popup + partial answer in plain chat: letters go green/red, no punishment, retry allowed
			sp.getServer().runCommand("give @p minecraft:apple 5");
			ctx.waitTicks(25);
			check(ctx.computeOnClient(c -> LanguageModClient.HUD.debugPhase()).equals("ASKING")
					&& ctx.computeOnClient(c -> LanguageModClient.HUD.debugWord()).equals("măr"), "popup shows the word 'măr'");
			ctx.takeScreenshot("02b-popup");
			chat(ctx, "mor");
			ctx.waitTicks(5);
			check(ctx.computeOnClient(c -> LanguageModClient.HUD.debugMask()).equals("GRG"), "'mor' colours m-ă-r as green-red-green ("
					+ ctx.computeOnClient(c -> LanguageModClient.HUD.debugMask()) + ")");
			check(count(sp, Items.APPLE) == 5 && sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))),
					"wrong try keeps the apples and the timer running");
			ctx.waitTicks(80); // let the bar drain a bit for the screenshot
			ctx.takeScreenshot("03-partial");
			chat(ctx, "un măr");
			ctx.waitTicks(5);
			check(count(sp, Items.APPLE) == 5 && !sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))),
					"retry 'un măr' in plain chat is accepted");
			check(ctx.computeOnClient(c -> LanguageModClient.HUD.debugPhase()).equals("CORRECT"), "popup flips to Correct");
			ctx.takeScreenshot("03b-correct-popup");

			// 6. Timeout (15 s) -> stack deleted, half a heart, break sound + particles
			ctx.waitTicks(40);
			lastDamage = 0;
			sp.getServer().runCommand("give @p minecraft:bread 3");
			ctx.waitTicks(25);
			check(sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))), "bread starts a challenge");
			ctx.getInput().pressKey(o -> o.togglePerspectiveKey); // F5: third person, so the particles are in view
			heardSounds.clear();
			crackParticles.set(0);
			ctx.waitFor(c -> LanguageModClient.HUD.debugPhase().equals("FAILED"), 20 * 20);
			ctx.waitTicks(2);
			ctx.takeScreenshot("04-timeout");
			check(count(sp, Items.BREAD) == 0, "timeout deletes the bread");
			check(lastDamage == 1.0f, "timeout deals 1.0 damage (took " + lastDamage + ")");
			check(heardSounds.contains("minecraft:entity.item.break"), "client played the item-break sound (heard " + heardSounds + ")");
			check(crackParticles.get() > 0, "client spawned item-break particles (" + crackParticles.get() + ")");
			check(ctx.computeOnClient(c -> c.player.getInventory().count(Items.BREAD)) == 0, "client inventory shows the bread gone");
			ctx.getInput().pressKey(o -> o.togglePerspectiveKey);
			ctx.getInput().pressKey(o -> o.togglePerspectiveKey); // back to first person
			ctx.waitTicks(40);
			check(ctx.computeOnClient(c -> LanguageModClient.HUD.debugPhase()).equals("HIDDEN"), "popup hides after the result");

			// 7. Verified items don't ask again
			sp.getServer().runCommand("give @p minecraft:oak_log 1");
			ctx.waitTicks(25);
			check(!sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))), "already-verified oak log is not asked again");

			// 7b. Voice: real Romanian speech (edge-tts clips) through detector -> Whisper -> VoiceAnswer packet
			ctx.waitFor(c -> LanguageModClient.VOICE.ready(), 60 * 20);
			sp.getServer().runCommand("give @p minecraft:bread 2");
			// A missed word is a review now: it comes back after the 20 s review gap, not instantly.
			ctx.waitFor(c -> LanguageModClient.HUD.debugPhase().equals("ASKING"), 30 * 20);
			check(sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))), "missed bread comes back as a review");
			ctx.runOnClient(c -> LanguageModClient.VOICE.feedTestAudio(wav("mor")));
			ctx.waitFor(c -> !LanguageModClient.HUD.debugMask().isEmpty(), 20 * 20);
			String wrongHeard = ctx.computeOnClient(c -> LanguageModClient.HUD.debugLastAttempt());
			LanguageMod.LOG.info("[gametest] whisper heard '{}' for mor.wav, mask {}", wrongHeard,
					ctx.computeOnClient(c -> LanguageModClient.HUD.debugMask()));
			check(count(sp, Items.BREAD) == 2 && sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))),
					"spoken wrong word is a retry, not a loss");
			ctx.takeScreenshot("06-voice-wrong");
			ctx.runOnClient(c -> LanguageModClient.VOICE.feedTestAudio(wav("paine")));
			ctx.waitFor(c -> !LanguageModClient.HUD.debugPhase().equals("ASKING"), 20 * 20);
			String rightHeard = ctx.computeOnClient(c -> LanguageModClient.HUD.debugLastAttempt());
			LanguageMod.LOG.info("[gametest] whisper heard '{}' for paine.wav", rightHeard);
			ctx.takeScreenshot("07-voice-correct");
			check(ctx.computeOnClient(c -> LanguageModClient.HUD.debugPhase()).equals("CORRECT") && count(sp, Items.BREAD) == 2,
					"spoken 'pâine' is recognised and accepted");
			ctx.waitTicks(40);

			Path config = FabricLoader.getInstance().getConfigDir().resolve("languagemod/config.json");
			String original = read(config);

			// 7c. deleteItemOnFail=false: a miss only hurts, the item stays, and it is asked again after the snooze
			try {
				write(config, original.replace("\"deleteItemOnFail\": true", "\"deleteItemOnFail\": false")
						.replace("\"retryDelaySeconds\": 30", "\"retryDelaySeconds\": 5"));
				sp.getServer().runCommand("languagemod reload");
				ctx.waitTicks(5);
				lastDamage = 0;
				sp.getServer().runCommand("give @p minecraft:carrot 3");
				ctx.waitTicks(25);
				check(sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))), "carrot starts a challenge");
				ctx.waitFor(c -> LanguageModClient.HUD.debugPhase().equals("FAILED"), 20 * 20);
				ctx.waitTicks(2);
				ctx.takeScreenshot("08-damage-only");
				check(count(sp, Items.CARROT) == 3, "damage-only miss keeps the carrots (have " + count(sp, Items.CARROT) + ")");
				check(lastDamage == 1.0f, "damage-only miss still deals 1.0 damage (took " + lastDamage + ")");
				check(ctx.computeOnClient(c -> LanguageModClient.HUD.debugItemKept()), "popup says the item was kept");
				ctx.waitTicks(60);
				check(!sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))), "kept item is not asked again during the snooze");
				ctx.waitFor(c -> LanguageModClient.HUD.debugPhase().equals("ASKING"), 30 * 20);
				check(sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))), "kept item is asked again after the snooze");
				chat(ctx, "morcov");
				ctx.waitTicks(5);
				check(!sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))), "'morcov' clears it");
			} finally {
				write(config, original);
				sp.getServer().runCommand("languagemod reload");
			}
			ctx.waitTicks(40);

			// 7d. Voice grace: time runs out while the voice answer is still coming -> wait; a wrong answer then fails
			sp.getServer().runCommand("give @p minecraft:potato 2");
			ctx.waitFor(c -> LanguageModClient.HUD.debugPhase().equals("ASKING"), 10 * 20);
			sp.getServer().runOnServer(s -> LanguageMod.challenges().setVoiceBusy(player(s), true));
			ctx.waitTicks(15 * 20 + 40); // 2 s past the deadline
			check(sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))),
					"busy voice holds the challenge open past the timer");
			lastDamage = 0;
			chat(ctx, "rosie");
			ctx.waitTicks(5);
			check(!sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))) && lastDamage == 1.0f,
					"a wrong answer after the timer ends it as a miss");
			ctx.waitTicks(40);

			// 7e. The dictionary is saved in the world and shown with J
			sp.getServer().runOnServer(s -> LanguageMod.challenges().saveAll());
			Path saved = sp.getServer().computeOnServer(s -> s.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
					.resolve("languagemod/players/ro/" + player(s).getUuid() + ".json"));
			String json = read(saved);
			check(json.contains("minecraft:apple") && json.contains("\"misses\": 1") && json.contains("playTicks"),
					"progress file saved in the world: " + saved.getFileName());
			ctx.getInput().pressKey(GLFW.GLFW_KEY_J);
			ctx.waitForScreen(dev.itonk.languagemod.client.DictionaryScreen.class);
			ctx.waitTicks(10);
			ctx.takeScreenshot("09-dictionary");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(5);

			// 8. Switch to Spanish with /languagemod reload
			try {
				write(config, original.replace("\"targetLanguage\": \"ro\"", "\"targetLanguage\": \"es\"")
						.replace("\"Romanian\"", "\"Spanish\""));
				sp.getServer().runCommand("languagemod reload");
				ctx.waitTicks(5);
				sp.getServer().runCommand("give @p minecraft:stick 4");
				ctx.waitTicks(25);
				ctx.takeScreenshot("05-spanish-prompt");
				chat(ctx, "/verify el palo");
				ctx.waitTicks(5);
				check(!sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s)))
						&& count(sp, Items.STICK) == 4, "after reload, Spanish 'el palo' verifies a stick");
			} finally {
				write(config, original);
				sp.getServer().runCommand("languagemod reload");
			}
			ctx.waitTicks(10);

			// 8b. J toggles the dictionary; K opens settings
			ctx.getInput().pressKey(GLFW.GLFW_KEY_J);
			ctx.waitForScreen(dev.itonk.languagemod.client.DictionaryScreen.class);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_J);
			ctx.waitTicks(5);
			check(ctx.computeOnClient(c -> c.currentScreen == null), "J closes the dictionary again");

			// 8c. Settings screen: switch to Russian with real clicks, answer in Cyrillic
			ctx.getInput().pressKey(GLFW.GLFW_KEY_K);
			ctx.waitForScreen(dev.itonk.languagemod.client.SettingsScreen.class);
			ctx.waitTicks(5);
			ctx.takeScreenshot("13-settings");
			click(ctx, "Language to learn"); // ro -> es
			click(ctx, "Language to learn"); // es -> ru
			check(buttonText(ctx, "Language to learn").endsWith("Russian"), "settings: language button cycles to Russian");
			click(ctx, "Done");
			ctx.waitTicks(10);
			check(ctx.computeOnClient(c -> c.currentScreen == null), "settings: Done closes the screen");
			check(sp.getServer().computeOnServer(s -> LanguageMod.challenges().config().targetLanguage).equals("ru"),
					"settings: the world now asks in Russian");
			sp.getServer().runCommand("give @p minecraft:cake 1");
			ctx.waitTicks(25);
			String ruWord = ctx.computeOnClient(c -> LanguageModClient.HUD.debugWord());
			check(ruWord.equals("Торт"), "Russian popup shows the game's own word (" + ruWord + ")");
			ctx.takeScreenshot("14-russian-prompt");
			chat(ctx, "торт");
			ctx.waitTicks(5);
			check(ctx.computeOnClient(c -> LanguageModClient.HUD.debugPhase()).equals("CORRECT"), "Russian: typed 'торт' is correct");
			ctx.waitTicks(40);

			// 8d. Hebrew: shown right to left, answered in Hebrew
			ctx.getInput().pressKey(GLFW.GLFW_KEY_K);
			ctx.waitForScreen(dev.itonk.languagemod.client.SettingsScreen.class);
			click(ctx, "Language to learn"); // ru -> he
			click(ctx, "Done");
			ctx.waitTicks(10);
			sp.getServer().runCommand("give @p minecraft:torch 4");
			ctx.waitTicks(25);
			String heWord = ctx.computeOnClient(c -> LanguageModClient.HUD.debugWord());
			check(heWord.equals("לפיד"), "Hebrew popup shows the game's own word (" + heWord + ")");
			chat(ctx, "לפוד"); // one wrong letter: popup and chat colour it
			ctx.waitTicks(5);
			ctx.takeScreenshot("15-hebrew-prompt");
			chat(ctx, "לפיד");
			ctx.waitTicks(5);
			check(ctx.computeOnClient(c -> LanguageModClient.HUD.debugPhase()).equals("CORRECT"), "Hebrew: typed 'לפיד' is correct");
			ctx.waitTicks(40);
			ctx.getInput().pressKey(GLFW.GLFW_KEY_J);
			ctx.waitForScreen(dev.itonk.languagemod.client.DictionaryScreen.class);
			ctx.waitTicks(10);
			ctx.takeScreenshot("16-hebrew-dictionary");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_J);
			ctx.waitTicks(5);

			// 8e. Back to Romanian: the Romanian dictionary is still there (oak log known, not asked)
			ctx.getInput().pressKey(GLFW.GLFW_KEY_K);
			ctx.waitForScreen(dev.itonk.languagemod.client.SettingsScreen.class);
			click(ctx, "Language to learn"); // he -> ro
			click(ctx, "Done");
			ctx.waitTicks(10);
			check(sp.getServer().computeOnServer(s -> LanguageMod.challenges().config().targetLanguage).equals("ro"), "settings: back to Romanian");
			sp.getServer().runCommand("give @p minecraft:oak_log 1");
			ctx.waitTicks(25);
			check(!sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))),
					"Romanian progress kept per language: known oak log isn't asked again");
		}

		// 9. Client-only mode: the server acts as if it had no LanguageMod (no rules, no channels),
		//    so the client runs the rules itself. Chat answers must stay off the server.
		List<String> serverChat = new CopyOnWriteArrayList<>();
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> serverChat.add(message.getSignedContent()));
		LanguageMod.setServerRules(false);
		try (TestSingleplayerContext sp = ctx.worldBuilder().create()) {
			sp.getClientWorld().waitForChunksRender();
			sp.getServer().runCommand("gamemode survival @a");
			sp.getServer().runCommand("time set noon");
			sp.getServer().runCommand("difficulty peaceful");
			ctx.waitTicks(60);
			check(ctx.computeOnClient(c -> LanguageModClient.CLIENT_ONLY.active()), "client-only mode starts on a server without the mod");

			sp.getServer().runCommand("give @p minecraft:apple 3");
			ctx.waitTicks(25);
			check(ctx.computeOnClient(c -> LanguageModClient.HUD.debugPhase()).equals("ASKING"), "client-only: new apple opens the popup");
			ctx.takeScreenshot("10-client-only-prompt");
			chat(ctx, "mar");
			ctx.waitTicks(5);
			check(ctx.computeOnClient(c -> LanguageModClient.HUD.debugPhase()).equals("CORRECT"), "client-only: chat 'mar' answers the apple");
			check(serverChat.isEmpty(), "client-only: the answer never reached the server chat (" + serverChat + ")");
			check(count(sp, Items.APPLE) == 3, "client-only: correct answer keeps the apples");
			ctx.waitTicks(40);

			sp.getServer().runCommand("give @p minecraft:bread 2");
			ctx.waitTicks(25);
			check(ctx.computeOnClient(c -> LanguageModClient.HUD.debugPhase()).equals("ASKING"), "client-only: bread opens the popup");
			// Watch tick by tick: the flash lasts only 10 ticks.
			for (int i = 0; i < 15 * 20 + 20 && !ctx.computeOnClient(c -> LanguageModClient.HUD.debugPhase()).equals("FAILED"); i++) {
				ctx.waitTicks(1);
			}
			ctx.takeScreenshot("11-client-only-miss");
			check(ctx.computeOnClient(c -> LanguageModClient.HUD.debugPhase()).equals("FAILED"), "client-only: time's up fails it");
			check(ctx.computeOnClient(c -> LanguageModClient.HUD.debugHurtTicks()) > 0, "client-only: miss shows the red flash");
			check(ctx.computeOnClient(c -> c.player.hurtTime) > 0, "client-only: miss plays the hurt camera shake");
			ctx.waitTicks(3);
			check(count(sp, Items.BREAD) == 0 && ctx.computeOnClient(c -> c.player.getInventory().count(Items.BREAD)) == 0,
					"client-only: miss throws the bread out (server inventory agrees)");

			ctx.getInput().pressKey(GLFW.GLFW_KEY_J);
			ctx.waitForScreen(dev.itonk.languagemod.client.DictionaryScreen.class);
			ctx.waitTicks(10);
			ctx.takeScreenshot("12-client-only-dictionary");
			check(ctx.computeOnClient(c -> LanguageModClient.CLIENT_ONLY.snapshotSize()) >= 2, "client-only: dictionary lists apple and bread");
			ctx.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
			ctx.waitTicks(5);
		} finally {
			LanguageMod.setServerRules(true);
		}

		LanguageMod.LOG.info("[gametest] ALL CHECKS PASSED");
	}

	/** Loads src/gametest/resources/voice/NAME.wav (16 kHz mono s16) as samples in -1..1. */
	private static float[] wav(String name) {
		try (var in = ChallengeClientGameTest.class.getResourceAsStream("/voice/" + name + ".wav")) {
			ByteBuffer b = ByteBuffer.wrap(in.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
			int pos = 12; // after "RIFF....WAVE"
			while (pos + 8 <= b.limit()) {
				String id = new String(new byte[] {b.get(pos), b.get(pos + 1), b.get(pos + 2), b.get(pos + 3)});
				int size = b.getInt(pos + 4);
				if (id.equals("data")) {
					float[] out = new float[size / 2];
					for (int i = 0; i < out.length; i++) out[i] = b.getShort(pos + 8 + 2 * i) / 32768f;
					return out;
				}
				pos += 8 + size + (size & 1);
			}
			throw new IllegalStateException("no data chunk in " + name);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String read(Path p) {
		try { return Files.readString(p); } catch (IOException e) { throw new UncheckedIOException(e); }
	}

	private static void write(Path p, String s) {
		try { Files.writeString(p, s); } catch (IOException e) { throw new UncheckedIOException(e); }
	}

	private static ServerPlayerEntity player(MinecraftServer s) {
		return s.getPlayerManager().getPlayerList().getFirst();
	}

	private static int count(TestSingleplayerContext sp, Item item) {
		return sp.getServer().computeOnServer(s -> player(s).getInventory().count(item));
	}

	private static void chat(ClientGameTestContext ctx, String line) {
		ctx.getInput().pressKey(o -> o.chatKey);
		ctx.waitTicks(3);
		ctx.getInput().typeChars(line);
		ctx.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
	}

	/** Clicks the settings button whose label starts with {@code prefix}, with the real mouse. */
	private static void click(ClientGameTestContext ctx, String prefix) {
		double[] pos = ctx.computeOnClient(c -> {
			for (var child : c.currentScreen.children()) {
				if (child instanceof net.minecraft.client.gui.widget.ButtonWidget b && b.getMessage().getString().startsWith(prefix)) {
					double scale = c.getWindow().getScaleFactor();
					return new double[] {(b.getX() + b.getWidth() / 2.0) * scale, (b.getY() + b.getHeight() / 2.0) * scale};
				}
			}
			throw new AssertionError("no button " + prefix);
		});
		ctx.getInput().setCursorPos(pos[0], pos[1]);
		ctx.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
	}

	private static String buttonText(ClientGameTestContext ctx, String prefix) {
		return ctx.computeOnClient(c -> {
			for (var child : c.currentScreen.children()) {
				if (child instanceof net.minecraft.client.gui.widget.ButtonWidget b && b.getMessage().getString().startsWith(prefix)) {
					return b.getMessage().getString();
				}
			}
			return "";
		});
	}

	private static double horizontal(Vec3d a, Vec3d b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	private static void check(boolean ok, String what) {
		LanguageMod.LOG.info("[gametest] {} {}", ok ? "PASS" : "FAIL", what);
		if (!ok) throw new AssertionError(what);
	}
}
