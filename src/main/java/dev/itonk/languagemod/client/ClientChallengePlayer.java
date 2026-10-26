package dev.itonk.languagemod.client;

import dev.itonk.languagemod.challenge.ChallengeMessages;
import dev.itonk.languagemod.challenge.ChallengePlayer;
import dev.itonk.languagemod.network.HudPayloads;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * Client-only mode's player: the server doesn't run the mod, so everything happens in this
 * game. A miss can't deal real damage (only the server can), so it plays the hurt shake,
 * a red flash and the hurt sound; "delete the item" throws the stack out like Ctrl+Q.
 * Client thread only.
 */
final class ClientChallengePlayer implements ChallengePlayer {
	static final String MODE_NOTE = "Client-only mode: this server doesn't have the mod, so a miss shakes you instead of real damage.";

	private final MinecraftClient client;

	ClientChallengePlayer(MinecraftClient client) {
		this.client = client;
	}

	private ClientPlayerEntity player() {
		return client.player;
	}

	@Override public UUID uuid() { return player().getUuid(); }

	@Override public PlayerInventory inventory() { return player().getInventory(); }

	@Override public boolean isAlive() { return player().isAlive(); }

	@Override public boolean isCreativeOrSpectator() { return player().isCreative() || player().isSpectator(); }

	@Override
	public void prompt(ItemStack stack, String word, String languageName, String languageCode, int durationTicks) {
		chat(ChallengeMessages.prompt(stack, word, languageName));
		LanguageModClient.HUD.start(stack.copyWithCount(1), word, languageName, durationTicks);
		LanguageModClient.VOICE.onChallengeStart(languageCode, word);
		sound(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1f);
	}

	@Override
	public void attempt(String attempt, String word, boolean[] mask) {
		chat(ChallengeMessages.attempt(attempt, word, mask));
		LanguageModClient.HUD.attempt(attempt, HudPayloads.toPattern(mask));
		sound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.7f);
	}

	@Override
	public void success(ItemStack stack) {
		chat(ChallengeMessages.success(stack));
		LanguageModClient.HUD.end(true, true);
		LanguageModClient.VOICE.onChallengeEnd();
		sound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f);
	}

	@Override
	public void punish(int slot, boolean keep, String reason, String answer, float damage) {
		boolean thrown = !keep && slot >= 0 && throwStack(slot);
		chat(ChallengeMessages.failure(reason, answer, keep ? " (item kept)" : thrown ? " (item thrown away)" : ""));
		LanguageModClient.HUD.end(false, keep);
		LanguageModClient.HUD.hurt();
		LanguageModClient.VOICE.onChallengeEnd();
		player().animateDamage(0f); // the vanilla hurt camera shake
		sound(SoundEvents.ENTITY_PLAYER_HURT, 1f);
		if (thrown) sound(SoundEvents.ENTITY_ITEM_BREAK.value(), 1f);
	}

	@Override
	public void welcome(String languageName, int carried, int gapSeconds, boolean off) {
		for (Text line : ChallengeMessages.welcome(languageName, carried, gapSeconds, off, MODE_NOTE)) chat(line);
	}

	/**
	 * Throws the whole stack out of the inventory through the normal slot-click packet, the
	 * same thing Ctrl+Q over a slot does, so any server accepts it.
	 */
	private boolean throwStack(int inventorySlot) {
		int handlerSlot = toScreenSlot(inventorySlot);
		ClientPlayerEntity p = player();
		if (handlerSlot < 0 || client.interactionManager == null) return false;
		if (p.currentScreenHandler != p.playerScreenHandler) p.closeHandledScreen();
		client.interactionManager.clickSlot(p.playerScreenHandler.syncId, handlerSlot, 1, SlotActionType.THROW, p);
		return true;
	}

	/** PlayerInventory index -> PlayerScreenHandler slot id (hotbar 36-44, main 9-35, armor 5-8, offhand 45). */
	static int toScreenSlot(int i) {
		if (i >= 0 && i < 9) return 36 + i;
		if (i < 36) return i;
		if (i < 40) return 8 - (i - 36);
		if (i == 40) return 45;
		return -1;
	}

	private void chat(Text line) {
		player().sendMessage(line, false);
	}

	private void sound(SoundEvent event, float pitch) {
		client.getSoundManager().play(PositionedSoundInstance.ui(event, pitch));
	}
}
