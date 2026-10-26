package dev.itonk.languagemod.challenge;

import dev.itonk.languagemod.network.HudPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

/**
 * Chat, popup packets, sound and particles. No game rules live here.
 * Players with the mod on their client get the popup; everyone gets the chat lines,
 * so a vanilla client on a modded server can still play.
 */
public final class ChallengeEffects {
	private ChallengeEffects() {}

	public static void prompt(ServerPlayerEntity player, ItemStack stack, String word, String languageName,
			String languageCode, int durationTicks) {
		player.sendMessage(ChallengeMessages.prompt(stack, word, languageName), false);
		send(player, new HudPayloads.Start(stack.copyWithCount(1), word, languageName, languageCode, durationTicks));
		playSoundPrivate(player, SoundEvents.BLOCK_NOTE_BLOCK_BELL, 1f);
	}

	public static void welcome(ServerPlayerEntity player, String languageName, int carried, int gapSeconds, boolean off) {
		for (Text line : ChallengeMessages.welcome(languageName, carried, gapSeconds, off, null)) player.sendMessage(line, false);
	}

	/** A wrong try while the timer still runs: colour each letter of the word green/red. */
	public static void attempt(ServerPlayerEntity player, String attempt, String word, boolean[] mask) {
		player.sendMessage(ChallengeMessages.attempt(attempt, word, mask), false);
		send(player, new HudPayloads.Attempt(attempt, HudPayloads.toPattern(mask)));
		playSoundPrivate(player, SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.7f);
	}

	public static void success(ServerPlayerEntity player, ItemStack stack) {
		player.sendMessage(ChallengeMessages.success(stack), false);
		send(player, new HudPayloads.End(true, true));
		playSoundPrivate(player, Registries.SOUND_EVENT.getEntry(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP), 1.2f);
	}

	/** @param kept true when deleteItemOnFail is off: no break sound/particles, just the damage. */
	public static void failure(ServerPlayerEntity player, ItemStack removed, boolean kept, String reason, String answer) {
		player.sendMessage(ChallengeMessages.failure(reason, answer, kept ? " (item kept)" : ""), false);
		send(player, new HudPayloads.End(false, kept));
		if (kept) return;

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

	/** Popup packets only go to clients that have the mod (vanilla clients would disconnect). */
	private static void send(ServerPlayerEntity player, CustomPayload payload) {
		if (ServerPlayNetworking.canSend(player, payload.getId())) ServerPlayNetworking.send(player, payload);
	}

	/** A sound only this player hears (the prompt/success chimes shouldn't spam everyone nearby). */
	private static void playSoundPrivate(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, float pitch) {
		player.networkHandler.sendPacket(new PlaySoundS2CPacket(sound, SoundCategory.PLAYERS,
				player.getX(), player.getY(), player.getZ(), 1f, pitch, player.getRandom().nextLong()));
	}
}
