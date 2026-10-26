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
	/** Stop walking/jumping while a word is asked. Off by default (owner, 2026-10-26: too annoying). */
	public boolean freezeMovement = false;
	public boolean challengeItemsHeldOnJoin = false;
	public boolean skipCreativeAndSpectator = true;
	public boolean ignoreItemsMissingFromDictionary = true;
	/** false = retry until the timer runs out (letters shown green/red); true = first wrong try punishes. */
	public boolean wrongAnswerPunishes = false;
	/** Plain chat messages count as answers while a challenge is open (and aren't broadcast). */
	public boolean chatAnswers = true;
	/** true = a miss destroys the stack; false = a miss only deals the damage and you keep the item. */
	public boolean deleteItemOnFail = true;
	/** Forgive small speech-to-text slips: 1 wrong letter for words of 4+ letters, 2 for 8+ ("Paană" = "Pană"). */
	public boolean fuzzyMatch = true;
	/** Play-time gaps before a word comes back, by box (each right answer moves up a box; a miss resets to 1). */
	public double[] reviewStepsMinutes = {5, 20, 60, 180, 600};
	/** After any challenge, wait this long (play time) before starting a review. New pickups don't wait. */
	public int reviewGapSeconds = 20;
	/** If the time runs out while the player is speaking or Whisper is still working, wait up to this long for the answer. */
	public int voiceGraceSeconds = 10;
	/** When the item is kept after a miss, wait this long before asking about it again. */
	public int retryDelaySeconds = 30;

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
