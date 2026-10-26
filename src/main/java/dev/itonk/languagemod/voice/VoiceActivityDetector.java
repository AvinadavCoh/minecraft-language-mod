package dev.itonk.languagemod.voice;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Open-mic phrase cutter. Feed it fixed-size frames of 16 kHz mono PCM; it returns a whole
 * utterance (with a little audio before the first loud frame) once the speaker goes quiet.
 *
 * Energy based with an adaptive noise floor, so a fan or room hum doesn't count as speech.
 * Pure logic, no Minecraft or audio-device classes: unit-tested with synthetic signals.
 */
public final class VoiceActivityDetector {
	public static final int SAMPLE_RATE = 16_000;
	public static final int FRAME = 480; // 30 ms

	private static final int PRE_ROLL_FRAMES = 7;     // ~200 ms kept before speech starts
	private static final int HANGOVER_FRAMES = 14;    // ~420 ms of quiet ends a phrase (was 600 ms; owner: feedback too slow)
	private static final int MIN_SPEECH_FRAMES = 4;   // ~120 ms; "măr" is only ~7 loud frames, clicks are 1-2
	private static final int MAX_FRAMES = 200;        // 6 s hard cap per phrase
	private static final float MIN_THRESHOLD = 0.012f;
	private static final float FLOOR_FACTOR = 3.0f;

	private final Deque<float[]> preRoll = new ArrayDeque<>();
	private final Deque<float[]> phrase = new ArrayDeque<>();
	private float noiseFloor = 0.005f;
	private boolean inSpeech;
	private int speechFrames;
	private int quietFrames;
	private float lastLevel;

	/** Feeds one frame; returns a finished utterance or null. */
	public float[] accept(float[] frame) {
		float rms = rms(frame);
		lastLevel = rms;
		boolean loud = rms > Math.max(MIN_THRESHOLD, noiseFloor * FLOOR_FACTOR);

		if (!inSpeech) {
			// Track the noise floor only while nobody is talking.
			noiseFloor = noiseFloor * 0.95f + rms * 0.05f;
			preRoll.addLast(frame);
			if (preRoll.size() > PRE_ROLL_FRAMES) preRoll.removeFirst();
			if (loud) {
				inSpeech = true;
				speechFrames = 1;
				quietFrames = 0;
				phrase.clear();
				phrase.addAll(preRoll);
				preRoll.clear();
			}
			return null;
		}

		phrase.addLast(frame);
		if (loud) {
			speechFrames++;
			quietFrames = 0;
		} else {
			quietFrames++;
		}
		if (quietFrames >= HANGOVER_FRAMES || phrase.size() >= MAX_FRAMES) {
			inSpeech = false;
			float[] out = speechFrames >= MIN_SPEECH_FRAMES ? concat() : null;
			phrase.clear();
			return out;
		}
		return null;
	}

	public boolean inSpeech() {
		return inSpeech;
	}

	/** Loudness of the last frame, 0..1, for the level meter. */
	public float level() {
		return lastLevel;
	}

	public void reset() {
		preRoll.clear();
		phrase.clear();
		inSpeech = false;
	}

	private float[] concat() {
		float[] out = new float[phrase.size() * FRAME];
		int i = 0;
		for (float[] f : phrase) {
			System.arraycopy(f, 0, out, i, f.length);
			i += f.length;
		}
		return Arrays.copyOf(out, i);
	}

	static float rms(float[] frame) {
		double sum = 0;
		for (float s : frame) sum += s * s;
		return (float) Math.sqrt(sum / frame.length);
	}
}
