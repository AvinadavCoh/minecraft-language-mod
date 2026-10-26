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
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Chat, popup packets, sound and particles. No game rules live here.
 * Players with the mod on their client get the popup; everyone gets the chat lines,
 * so a vanilla client on a modded server can still play.
 */
public final class ChallengeEffects {
	private static final Text PREFIX = Text.literal("[LanguageMod] ").formatted(Formatting.GOLD);

	private ChallengeEffects() {}

	public static void prompt(ServerPlayerEntity player, ItemStack stack, String word, String languageName, int durationTicks) {
		player.sendMessage(PREFIX.copy()
				.append(Text.literal("Name this item in " + languageName + "! ").formatted(Formatting.YELLOW))
				.append(stack.getName().copy().formatted(Formatting.AQUA))
				.append(Text.literal(" → ").formatted(Formatting.GRAY))
				.append(Text.literal(word).formatted(Formatting.WHITE, Formatting.BOLD)), false);
		send(player, new HudPayloads.Start(stack.copyWithCount(1), word, languageName, durationTicks));
		playPrivate(player, SoundEvents.BLOCK_NOTE_BLOCK_BELL, 1f);
	}

	/** A wrong try while the timer still runs: colour each letter of the word green/red. */
	public static void attempt(ServerPlayerEntity player, String attempt, String word, boolean[] mask) {
		MutableText colored = Text.empty();
		for (int i = 0; i < word.length(); i++) {
			colored.append(Text.literal(String.valueOf(word.charAt(i))).formatted(mask[i] ? Formatting.GREEN : Formatting.RED));
		}
		player.sendMessage(PREFIX.copy().append(Text.literal("\"" + attempt + "\" → ").formatted(Formatting.GRAY)).append(colored), false);
		send(player, new HudPayloads.Attempt(attempt, HudPayloads.toPattern(mask)));
		playPrivate(player, SoundEvents.BLOCK_NOTE_BLOCK_BASS, 0.7f);
	}

	public static void success(ServerPlayerEntity player, ItemStack stack) {
		player.sendMessage(PREFIX.copy().append(Text.literal("Correct! ").formatted(Formatting.GREEN))
				.append(stack.getName()), false);
		send(player, new HudPayloads.End(true));
		playPrivate(player, Registries.SOUND_EVENT.getEntry(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP), 1.2f);
	}

	public static void failure(ServerPlayerEntity player, ItemStack removed, String reason, String answer) {
		String hint = answer != null ? " It was: " + answer : "";
		player.sendMessage(PREFIX.copy().append(Text.literal(reason + hint).formatted(Formatting.RED)), false);
		send(player, new HudPayloads.End(false));

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
	private static void playPrivate(ServerPlayerEntity player, RegistryEntry<SoundEvent> sound, float pitch) {
		player.networkHandler.sendPacket(new PlaySoundS2CPacket(sound, SoundCategory.PLAYERS,
				player.getX(), player.getY(), player.getZ(), 1f, pitch, player.getRandom().nextLong()));
	}
}
