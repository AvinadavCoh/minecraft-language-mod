package dev.itonk.languagemod.voice;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceActivityDetectorTest {
	private static final int F = VoiceActivityDetector.FRAME;
	private final Random random = new Random(1);

	private float[] noise(float amp) {
		float[] f = new float[F];
		for (int i = 0; i < F; i++) f[i] = (random.nextFloat() * 2 - 1) * amp;
		return f;
	}

	private float[] tone(float amp, int frameIndex) {
		float[] f = new float[F];
		for (int i = 0; i < F; i++) f[i] = (float) (amp * Math.sin(2 * Math.PI * 220 * (frameIndex * F + i) / 16000.0));
		return f;
	}

	/** Feeds frames, returns the first utterance produced (or null). */
	private float[] run(VoiceActivityDetector vad, float[]... frames) {
		for (float[] f : frames) {
			float[] out = vad.accept(f);
			if (out != null) return out;
		}
		return null;
	}

	@Test
	void quietRoomProducesNothing() {
		VoiceActivityDetector vad = new VoiceActivityDetector();
		for (int i = 0; i < 300; i++) assertNull(vad.accept(noise(0.003f)));
	}

	@Test
	void phraseThenSilenceIsCut() {
		VoiceActivityDetector vad = new VoiceActivityDetector();
		for (int i = 0; i < 30; i++) vad.accept(noise(0.003f));
		float[] out = null;
		for (int i = 0; i < 25 && out == null; i++) out = vad.accept(tone(0.2f, i)); // ~750 ms of "speech"
		assertNull(out, "no cut while still talking");
		for (int i = 0; i < 40 && out == null; i++) out = vad.accept(noise(0.003f));
		assertNotNull(out, "quiet after speech ends the phrase");
		// pre-roll + 25 speech frames + hangover
		assertTrue(out.length >= 25 * F && out.length <= 60 * F, "length " + out.length / F + " frames");
	}

	@Test
	void shortClickIsIgnored() {
		VoiceActivityDetector vad = new VoiceActivityDetector();
		for (int i = 0; i < 30; i++) vad.accept(noise(0.003f));
		vad.accept(tone(0.5f, 0));
		vad.accept(tone(0.5f, 1));
		for (int i = 0; i < 40; i++) assertNull(vad.accept(noise(0.003f)));
	}

	@Test
	void realSpeechClipsAreCut() throws Exception {
		for (String name : new String[] {"mar", "mor", "paine"}) {
			float[] pcm = WhisperTranscriberIT.wav(java.nio.file.Path.of("src/gametest/resources/voice/" + name + ".wav"));
			float peak = 0;
			for (float s : pcm) peak = Math.max(peak, Math.abs(s));
			VoiceActivityDetector vad = new VoiceActivityDetector();
			float[] out = null;
			float maxLevel = 0;
			for (int i = 0; i + F <= pcm.length && out == null; i += F) {
				float[] frame = java.util.Arrays.copyOfRange(pcm, i, i + F);
				out = vad.accept(frame);
				maxLevel = Math.max(maxLevel, vad.level());
			}
			for (int i = 0; i < 30 && out == null; i++) out = vad.accept(new float[F]);
			System.out.printf("[vad] %s: peak %.3f, max frame rms %.3f, phrase %s%n", name, peak, maxLevel,
					out == null ? "none" : (out.length / 16) + " ms");
			assertNotNull(out, name + ".wav should produce a phrase");
		}
	}

	@Test
	void steadyFanNoiseRaisesTheFloor() {
		VoiceActivityDetector vad = new VoiceActivityDetector();
		// A loud-ish constant fan: at first it may look like speech, but it must not keep
		// producing phrases forever once the floor has adapted.
		int phrases = 0;
		for (int i = 0; i < 600; i++) if (vad.accept(noise(0.02f)) != null) phrases++;
		assertTrue(phrases <= 3, "fan noise produced " + phrases + " phrases");
	}
}
