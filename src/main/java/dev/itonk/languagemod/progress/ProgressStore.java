package dev.itonk.languagemod.progress;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Saves each player's dictionary per language as <world>/languagemod/players/<lang>/<uuid>.json
 * (written via a temp file), so switching language starts a fresh dictionary and switching back
 * restores the old one. Saves from before languages were split (players/<uuid>.json) were Romanian.
 */
public final class ProgressStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path dir;

	public ProgressStore(Path worldDir) {
		this.dir = worldDir.resolve("languagemod").resolve("players");
	}

	private Path file(UUID player, String language) {
		return dir.resolve(language).resolve(player + ".json");
	}

	public WordProgress load(UUID player, String language) {
		Path file = file(player, language);
		Path legacy = dir.resolve(player + ".json");
		if (!Files.exists(file) && "ro".equals(language) && Files.exists(legacy)) file = legacy;
		if (!Files.exists(file)) return new WordProgress();
		try (Reader r = Files.newBufferedReader(file)) {
			WordProgress p = GSON.fromJson(r, WordProgress.class);
			return p != null ? p : new WordProgress();
		} catch (IOException | RuntimeException e) {
			throw new IllegalStateException("Could not read " + file, e);
		}
	}

	public void save(UUID player, String language, WordProgress progress) throws IOException {
		Path file = file(player, language);
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(player + ".json.tmp");
		try (Writer w = Files.newBufferedWriter(tmp)) {
			GSON.toJson(progress, w);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
