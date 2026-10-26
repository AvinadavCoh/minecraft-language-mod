package dev.itonk.languagemod.test;

import dev.itonk.languagemod.LanguageMod;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
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
			check(speed == 0.0, "movement speed is 0 while frozen (was " + speed + ")");
			ctx.takeScreenshot("01-prompt");

			// 2. Walking does nothing
			Vec3d before = sp.getServer().computeOnServer(s -> player(s).getEntityPos());
			ctx.getInput().holdKeyFor(o -> o.forwardKey, 30);
			Vec3d after = sp.getServer().computeOnServer(s -> player(s).getEntityPos());
			check(horizontal(before, after) < 0.05, "W key does not move a frozen player (moved " + horizontal(before, after) + ")");

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

			// 5. Wrong answer -> stack deleted + half a heart
			lastDamage = 0;
			sp.getServer().runCommand("give @p minecraft:apple 5");
			ctx.waitTicks(25);
			ctx.getInput().pressKey(o -> o.togglePerspectiveKey); // F5: third person, so the particles are in view
			heardSounds.clear();
			crackParticles.set(0);
			chat(ctx, "/verify roble");
			ctx.waitTicks(2);
			ctx.takeScreenshot("03-wrong");
			check(count(sp, Items.APPLE) == 0, "wrong answer deletes the apples");
			check(lastDamage == 1.0f, "wrong answer deals 1.0 damage (took " + lastDamage + ")");
			check(heardSounds.contains("minecraft:entity.item.break"), "client played the item-break sound (heard " + heardSounds + ")");
			check(crackParticles.get() > 0, "client spawned item-break particles (" + crackParticles.get() + ")");
			check(ctx.computeOnClient(c -> c.player.getInventory().count(Items.APPLE)) == 0, "client inventory shows the apples gone");
			ctx.getInput().pressKey(o -> o.togglePerspectiveKey);
			ctx.getInput().pressKey(o -> o.togglePerspectiveKey); // back to first person

			// 6. Timeout (15 s) -> same punishment
			ctx.waitTicks(30); // clear the hurt cooldown
			lastDamage = 0;
			sp.getServer().runCommand("give @p minecraft:bread 3");
			ctx.waitTicks(25);
			check(sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))), "bread starts a challenge");
			ctx.waitTicks(15 * 20 + 10);
			check(count(sp, Items.BREAD) == 0, "timeout deletes the bread");
			check(lastDamage == 1.0f, "timeout deals 1.0 damage (took " + lastDamage + ")");
			ctx.takeScreenshot("04-timeout");

			// 7. Verified items don't ask again
			sp.getServer().runCommand("give @p minecraft:oak_log 1");
			ctx.waitTicks(25);
			check(!sp.getServer().computeOnServer(s -> LanguageMod.challenges().isFrozen(player(s))), "already-verified oak log is not asked again");

			// 8. Switch to Spanish with /languagemod reload
			Path config = FabricLoader.getInstance().getConfigDir().resolve("languagemod/config.json");
			String original = read(config);
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

			LanguageMod.LOG.info("[gametest] ALL CHECKS PASSED");
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

	private static double horizontal(Vec3d a, Vec3d b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	private static void check(boolean ok, String what) {
		LanguageMod.LOG.info("[gametest] {} {}", ok ? "PASS" : "FAIL", what);
		if (!ok) throw new AssertionError(what);
	}
}
