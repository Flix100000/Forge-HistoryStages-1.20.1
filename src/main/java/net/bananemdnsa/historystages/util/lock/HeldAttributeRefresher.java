package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.Config;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
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
        boolean lockingEnabled = Config.GAMEPLAY.lockItemUsage.get() || Config.GAMEPLAY.individualLockItemUsage.get();
        AttributeMap attributes = player.getAttributes();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            boolean locked = lockingEnabled && LockGate.isActionLocked(stack, player, "use",
                    Config.GAMEPLAY.lockItemUsage, Config.GAMEPLAY.individualLockItemUsage);

            stack.forEachModifier(slot, (holder, modifier) -> {
                AttributeInstance instance = attributes.getInstance(holder);
                if (instance == null) return;
                instance.removeModifier(modifier.id());
                if (!locked) instance.addTransientModifier(modifier);
            });
        }
    }
}
