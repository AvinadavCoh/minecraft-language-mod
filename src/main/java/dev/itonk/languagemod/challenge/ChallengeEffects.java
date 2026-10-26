package dev.itonk.languagemod.challenge;

import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Chat, sound and particles. No game rules live here. */
public final class ChallengeEffects {
	private static final Text PREFIX = Text.literal("[LanguageMod] ").formatted(Formatting.GOLD);

	private ChallengeEffects() {}

	public static void prompt(ServerPlayerEntity player, ItemStack stack, String languageName, int seconds) {
		player.sendMessage(PREFIX.copy()
				.append(Text.literal("Name this item in " + languageName + "! ").formatted(Formatting.YELLOW))
				.append(stack.getName().copy().formatted(Formatting.AQUA))
				.append(Text.literal(" (" + seconds + "s, /verify <word>)").formatted(Formatting.GRAY)), false);
		playPrivate(player, SoundEvents.BLOCK_NOTE_BLOCK_BELL, 1f);
	}

	public static void success(ServerPlayerEntity player, ItemStack stack) {
		player.sendMessage(PREFIX.copy().append(Text.literal("Correct! ").formatted(Formatting.GREEN))
				.append(stack.getName()), false);
		playPrivate(player, Registries.SOUND_EVENT.getEntry(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP), 1.2f);
	}

	/** A sound only this player hears (the prompt/success chimes shouldn't spam everyone nearby). */
	private static void playPrivate(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, float pitch) {
		player.networkHandler.sendPacket(new PlaySoundS2CPacket(sound, SoundCategory.PLAYERS,
				player.getX(), player.getY(), player.getZ(), 1f, pitch, player.getRandom().nextLong()));
	}

	public static void failure(ServerPlayerEntity player, ItemStack removed, String reason, String answer) {
		String hint = answer != null ? " It was: " + answer : "";
		player.sendMessage(PREFIX.copy().append(Text.literal(reason + hint).formatted(Formatting.RED)), false);

		ServerWorld world = player.getEntityWorld();
		// playSound with a null "except" player -> everyone nearby hears it, including this player.
		world.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1f, 1f);
		if (!removed.isEmpty()) {
			world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, removed),
					player.getX(), player.getY() + 1.2, player.getZ(),
					24, 0.25, 0.25, 0.25, 0.08);
		}
	}
}
