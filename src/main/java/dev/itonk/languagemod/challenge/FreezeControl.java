package dev.itonk.languagemod.challenge;

import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * Freezes movement with attribute modifiers rather than teleporting the player back
 * every tick. Attributes are synced to the client, so client-side movement prediction
 * also stops: no rubber-banding. Modifiers are "temporary" (not saved to disk), so a
 * crash mid-challenge can't leave a player frozen forever.
 */
public final class FreezeControl {
	private static final Identifier FREEZE_ID = Identifier.of("languagemod", "freeze");

	private FreezeControl() {}

	public static void freeze(ServerPlayerEntity player) {
		apply(player, EntityAttributes.MOVEMENT_SPEED);
		apply(player, EntityAttributes.JUMP_STRENGTH);
	}

	public static void unfreeze(ServerPlayerEntity player) {
		remove(player, EntityAttributes.MOVEMENT_SPEED);
		remove(player, EntityAttributes.JUMP_STRENGTH);
	}

	private static void apply(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute) {
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance == null || instance.hasModifier(FREEZE_ID)) return;
		// -100% of the final value -> 0 speed / 0 jump.
		instance.addTemporaryModifier(new EntityAttributeModifier(
				FREEZE_ID, -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
	}

	private static void remove(ServerPlayerEntity player, RegistryEntry<EntityAttribute> attribute) {
		EntityAttributeInstance instance = player.getAttributeInstance(attribute);
		if (instance != null) instance.removeModifier(FREEZE_ID);
	}
}
