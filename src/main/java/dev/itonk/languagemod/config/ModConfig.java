package dev.itonk.languagemod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tunable numbers, read from config/languagemod/config.json.
 * The bundled defaults are copied there on first launch so the file is editable.
 */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public String targetLanguage = "ro";
	public String targetLanguageName = "Romanian";
	public int timeoutSeconds = 15;
	public float damage = 1.0f;
	public int scanIntervalTicks = 10;
	public boolean freezeMovement = true;
	public boolean challengeItemsHeldOnJoin = false;
	public boolean skipCreativeAndSpectator = true;
	public boolean ignoreItemsMissingFromDictionary = true;
	/** false = retry until the timer runs out (letters shown green/red); true = first wrong try punishes. */
	public boolean wrongAnswerPunishes = false;
	/** Plain chat messages count as answers while a challenge is open (and aren't broadcast). */
	public boolean chatAnswers = true;

	public static ModConfig load(Path configDir) throws IOException {
		Path file = configDir.resolve("config.json");
		copyDefaultIfMissing("config.json", file);
		try (Reader reader = Files.newBufferedReader(file)) {
			ModConfig config = GSON.fromJson(reader, ModConfig.class);
			return config != null ? config : new ModConfig();
		}
	}

	/** Copies a file from src/main/resources/languagemod/defaults into the config dir if absent. */
	public static void copyDefaultIfMissing(String name, Path target) throws IOException {
		if (Files.exists(target)) return;
		Files.createDirectories(target.getParent());
		try (InputStream in = ModConfig.class.getResourceAsStream("/languagemod/defaults/" + name)) {
			if (in == null) return;
			Files.copy(in, target);
		}
	}
}
