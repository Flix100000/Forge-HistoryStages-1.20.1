package net.bananemdnsa.historystages.util;

import com.google.common.collect.Multimap;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.lock.LockGate;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Re-evaluates the attribute modifiers of a player's equipped/held items on demand.
 * Needed because vanilla caches per-slot modifiers and only recomputes on item change;
 * a stage lock/unlock while the item is held would otherwise not take effect until the
 * player re-selects the slot. Mirrors vanilla's apply logic and respects the lock gate.
 */
public final class HeldAttributeRefresher {

    private HeldAttributeRefresher() {}

    public static void refresh(Player player) {
        if (player.level().isClientSide()) return;
        boolean lockingEnabled = Config.COMMON.lockItemUsage.get() || Config.COMMON.individualLockItemUsage.get();
        AttributeMap attributes = player.getAttributes();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            Multimap<Attribute, AttributeModifier> modifiers = stack.getAttributeModifiers(slot);
            if (modifiers.isEmpty()) continue;

            boolean locked = lockingEnabled && LockGate.isActionLocked(stack, player, "use",
                    Config.COMMON.lockItemUsage, Config.COMMON.individualLockItemUsage);

            // Remove first so a re-add can't throw on a duplicate modifier id, then re-apply
            // only when the item is not (or no longer) locked.
            attributes.removeAttributeModifiers(modifiers);
            if (!locked) attributes.addTransientAttributeModifiers(modifiers);
        }
    }
}
