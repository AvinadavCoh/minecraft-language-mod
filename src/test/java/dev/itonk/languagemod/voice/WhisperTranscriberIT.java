package dev.itonk.languagemod.voice;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Real Whisper on the edge-tts clips, cut by the voice detector exactly like the mic path.
 * Skipped unless -Dwhisper.model=PATH is given (the model is 190 MB and not in the repo).
 */
class WhisperTranscriberIT {
	@Test
	void transcribesRomanianClips() throws Exception {
		String model = System.getProperty("whisper.model");
		Assumptions.assumeTrue(model != null && !model.isBlank() && Files.isRegularFile(Path.of(model)), "no -Dwhisper.model");
		String[][] cases = {{"mar", "măr"}, {"mor", "măr"}, {"paine", "pâine"}, {"mor", "pâine"}};
		int threads = Integer.getInteger("whisper.threads", 4);
		try (WhisperTranscriber t = new WhisperTranscriber(Path.of(model), threads)) {
			t.setAudioCtx(Integer.getInteger("whisper.ctxA", 1000));
			for (String[] c : cases) {
				float[] pcm = cut(wav(Path.of("src/gametest/resources/voice/" + c[0] + ".wav")));
				long t0 = System.nanoTime();
				String text = t.transcribe(pcm, "ro", c[1]);
				System.out.printf("[whisper-it] pad=%s ctx=%s %s.wav (cut %d ms) hint=%s -> \"%s\" in %d ms%n",
						System.getProperty("whisper.padMs"), System.getProperty("whisper.ctxA"), c[0], pcm.length / 16,
						c[1], text, (System.nanoTime() - t0) / 1_000_000);
			}
		}
	}

	/** -Dwhisper.gpuServer=PATH to whisper-server.exe: same clips through the GPU helper (mod code path). */
	@Test
	void transcribesOnGpu() throws Exception {
		String model = System.getProperty("whisper.model");
		String exe = System.getProperty("whisper.gpuServer");
		Assumptions.assumeTrue(model != null && !model.isBlank() && exe != null && !exe.isBlank()
				&& Files.isRegularFile(Path.of(exe)), "no -Dwhisper.gpuServer");
		Path tmp = Files.createTempDirectory("whisper-gpu-it");
		String[][] cases = {{"mar", "măr"}, {"mor", "măr"}, {"paine", "pâine"}, {"mor", "pâine"}};
		try (WhisperServerTranscriber t = new WhisperServerTranscriber(Path.of(exe), Path.of(model), 4,
				Integer.getInteger("whisper.ctxA", 1000), tmp.resolve("server.log"), tmp.resolve("server.pid"))) {
			long start = System.nanoTime();
			while (!t.ready() && t.alive() && System.nanoTime() - start < 300_000_000_000L) Thread.sleep(200);
			org.junit.jupiter.api.Assertions.assertTrue(t.ready(), "GPU helper became ready");
			System.out.printf("[whisper-gpu-it] ready after %d ms%n", (System.nanoTime() - start) / 1_000_000);
			for (int round = 0; round < 2; round++) {
				for (String[] c : cases) {
					float[] pcm = cut(wav(Path.of("src/gametest/resources/voice/" + c[0] + ".wav")));
					long t0 = System.nanoTime();
					String text = t.transcribe(pcm, "ro", c[1]);
					System.out.printf("[whisper-gpu-it] %s.wav hint=%s -> \"%s\" in %d ms%n", c[0], c[1], text,
							(System.nanoTime() - t0) / 1_000_000);
				}
			}
		}
	}

	/** -Dwhisper.wav=PATH: transcribe any recording (e.g. a mic test), in 25 s chunks, no hint. */
	@Test
	void transcribesGivenRecording() throws Exception {
		String model = System.getProperty("whisper.model");
		String file = System.getProperty("whisper.wav");
		Assumptions.assumeTrue(model != null && !model.isBlank() && Files.isRegularFile(Path.of(model))
				&& file != null && !file.isBlank(), "no -Dwhisper.wav");
		float[] pcm = wav(Path.of(file));
		try (WhisperTranscriber t = new WhisperTranscriber(Path.of(model), Integer.getInteger("whisper.threads", 4))) {
			long t0 = System.nanoTime();
			String text = t.transcribe(pcm, System.getProperty("whisper.lang", "ro"), null);
			System.out.printf("[whisper-it] %s (%d ms audio) -> \"%s\" in %d ms%n", Path.of(file).getFileName(),
					pcm.length / 16, text, (System.nanoTime() - t0) / 1_000_000);
		}
	}

	/** The phrase the voice detector would hand to Whisper. */
	static float[] cut(float[] pcm) {
		VoiceActivityDetector vad = new VoiceActivityDetector();
		int f = VoiceActivityDetector.FRAME;
		for (int i = 0; i + f <= pcm.length; i += f) {
			float[] out = vad.accept(Arrays.copyOfRange(pcm, i, i + f));
			if (out != null) return out;
		}
		for (int i = 0; i < 40; i++) {
			float[] out = vad.accept(new float[f]);
			if (out != null) return out;
		}
		throw new IllegalStateException("no phrase");
	}

	static float[] wav(Path p) throws Exception {
		ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(p)).order(ByteOrder.LITTLE_ENDIAN);
		int pos = 12;
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
		throw new IllegalStateException("no data chunk");
	}
}
