package dev.itonk.languagemod.voice;

/** Speech-to-text engine. Called on a worker thread, never the game thread. */
public interface SpeechTranscriber extends AutoCloseable {
	/**
	 * @param pcm 16 kHz mono samples in -1..1
	 * @param languageCode ISO 639-1, e.g. "ro"
	 * @param hint the word the player is expected to say (biases recognition), or null
	 * @return the recognised text, or "" if nothing was understood
	 */
	String transcribe(float[] pcm, String languageCode, String hint);

	@Override
	void close();
}
