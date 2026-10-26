package dev.itonk.languagemod.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceTextTest {
	@Test
	void normalAnswersPassThrough() {
		assertEquals("Scânduri de stejar", VoiceText.clean(" Scânduri de stejar "));
		assertEquals("Lingou de cupru", VoiceText.clean("Lingou de cupru"));
		assertEquals("pană pană", VoiceText.clean("pană pană"), "saying a word twice is fine");
	}

	@Test
	void whisperLoopIsDropped() {
		// What a real PC got for 1 s of Hebrew audio (it disconnected the player before this fix).
		String loop = "‫-אה, ".repeat(44);
		assertEquals("", VoiceText.clean(loop));
	}

	@Test
	void longAnswersAreCappedBelowThePacketLimit() {
		String rambling = "torce înălțită în casa ca să fie monstări și apoi am mers acasă să mâncăm ceva bun cu toții împreună azi seară";
		assertTrue(VoiceText.clean(rambling).length() <= VoiceText.MAX_CHARS);
	}

	@Test
	void hebrewDirectionMarksAreRemoved() {
		assertEquals("עכשיו", VoiceText.clean("‫עכשיו‬"));
	}
}
