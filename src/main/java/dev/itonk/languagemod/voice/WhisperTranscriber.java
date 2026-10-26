package dev.itonk.languagemod.voice;

import io.github.givimad.whisperjni.WhisperContext;
import io.github.givimad.whisperjni.WhisperFullParams;
import io.github.givimad.whisperjni.WhisperJNI;
import io.github.givimad.whisperjni.WhisperSamplingStrategy;

import java.io.IOException;
import java.nio.file.Path;

/**
 * whisper.cpp through whisper-jni, running in-process on the CPU. No audio leaves the PC.
 * Not thread-safe: use from one worker thread.
 */
public final class WhisperTranscriber implements SpeechTranscriber {
	private static final int MIN_SAMPLES = 16_000 * 3 / 2; // pad to 1.5 s (2 s / 3 s measured: no difference)
	private static boolean libraryLoaded;

	private final WhisperJNI whisper;
	private final WhisperContext context;
	private final int threads;
	/** Minimum encoder window in context frames (50 per second); 0 = Whisper's full 30 s. */
	private volatile int audioCtx = 0;

	public void setAudioCtx(int audioCtx) {
		this.audioCtx = audioCtx;
	}

	public WhisperTranscriber(Path model, int threads) throws IOException {
		synchronized (WhisperTranscriber.class) {
			if (!libraryLoaded) {
				WhisperJNI.loadLibrary();
				libraryLoaded = true;
			}
		}
		this.whisper = new WhisperJNI();
		this.context = whisper.init(model);
		this.threads = threads;
	}

	@Override
	public String transcribe(float[] pcm, String languageCode, String hint) {
		// whisper.cpp rejects input under 1 s ("input is too short"); a one-word answer often is.
		if (pcm.length < MIN_SAMPLES) pcm = java.util.Arrays.copyOf(pcm, MIN_SAMPLES);
		WhisperFullParams params = new WhisperFullParams(WhisperSamplingStrategy.GREEDY);
		// Whisper normally encodes a fixed 30 s window. A smaller slice is faster; too small
		// makes it loop ("Morse Morse ..."). 50 context frames = 1 s of audio.
		params.audioCtx = audioCtx <= 0 ? 0 : Math.min(1500, Math.max(audioCtx, pcm.length / 320 + 64));
		params.temperatureInc = 0f; // no slow re-decode fallbacks
		if (hint != null && !hint.isBlank()) params.initialPrompt = hint;
		params.language = languageCode;
		params.translate = false;
		params.noTimestamps = true;
		params.singleSegment = true;
		params.noContext = true;
		params.suppressBlank = true;
		params.suppressNonSpeechTokens = true;
		params.temperature = 0f;
		params.nThreads = threads;
		params.printProgress = false;
		params.printRealtime = false;
		params.printTimestamps = false;

		int result = whisper.full(context, params, pcm, pcm.length);
		if (result != 0) throw new IllegalStateException("whisper_full failed with code " + result);
		StringBuilder text = new StringBuilder();
		int segments = whisper.fullNSegments(context);
		for (int i = 0; i < segments; i++) text.append(whisper.fullGetSegmentText(context, i));
		return text.toString().trim();
	}

	@Override
	public void close() {
		context.close();
	}
}
