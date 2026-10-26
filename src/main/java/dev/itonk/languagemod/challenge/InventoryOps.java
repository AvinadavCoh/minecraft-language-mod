package dev.itonk.languagemod.challenge;

import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Every inventory read/write the mod does goes through here.
 *
 * Thread-safety rule: call these ONLY on the server thread (server tick, command
 * execution, or inside server.execute(...)). On an integrated server the client
 * thread and the server thread are different threads; writing to the server-side
 * PlayerInventory from anywhere else races with vanilla's own tick and causes
 * ghost items or desyncs. When called on the server thread, no manual packets are
 * needed: vanilla's ServerPlayerEntity tick runs currentScreenHandler.sendContentUpdates(),
 * which diffs every slot and sends the change to the client.
 */
public final class InventoryOps {
	private InventoryOps() {}

	public static Identifier idOf(ItemStack stack) {
		return Registries.ITEM.getId(stack.getItem());
	}

	/** True if the slot still holds the given item type. */
	public static boolean slotHolds(PlayerInventory inv, int slot, Identifier itemId) {
		if (slot < 0 || slot >= inv.size()) return false;
		ItemStack stack = inv.getStack(slot);
		return !stack.isEmpty() && idOf(stack).equals(itemId);
	}

	/** Finds the item anywhere in the inventory (main, hotbar, armor, offhand), or -1. */
	public static int findSlot(PlayerInventory inv, Identifier itemId) {
		for (int i = 0; i < inv.size(); i++) {
			if (slotHolds(inv, i, itemId)) return i;
		}
		return -1;
	}

	/**
	 * Removes the whole stack in a slot and returns a copy of what was removed
	 * (the copy is used for the break particles; the original is gone).
	 * Also clears the cursor stack if the player is holding that item there,
	 * so it can't survive inside an open inventory screen.
	 */
	public static ItemStack deleteStack(ServerPlayerEntity player, int slot) {
		PlayerInventory inv = player.getInventory();
		ItemStack removed = inv.getStack(slot).copy();
		inv.setStack(slot, ItemStack.EMPTY);
		inv.markDirty();

		ItemStack cursor = player.currentScreenHandler.getCursorStack();
		if (!cursor.isEmpty() && !removed.isEmpty() && cursor.isOf(removed.getItem())) {
			player.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
		}
		// Push the change now instead of waiting for the end-of-tick diff, so the
		// item vanishes in the same frame the break sound plays.
		player.currentScreenHandler.sendContentUpdates();
		return removed;
	}
}
