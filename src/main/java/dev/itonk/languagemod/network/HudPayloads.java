package dev.itonk.languagemod.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

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

	/** S2C: the challenge closed. {@code itemKept} is false when a miss destroyed the stack. */
	public record End(boolean success, boolean itemKept) implements CustomPayload {
		public static final Id<End> ID = new Id<>(Identifier.of("languagemod", "challenge_end"));
		public static final PacketCodec<RegistryByteBuf, End> CODEC = PacketCodec.tuple(
				PacketCodecs.BOOLEAN, End::success,
				PacketCodecs.BOOLEAN, End::itemKept,
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

	/** C2S: the player is speaking or Whisper is still working; the server holds the timer for it. */
	public record VoiceBusy(boolean busy) implements CustomPayload {
		public static final Id<VoiceBusy> ID = new Id<>(Identifier.of("languagemod", "voice_busy"));
		public static final PacketCodec<RegistryByteBuf, VoiceBusy> CODEC = PacketCodec.tuple(
				PacketCodecs.BOOLEAN, VoiceBusy::busy,
				VoiceBusy::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** One word in the player's dictionary. {@code status} is a WordProgress.Status ordinal. */
	public record DictionaryEntry(String itemId, String word, int status, int box, int correct, int misses,
			int wrongTries, int dueInSeconds) {
		public static final PacketCodec<RegistryByteBuf, DictionaryEntry> CODEC = PacketCodec.of(
				(e, buf) -> {
					buf.writeString(e.itemId);
					buf.writeString(e.word);
					buf.writeVarInt(e.status);
					buf.writeVarInt(e.box);
					buf.writeVarInt(e.correct);
					buf.writeVarInt(e.misses);
					buf.writeVarInt(e.wrongTries);
					buf.writeVarInt(e.dueInSeconds);
				},
				buf -> new DictionaryEntry(buf.readString(), buf.readString(), buf.readVarInt(), buf.readVarInt(),
						buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
	}

	/** C2S: the dictionary screen opened and wants the player's words. */
	public record DictionaryRequest() implements CustomPayload {
		public static final DictionaryRequest INSTANCE = new DictionaryRequest();
		public static final Id<DictionaryRequest> ID = new Id<>(Identifier.of("languagemod", "dictionary_request"));
		public static final PacketCodec<RegistryByteBuf, DictionaryRequest> CODEC = PacketCodec.unit(INSTANCE);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** S2C: the player's words, highest-priority "needs repeating" first. */
	public record DictionarySnapshot(List<DictionaryEntry> entries) implements CustomPayload {
		public static final Id<DictionarySnapshot> ID = new Id<>(Identifier.of("languagemod", "dictionary_snapshot"));
		public static final PacketCodec<RegistryByteBuf, DictionarySnapshot> CODEC = PacketCodec.tuple(
				DictionaryEntry.CODEC.collect(PacketCodecs.toList()), DictionarySnapshot::entries,
				DictionarySnapshot::new);

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
		PayloadTypeRegistry.playC2S().register(VoiceBusy.ID, VoiceBusy.CODEC);
		PayloadTypeRegistry.playC2S().register(DictionaryRequest.ID, DictionaryRequest.CODEC);
		PayloadTypeRegistry.playS2C().register(DictionarySnapshot.ID, DictionarySnapshot.CODEC);
	}

	public static String toPattern(boolean[] mask) {
		StringBuilder sb = new StringBuilder(mask.length);
		for (boolean b : mask) sb.append(b ? 'G' : 'R');
		return sb.toString();
	}
}
