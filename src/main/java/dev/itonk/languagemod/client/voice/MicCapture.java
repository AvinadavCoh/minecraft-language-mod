package dev.itonk.languagemod.client.voice;

import dev.itonk.languagemod.voice.VoiceActivityDetector;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.util.function.Consumer;

/**
 * Reads the default microphone as 16 kHz mono frames on its own thread and hands each
 * frame to a consumer. The device is only open between start() and stop(), so Windows'
 * "mic in use" indicator is only on while a challenge is up.
 */
final class MicCapture {
	private static final AudioFormat FORMAT = new AudioFormat(VoiceActivityDetector.SAMPLE_RATE, 16, 1, true, false);

	private final Consumer<float[]> frames;
	private volatile Thread thread;
	private volatile TargetDataLine line;
	private volatile float gain = 1f;

	MicCapture(Consumer<float[]> frames) {
		this.frames = frames;
	}

	void setGain(float gain) {
		this.gain = gain;
	}

	/** Opens the mic. Throws if there is no usable input device. */
	synchronized void start() throws LineUnavailableException {
		if (thread != null) return;
		TargetDataLine l = AudioSystem.getTargetDataLine(FORMAT);
		l.open(FORMAT, VoiceActivityDetector.FRAME * 2 * 8);
		l.start();
		line = l;
		Thread t = new Thread(this::run, "LanguageMod-Mic");
		t.setDaemon(true);
		thread = t;
		t.start();
	}

	synchronized void stop() {
		Thread t = thread;
		thread = null;
		TargetDataLine l = line;
		line = null;
		if (l != null) {
			l.stop();
			l.close(); // unblocks read()
		}
		if (t != null) t.interrupt();
	}

	boolean running() {
		return thread != null;
	}

	private void run() {
		byte[] bytes = new byte[VoiceActivityDetector.FRAME * 2];
		while (thread == Thread.currentThread()) {
			TargetDataLine l = line;
			if (l == null) break;
			int read = 0;
			while (read < bytes.length) {
				int n = l.read(bytes, read, bytes.length - read);
				if (n <= 0) break;
				read += n;
			}
			if (read < bytes.length) break; // line closed
			float[] frame = new float[VoiceActivityDetector.FRAME];
			float g = gain;
			for (int i = 0; i < frame.length; i++) {
				short s = (short) ((bytes[2 * i] & 0xFF) | (bytes[2 * i + 1] << 8));
				frame[i] = Math.max(-1f, Math.min(1f, s / 32768f * g));
			}
			frames.accept(frame);
		}
	}
}
