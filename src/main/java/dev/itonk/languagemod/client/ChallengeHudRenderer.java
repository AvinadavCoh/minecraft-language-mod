package dev.itonk.languagemod.client;

import dev.itonk.languagemod.client.voice.VoiceStatus;
import dev.itonk.languagemod.dictionary.BidiText;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import org.joml.Matrix3x2fStack;

/**
 * The challenge popup: item icon, the word to read aloud (letters turn green/red after a
 * try), and a time bar that drains from green to red. Pure drawing, reads ChallengeHudState.
 */
public final class ChallengeHudRenderer {
	private static final int BG = 0xD8101418;
	private static final int BG_CORRECT = 0xD8123A1A;
	private static final int BG_FAILED = 0xD83A1414;
	private static final int GOLD = 0xFFFFD34E;
	private static final int GREEN = 0xFF55FF55;
	private static final int RED = 0xFFFF5555;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int DARK = 0xFF2A2A2A;

	private static final int ICON_SCALE = 2;       // 16px item -> 32px
	private static final float WORD_SCALE = 2f;
	private static final int PAD = 8;
	private static final int SLIDE_TICKS = 5;

	private final ChallengeHudState state;

	private final VoiceStatus voice;

	public ChallengeHudRenderer(ChallengeHudState state, VoiceStatus voice) {
		this.state = state;
		this.voice = voice;
	}

	public void render(DrawContext ctx, RenderTickCounter tickCounter) {
		if (state.hurtTicks > 0) {
			int alpha = 0x70 * state.hurtTicks / ChallengeHudState.HURT_TICKS;
			ctx.fill(0, 0, ctx.getScaledWindowWidth(), ctx.getScaledWindowHeight(), alpha << 24 | 0xFF0000);
		}
		if (state.phase == ChallengeHudState.Phase.HIDDEN) return;
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.options.hudHidden) return;
		TextRenderer font = client.textRenderer;
		float partial = tickCounter.getTickProgress(false);

		String label = switch (state.phase) {
			case CORRECT -> "Correct!";
			case FAILED -> state.itemKept ? "Ouch! Item kept" : "Item lost!";
			default -> "Say it in " + state.languageName + ":";
		};
		String said = state.lastAttempt.isEmpty() ? "" : "you said: " + state.lastAttempt;

		int wordW = Math.round(font.getWidth(state.word) * WORD_SCALE);
		int textW = Math.max(wordW, Math.max(font.getWidth(label), font.getWidth(said)));
		int iconSize = 16 * ICON_SCALE;
		int panelW = Math.max(200, PAD + iconSize + PAD + textW + PAD);
		String micText = state.phase == ChallengeHudState.Phase.ASKING ? voice.statusText() : "";
		int panelH = micText.isEmpty() ? 64 : 76;
		panelW = Math.max(panelW, PAD + font.getWidth(micText) + 6 + 40 + PAD);

		int x = (ctx.getScaledWindowWidth() - panelW) / 2;
		int y = 10 + slideOffset(panelH, partial);

		int border = switch (state.phase) {
			case CORRECT -> GREEN;
			case FAILED -> RED;
			default -> GOLD;
		};
		int bg = switch (state.phase) {
			case CORRECT -> BG_CORRECT;
			case FAILED -> BG_FAILED;
			default -> BG;
		};
		ctx.fill(x, y, x + panelW, y + panelH, bg);
		ctx.drawStrokedRectangle(x - 1, y - 1, panelW + 2, panelH + 2, border);

		// Item icon, scaled up.
		Matrix3x2fStack m = ctx.getMatrices();
		m.pushMatrix();
		m.translate(x + PAD, y + 12);
		m.scale(ICON_SCALE, ICON_SCALE);
		ctx.drawItem(state.stack, 0, 0);
		m.popMatrix();

		int tx = x + PAD + iconSize + PAD;
		ctx.drawTextWithShadow(font, label, tx, y + 6, state.phase == ChallengeHudState.Phase.ASKING ? GRAY : border);

		// The word, big, one letter at a time so each can take its own colour.
		m.pushMatrix();
		m.translate(tx, y + 18);
		m.scale(WORD_SCALE, WORD_SCALE);
		int cx = 0;
		// One letter per draw call, so Minecraft can't reorder it: put Hebrew in screen order ourselves.
		// Colours still follow the spoken letters.
		for (int i : BidiText.visualOrder(state.word)) {
			String ch = String.valueOf(state.word.charAt(i));
			int color = state.mask == null ? WHITE : (state.mask.charAt(i) == 'G' ? GREEN : RED);
			ctx.drawTextWithShadow(font, ch, cx, 0, color);
			cx += font.getWidth(ch);
		}
		m.popMatrix();

		if (!said.isEmpty()) ctx.drawTextWithShadow(font, Text.literal(said), tx, y + 40, GRAY); // Text: Minecraft reorders Hebrew in it

		// Mic status + level meter, just above the time bar.
		if (!micText.isEmpty()) {
			int my = y + panelH - 21;
			ctx.drawTextWithShadow(font, micText, x + PAD, my, voice.statusColor());
			if (voice.showLevel()) {
				int mx = x + panelW - PAD - 40;
				ctx.fill(mx, my + 2, mx + 40, my + 6, DARK);
				float lvl = Math.min(1f, voice.level() * 8f); // speech RMS is ~0.02-0.15
				ctx.fill(mx, my + 2, mx + Math.round(40 * lvl), my + 6, voice.statusColor());
			}
		}

		// Time bar along the bottom edge.
		int barX = x + PAD, barW = panelW - 2 * PAD, barY = y + panelH - 9;
		float left = state.phase == ChallengeHudState.Phase.ASKING ? state.timeLeft(partial)
				: state.phase == ChallengeHudState.Phase.CORRECT ? state.timeLeft(0) : 0f;
		ctx.fill(barX, barY, barX + barW, barY + 4, DARK);
		ctx.fill(barX, barY, barX + Math.round(barW * left), barY + 4, barColor(left));
	}

	/** Slides in from above when it opens and back up after the result has shown. */
	private int slideOffset(int panelH, float partial) {
		float t;
		if (state.phase == ChallengeHudState.Phase.ASKING) {
			t = Math.min(1f, (state.elapsedTicks + partial) / SLIDE_TICKS);
		} else {
			float out = ChallengeHudState.RESULT_TICKS - (state.resultTicks + partial);
			t = Math.min(1f, out / SLIDE_TICKS);
		}
		float eased = 1f - (1f - t) * (1f - t);
		return Math.round(-(panelH + 12) * (1f - eased));
	}

	/** Green (full) -> yellow (half) -> red (empty). */
	private static int barColor(float left) {
		int r, g;
		if (left > 0.5f) {
			r = Math.round(255 * (1f - left) * 2f);
			g = 255;
		} else {
			r = 255;
			g = Math.round(255 * left * 2f);
		}
		return 0xFF000000 | (r << 16) | (g << 8) | 0x40;
	}
}
