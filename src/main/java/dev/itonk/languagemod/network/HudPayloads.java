package dev.itonk.languagemod.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Messages between server and client for the challenge popup and voice answers.
 * The client only draws and listens; all judging (dictionary, letter matching, timer)
 * stays on the server.
 */
public final class HudPayloads {
	private HudPayloads() {}

	/** S2C: a challenge opened. {@code languageCode} (e.g. "ro") tells speech-to-text what to expect. */
	public record Start(ItemStack stack, String word, String languageName, String languageCode, int durationTicks)
			implements CustomPayload {
		public static final Id<Start> ID = new Id<>(Identifier.of("languagemod", "challenge_start"));
		public static final PacketCodec<RegistryByteBuf, Start> CODEC = PacketCodec.tuple(
				ItemStack.PACKET_CODEC, Start::stack,
				PacketCodecs.STRING, Start::word,
				PacketCodecs.STRING, Start::languageName,
				PacketCodecs.STRING, Start::languageCode,
				PacketCodecs.VAR_INT, Start::durationTicks,
				Start::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** S2C: a wrong attempt; {@code mask} has one 'G'/'R' per letter of the word. */
	public record Attempt(String attempt, String mask) implements CustomPayload {
		public static final Id<Attempt> ID = new Id<>(Identifier.of("languagemod", "challenge_attempt"));
		public static final PacketCodec<RegistryByteBuf, Attempt> CODEC = PacketCodec.tuple(
				PacketCodecs.STRING, Attempt::attempt,
				PacketCodecs.STRING, Attempt::mask,
				Attempt::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** S2C: the challenge closed. */
	public record End(boolean success) implements CustomPayload {
		public static final Id<End> ID = new Id<>(Identifier.of("languagemod", "challenge_end"));
		public static final PacketCodec<RegistryByteBuf, End> CODEC = PacketCodec.tuple(
				PacketCodecs.BOOLEAN, End::success,
				End::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** C2S: what speech-to-text heard. Judged exactly like a typed answer. */
	public record VoiceAnswer(String text) implements CustomPayload {
		public static final Id<VoiceAnswer> ID = new Id<>(Identifier.of("languagemod", "voice_answer"));
		public static final PacketCodec<RegistryByteBuf, VoiceAnswer> CODEC = PacketCodec.tuple(
				PacketCodecs.string(200), VoiceAnswer::text,
				VoiceAnswer::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** Called from the common initializer so both sides know the payload types. */
	public static void register() {
		PayloadTypeRegistry.playS2C().register(Start.ID, Start.CODEC);
		PayloadTypeRegistry.playS2C().register(Attempt.ID, Attempt.CODEC);
		PayloadTypeRegistry.playS2C().register(End.ID, End.CODEC);
		PayloadTypeRegistry.playC2S().register(VoiceAnswer.ID, VoiceAnswer.CODEC);
	}

	public static String toPattern(boolean[] mask) {
		StringBuilder sb = new StringBuilder(mask.length);
		for (boolean b : mask) sb.append(b ? 'G' : 'R');
		return sb.toString();
	}
}
