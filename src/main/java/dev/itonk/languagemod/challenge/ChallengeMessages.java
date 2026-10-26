package dev.itonk.languagemod.challenge;

import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/** The chat lines, built the same way on the server and in client-only mode. Text only, no sending. */
public final class ChallengeMessages {
	private static final Text PREFIX = Text.literal("[LanguageMod] ").formatted(Formatting.GOLD);

	private ChallengeMessages() {}

	private static MutableText line() {
		return PREFIX.copy();
	}

	public static Text prompt(ItemStack stack, String word, String languageName) {
		return line()
				.append(Text.literal("Name this item in " + languageName + "! ").formatted(Formatting.YELLOW))
				.append(stack.getName().copy().formatted(Formatting.AQUA))
				.append(Text.literal(" → ").formatted(Formatting.GRAY))
				.append(Text.literal(word).formatted(Formatting.WHITE, Formatting.BOLD));
	}

	/** The word with each letter green (right) or red (wrong). */
	public static Text attempt(String attempt, String word, boolean[] mask) {
		MutableText colored = Text.empty();
		for (int i = 0; i < word.length(); i++) {
			colored.append(Text.literal(String.valueOf(word.charAt(i))).formatted(mask[i] ? Formatting.GREEN : Formatting.RED));
		}
		return line().append(Text.literal("\"" + attempt + "\" → ").formatted(Formatting.GRAY)).append(colored);
	}

	public static Text success(ItemStack stack) {
		return line().append(Text.literal("Correct! ").formatted(Formatting.GREEN)).append(stack.getName());
	}

	/** @param outcome what happened to the item, e.g. " (item kept)"; empty for none. */
	public static Text failure(String reason, String answer, String outcome) {
		String hint = answer != null ? " It was: " + answer : "";
		return line().append(Text.literal(reason + hint + outcome).formatted(Formatting.RED));
	}

	/** On join: the mod is on and what happens next, so a quiet start doesn't look broken. */
	public static List<Text> welcome(String languageName, int carried, int gapSeconds, boolean off, String modeNote) {
		List<Text> lines = new ArrayList<>();
		lines.add(line().append(Text.literal("Learning " + languageName
				+ ": new items are asked when you pick them up. J = your dictionary.").formatted(Formatting.YELLOW)));
		if (modeNote != null) lines.add(line().append(Text.literal(modeNote).formatted(Formatting.GRAY)));
		if (carried > 0) {
			lines.add(line().append(Text.literal(carried + " items you carry will be asked one by one, every "
					+ gapSeconds + " s.").formatted(Formatting.GRAY)));
		}
		if (off) {
			lines.add(line().append(Text.literal("You're in creative/spectator: no challenges until you switch to survival.")
					.formatted(Formatting.RED)));
		}
		return lines;
	}
}
