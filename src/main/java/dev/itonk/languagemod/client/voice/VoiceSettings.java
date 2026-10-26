package dev.itonk.languagemod.client.voice;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-only voice settings: config/languagemod/voice.json (written with defaults on first launch). */
public final class VoiceSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** "open" = mic listens whenever a challenge is up; "push_to_talk" = hold the Talk key (V); "off". */
	public String mode = "open";
	public String model = "ggml-small-q5_1.bin";
	public String modelBaseUrl = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/";
	public boolean autoDownloadModel = true;
	/** Measured on a Ryzen 5 3600: 6 threads + window 1000 -> ~4 s per answer, all test words right. */
	public int whisperThreads = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors() / 2));
	/** Whisper encoder window in frames (50 = 1 s). 1500 = full/slowest; below ~1000 it adds junk endings. */
	public int audioContext = 1000;
	/** Give Whisper the word on screen as a hint: far better recognition of single words, slightly more forgiving. */
	public boolean hintExpectedWord = true;
	/** Part of the input device's name, e.g. "Realtek". Empty = first real "Microphone" device (the log lists them all). */
	public String micDevice = "";
	/** Save what the mic heard to config/languagemod/recordings/ (whole challenge + each phrase sent to Whisper). */
	public boolean saveRecordings = false;
	/** Multiplies mic samples before voice detection; raise for a quiet mic. */
	public float micGain = 1.0f;

	public static VoiceSettings load(Path dir) {
		VoiceSettings s = loadFile(dir);
		// -Dlanguagemod.voice.mode=... overrides the file (the bot test uses it so a real mic can't interfere).
		String forced = System.getProperty("languagemod.voice.mode");
		if (forced != null) s.mode = forced;
		return s;
	}

	private static VoiceSettings loadFile(Path dir) {
		Path file = dir.resolve("voice.json");
		try {
			if (Files.exists(file)) {
				try (Reader r = Files.newBufferedReader(file)) {
					VoiceSettings s = GSON.fromJson(r, VoiceSettings.class);
					if (s != null) return s;
				}
			}
			VoiceSettings defaults = new VoiceSettings();
			Files.createDirectories(dir);
			try (Writer w = Files.newBufferedWriter(file)) {
				GSON.toJson(defaults, w);
			}
			return defaults;
		} catch (IOException | RuntimeException e) {
			return new VoiceSettings();
		}
	}

	public boolean open() {
		return "open".equalsIgnoreCase(mode);
	}

	public boolean pushToTalk() {
		return "push_to_talk".equalsIgnoreCase(mode);
	}
}
