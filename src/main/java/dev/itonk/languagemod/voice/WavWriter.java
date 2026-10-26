package dev.itonk.languagemod.voice;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Writes 16 kHz mono 16-bit WAV files (debug recordings of what the mic heard). */
public final class WavWriter {
	private WavWriter() {}

	public static void write(Path file, float[] pcm) throws IOException {
		Files.createDirectories(file.getParent());
		try (OutputStream out = Files.newOutputStream(file)) {
			out.write(bytes(pcm));
		}
	}

	/** The whole WAV file in memory (also what the GPU server is sent). */
	public static byte[] bytes(float[] pcm) {
		int dataBytes = pcm.length * 2;
		ByteBuffer b = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
		b.put("RIFF".getBytes()).putInt(36 + dataBytes).put("WAVE".getBytes());
		b.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) 1)
				.putInt(VoiceActivityDetector.SAMPLE_RATE).putInt(VoiceActivityDetector.SAMPLE_RATE * 2)
				.putShort((short) 2).putShort((short) 16);
		b.put("data".getBytes()).putInt(dataBytes);
		for (float s : pcm) b.putShort((short) Math.round(Math.max(-1f, Math.min(1f, s)) * 32767));
		return b.array();
	}
}
