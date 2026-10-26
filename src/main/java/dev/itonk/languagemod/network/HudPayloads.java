package dev.itonk.languagemod.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server -> client messages that drive the challenge popup. The client only draws;
 * all judging (dictionary, letter matching, timer) stays on the server.
 */
public final class HudPayloads {
	private HudPayloads() {}

	/** A challenge opened: show the item, the word to read, and a full time bar. */
	public record Start(ItemStack stack, String word, String languageName, int durationTicks) implements CustomPayload {
		public static final Id<Start> ID = new Id<>(Identifier.of("languagemod", "challenge_start"));
		public static final PacketCodec<RegistryByteBuf, Start> CODEC = PacketCodec.tuple(
				ItemStack.PACKET_CODEC, Start::stack,
				PacketCodecs.STRING, Start::word,
				PacketCodecs.STRING, Start::languageName,
				PacketCodecs.VAR_INT, Start::durationTicks,
				Start::new);

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	/** A wrong attempt: {@code mask} has one 'G'/'R' per letter of the word. */
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

	/** The challenge closed. */
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

	/** Called from the common initializer so both sides know the payload types. */
	public static void register() {
		PayloadTypeRegistry.playS2C().register(Start.ID, Start.CODEC);
		PayloadTypeRegistry.playS2C().register(Attempt.ID, Attempt.CODEC);
		PayloadTypeRegistry.playS2C().register(End.ID, End.CODEC);
	}

	public static String toPattern(boolean[] mask) {
		StringBuilder sb = new StringBuilder(mask.length);
		for (boolean b : mask) sb.append(b ? 'G' : 'R');
		return sb.toString();
	}
}
