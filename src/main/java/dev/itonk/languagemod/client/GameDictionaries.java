package dev.itonk.languagemod.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.itonk.languagemod.LanguageMod;
import dev.itonk.languagemod.dictionary.DictionaryBuilder;
import dev.itonk.languagemod.dictionary.LanguageList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Makes sure config/languagemod/<code>.json is a full dictionary, built from the game's own language file. */
final class GameDictionaries {
	/** A file with fewer entries is the mod's short hand-written starter list: upgrade it. */
	private static final int FULL_SIZE = 500;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private GameDictionaries() {}

	/** Client thread. Returns false (and logs why) if the game has no such language file. */
	static boolean ensure(MinecraftClient client, LanguageList.Language lang, Path configDir) {
		Path file = configDir.resolve(lang.code() + ".json");
		try {
			if (Files.isRegularFile(file) && itemCount(file) >= FULL_SIZE) return true;
			List<Resource> found = client.getResourceManager().getAllResources(Identifier.ofVanilla("lang/" + lang.locale() + ".json"));
			if (found.isEmpty()) {
				LanguageMod.LOG.warn("[settings] the game has no {} language file", lang.locale());
				return Files.isRegularFile(file);
			}
			JsonObject official;
			try (Reader r = found.getFirst().getReader()) {
				official = GSON.fromJson(r, JsonObject.class);
			}
			JsonObject hand = null;
			try (InputStream in = GameDictionaries.class.getResourceAsStream("/languagemod/defaults/" + lang.code() + ".json")) {
				if (in != null) hand = GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
			}
			JsonObject dict = DictionaryBuilder.build(official, hand, lang.locale() + ".json");
			Files.createDirectories(configDir);
			Files.writeString(file, GSON.toJson(dict), StandardCharsets.UTF_8);
			LanguageMod.LOG.info("[settings] built {} with {} items from the game's {}", file.getFileName(),
					dict.getAsJsonObject("items").size(), lang.locale());
			return true;
		} catch (Exception e) {
			LanguageMod.LOG.error("[settings] could not build the {} dictionary", lang.name(), e);
			return Files.isRegularFile(file);
		}
	}

	private static int itemCount(Path file) {
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject o = GSON.fromJson(r, JsonObject.class);
			return o != null && o.has("items") ? o.getAsJsonObject("items").size() : 0;
		} catch (Exception e) {
			return 0;
		}
	}
}
