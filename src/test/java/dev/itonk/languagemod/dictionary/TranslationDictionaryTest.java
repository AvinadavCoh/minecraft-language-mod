package dev.itonk.languagemod.dictionary;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationDictionaryTest {
	private static TranslationDictionary bundled(String lang) {
		var in = TranslationDictionaryTest.class.getResourceAsStream("/languagemod/defaults/" + lang + ".json");
		JsonObject root = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), JsonObject.class);
		return TranslationDictionary.fromJson(lang, root);
	}

	@Test
	void romanianAcceptsDiacriticsCaseAndArticles() {
		TranslationDictionary ro = bundled("ro");
		Identifier oakLog = Identifier.of("minecraft", "oak_log");
		assertTrue(ro.matches(oakLog, "stejar"));
		assertTrue(ro.matches(oakLog, "  Buștean   de STEJAR "));
		assertTrue(ro.matches(Identifier.of("minecraft", "apple"), "un măr"));
		assertTrue(ro.matches(Identifier.of("minecraft", "apple"), "Mărul"));
		assertTrue(ro.matches(Identifier.of("minecraft", "apple"), " Măr."), "speech-to-text punctuation is ignored");
		assertTrue(ro.matches(oakLog, "Buștean de stejar!"));
		assertFalse(ro.matches(oakLog, "roble"));
		assertFalse(ro.matches(Identifier.of("minecraft", "nether_star"), "stea"));
	}

	@Test
	void spanishStillLoads() {
		TranslationDictionary es = bundled("es");
		assertTrue(es.matches(Identifier.of("minecraft", "oak_log"), "Roble"));
		assertTrue(es.matches(Identifier.of("minecraft", "cobblestone"), "el adoquín"));
	}
}
