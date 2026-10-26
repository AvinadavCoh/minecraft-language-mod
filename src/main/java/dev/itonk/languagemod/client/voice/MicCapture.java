package dev.itonk.languagemod.client.voice;

import dev.itonk.languagemod.LanguageMod;
import dev.itonk.languagemod.voice.VoiceActivityDetector;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Reads a microphone as 16 kHz mono frames on its own thread and hands each frame to a
 * consumer. The device is only open between start() and stop(), so Windows' "mic in use"
 * indicator is only on while a challenge is up.
 *
 * Device choice: Java's default ("Primary Sound Capture Driver") follows Windows' default
 * recording device, which on some PCs is Line In rather than the microphone. So unless
 * voice.json names a device, prefer a real device called "Microphone ...".
 */
final class MicCapture {
	private static final AudioFormat FORMAT = new AudioFormat(VoiceActivityDetector.SAMPLE_RATE, 16, 1, true, false);
	private static final DataLine.Info INFO = new DataLine.Info(TargetDataLine.class, FORMAT);

	private final Consumer<float[]> frames;
	private final String wantedDevice;
	private volatile Thread thread;
	private volatile TargetDataLine line;
	private volatile float gain = 1f;
	private volatile String deviceName = "";

	MicCapture(Consumer<float[]> frames, String wantedDevice) {
		this.frames = frames;
		this.wantedDevice = wantedDevice == null ? "" : wantedDevice.trim();
	}

	void setGain(float gain) {
		this.gain = gain;
	}

	String deviceName() {
		return deviceName;
	}

	/** All capture devices that can deliver 16 kHz mono. */
	static List<Mixer.Info> captureDevices() {
		List<Mixer.Info> out = new ArrayList<>();
		for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
			if (AudioSystem.getMixer(mi).isLineSupported(INFO)) out.add(mi);
		}
		return out;
	}

	/** voice.json "micDevice" (substring) > a real "Microphone" device > Java's default. */
	static Mixer.Info choose(List<Mixer.Info> devices, String wanted) {
		String w = wanted.toLowerCase(Locale.ROOT);
		if (!w.isEmpty()) {
			for (Mixer.Info mi : devices) if (mi.getName().toLowerCase(Locale.ROOT).contains(w)) return mi;
		}
		for (Mixer.Info mi : devices) {
			String n = mi.getName().toLowerCase(Locale.ROOT);
			if (n.startsWith("microphone") && !n.contains("steam") && !n.contains("virtual")) return mi;
		}
		return null; // default device
	}

	/** Opens the mic. Throws if there is no usable input device. */
	synchronized void start() throws LineUnavailableException {
		if (thread != null) return;
		Mixer.Info chosen = choose(captureDevices(), wantedDevice);
		TargetDataLine l = chosen != null
				? (TargetDataLine) AudioSystem.getMixer(chosen).getLine(INFO)
				: AudioSystem.getTargetDataLine(FORMAT);
		deviceName = chosen != null ? chosen.getName() : "Windows default";
		l.open(FORMAT, VoiceActivityDetector.FRAME * 2 * 8);
		l.start();
		line = l;
		LanguageMod.LOG.info("[voice] mic opened: {}", deviceName);
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
