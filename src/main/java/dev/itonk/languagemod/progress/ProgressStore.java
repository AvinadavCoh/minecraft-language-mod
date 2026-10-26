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

/** Saves each player's dictionary as <world>/languagemod/players/<uuid>.json (written via a temp file). */
public final class ProgressStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path dir;

	public ProgressStore(Path worldDir) {
		this.dir = worldDir.resolve("languagemod").resolve("players");
	}

	public WordProgress load(UUID player) {
		Path file = dir.resolve(player + ".json");
		if (!Files.exists(file)) return new WordProgress();
		try (Reader r = Files.newBufferedReader(file)) {
			WordProgress p = GSON.fromJson(r, WordProgress.class);
			return p != null ? p : new WordProgress();
		} catch (IOException | RuntimeException e) {
			throw new IllegalStateException("Could not read " + file, e);
		}
	}

	public void save(UUID player, WordProgress progress) throws IOException {
		Files.createDirectories(dir);
		Path file = dir.resolve(player + ".json");
		Path tmp = dir.resolve(player + ".json.tmp");
		try (Writer w = Files.newBufferedWriter(tmp)) {
			GSON.toJson(progress, w);
		}
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
