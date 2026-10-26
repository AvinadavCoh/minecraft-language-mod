package dev.itonk.languagemod.voice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GPU first, CPU when the GPU helper isn't ready yet, has died, or a request fails.
 * Use from one worker thread.
 */
public final class FallbackTranscriber implements SpeechTranscriber {
	private static final Logger LOG = LoggerFactory.getLogger("languagemod");

	private final WhisperServerTranscriber gpu;
	private final SpeechTranscriber cpu;
	private String lastEngine = "CPU";

	public FallbackTranscriber(WhisperServerTranscriber gpu, SpeechTranscriber cpu) {
		this.gpu = gpu;
		this.cpu = cpu;
	}

	/** "GPU" or "CPU": which engine answered the last phrase (for the log). */
	public String lastEngine() {
		return lastEngine;
	}

	@Override
	public String transcribe(float[] pcm, String languageCode, String hint) {
		if (gpu.ready()) {
			try {
				String text = gpu.transcribe(pcm, languageCode, hint);
				lastEngine = "GPU";
				return text;
			} catch (RuntimeException e) {
				LOG.warn("[voice] GPU Whisper failed, using the CPU for this phrase: {}", e.getMessage());
			}
		}
		lastEngine = "CPU";
		return cpu.transcribe(pcm, languageCode, hint);
	}

	@Override
	public void close() {
		gpu.close();
		cpu.close();
	}
}
