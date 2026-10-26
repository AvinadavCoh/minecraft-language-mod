package dev.itonk.languagemod.client;

import dev.itonk.languagemod.network.HudPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's dictionary (key J): every word met so far, grouped by what needs work.
 * Pure view: the server sends the data (DictionarySnapshot), this only draws and scrolls.
 */
public final class DictionaryScreen extends Screen {
	/** Section order on screen, by WordProgress.Status ordinal. */
	private static final int[] SECTION_ORDER = {0, 2, 3, 1};
	private static final String[] SECTION_NAMES = {"Needs repeating", "New", "Learning", "Known"};
	private static final int[] SECTION_COLORS = {0xFFFF7777, 0xFFAAAAAA, 0xFFFFD34E, 0xFF55FF55};
	private static final int ROW_H = 20;
	private static final int HEADER_H = 16;

	private static List<HudPayloads.DictionaryEntry> latest;

	private double scroll;
	/** Neither the server nor client-only mode has a dictionary (yet: the mode starts ~2 s after joining). */
	private boolean unavailable;

	public DictionaryScreen() {
		super(Text.literal("Dictionary"));
	}

	/** Called by the packet receiver (client thread). */
	static void receive(List<HudPayloads.DictionaryEntry> entries) {
		latest = entries;
	}

	@Override
	protected void init() {
		latest = null;
		unavailable = false;
		if (ClientPlayNetworking.canSend(HudPayloads.DictionaryRequest.ID)) {
			ClientPlayNetworking.send(HudPayloads.DictionaryRequest.INSTANCE);
		} else if (LanguageModClient.CLIENT_ONLY.active()) {
			latest = LanguageModClient.CLIENT_ONLY.snapshot();
		} else {
			unavailable = true;
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		scroll = Math.max(0, scroll - vertical * ROW_H * 2);
		return true;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		super.render(ctx, mouseX, mouseY, delta);
		int w = Math.min(360, width - 20);
		int x = (width - w) / 2;
		int top = 36, bottom = height - 16;
		ctx.fill(x - 4, top - 4, x + w + 4, bottom + 4, 0xD8101418);
		ctx.drawStrokedRectangle(x - 5, top - 5, w + 10, bottom - top + 10, 0xFFFFD34E);
		ctx.drawCenteredTextWithShadow(textRenderer, "My dictionary", width / 2, 6, 0xFFFFFFFF);

		List<HudPayloads.DictionaryEntry> entries = latest;
		if (entries == null) {
			ctx.drawCenteredTextWithShadow(textRenderer, unavailable ? "Not ready yet: close and press J again in a moment." : "Loading…",
					width / 2, top + 10, 0xFFAAAAAA);
			return;
		}
		if (entries.isEmpty()) {
			ctx.drawCenteredTextWithShadow(textRenderer, "No words yet: pick up some items!", width / 2, top + 10, 0xFFAAAAAA);
			return;
		}

		// Counts line under the title.
		int[] counts = new int[4];
		for (var e : entries) counts[e.status()]++;
		String summary = counts[0] + " to repeat · " + counts[2] + " learning · " + counts[3] + " known · " + counts[1] + " new";
		ctx.drawCenteredTextWithShadow(textRenderer, summary, width / 2, 18, 0xFFAAAAAA);

		ctx.enableScissor(x - 4, top, x + w + 4, bottom);
		int y = top - (int) scroll;
		int contentH = 0;
		for (int status : SECTION_ORDER) {
			List<HudPayloads.DictionaryEntry> rows = new ArrayList<>();
			for (var e : entries) if (e.status() == status) rows.add(e);
			if (rows.isEmpty()) continue;
			ctx.drawTextWithShadow(textRenderer, SECTION_NAMES[status] + " (" + rows.size() + ")", x, y + 4, SECTION_COLORS[status]);
			y += HEADER_H;
			contentH += HEADER_H;
			for (var e : rows) {
				if (y > top - ROW_H && y < bottom) drawRow(ctx, e, x, y, w, SECTION_COLORS[status]);
				y += ROW_H;
				contentH += ROW_H;
			}
			y += 6;
			contentH += 6;
		}
		ctx.disableScissor();
		scroll = Math.min(scroll, Math.max(0, contentH - (bottom - top)));
	}

	private void drawRow(DrawContext ctx, HudPayloads.DictionaryEntry e, int x, int y, int w, int color) {
		Identifier id = Identifier.tryParse(e.itemId());
		ItemStack stack = id == null ? ItemStack.EMPTY : new ItemStack(Registries.ITEM.get(id));
		ctx.drawItem(stack, x, y + 1);
		ctx.drawTextWithShadow(textRenderer, e.word(), x + 22, y + 2, 0xFFFFFFFF);
		ctx.drawTextWithShadow(textRenderer, stack.getName(), x + 22, y + 11, 0xFF808080);

		// Right side: ✓/✗ counts, box progress, due time.
		String stats = "✓" + e.correct() + " ✗" + e.misses() + (e.wrongTries() > 0 ? " ~" + e.wrongTries() : "");
		String due = e.status() == 0 ? (e.dueInSeconds() > 0 ? "again in " + duration(e.dueInSeconds()) : "due now")
				: e.box() == 0 ? "" : "next in " + duration(e.dueInSeconds());
		int rx = x + w;
		ctx.drawTextWithShadow(textRenderer, stats, rx - textRenderer.getWidth(stats), y + 2, 0xFFCCCCCC);
		ctx.drawTextWithShadow(textRenderer, due, rx - textRenderer.getWidth(due), y + 11, 0xFF808080);

		// Box dots: filled = boxes climbed (5 = fully known).
		int bx = rx - textRenderer.getWidth(stats) - 8 - 5 * 6;
		for (int i = 0; i < 5; i++) {
			ctx.fill(bx + i * 6, y + 4, bx + i * 6 + 4, y + 8, i < e.box() ? color : 0xFF333333);
		}
	}

	private static String duration(int seconds) {
		if (seconds < 60) return seconds + "s";
		if (seconds < 3600) return seconds / 60 + "m";
		return seconds / 3600 + "h" + (seconds % 3600 >= 60 ? " " + (seconds % 3600) / 60 + "m" : "");
	}
}
