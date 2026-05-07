package net.astr0.historystages.api;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.BitSet;
import java.util.Map;
import java.util.Set;

/**
 * ItemLockCategory is a specialised lock category for handling {@link Item} AND {@link ItemStack}.
 * The {@link ItemKey} class has compatibility with both, and you can create keys using {@link ItemKey#of(Item)}
 * and {@link ItemKey#of(ItemStack)} respectively. It should be noted that creating a key using the base {@link Item}
 * class will lock the underlying item, whilst applying a lock with the {@link ItemStack} variant will only lock ItemStacks
 * with the same exact NBT.
 */
public class ItemLockCategory extends LockCategory<ItemKey> {

    private final Set<Item> hasNbtLocks = new ReferenceOpenHashSet<>();

    public ItemLockCategory(String id, Map<ItemKey, BitSet> map) {
        super(id, map);
    }

    @Override
    public void applyLock(ItemKey key, int bitIndex) {
        if (key.hasNbt()) {
            hasNbtLocks.add(key.item());
        }
        super.applyLock(key, bitIndex);
    }

    public boolean isLocked(ItemStack stack, BitSet activeMask, BitSet globalUnlockedStages) {
        if (stack.isEmpty()) return false;
        Item baseItem = stack.getItem();

        // ALWAYS check the base item lock first.
        // This is incredibly fast and ensures base restrictions are never bypassed.
        BitSet baseLock = super.getLock(new ItemKey(baseItem, null));
        if (checkMask(baseLock, activeMask, globalUnlockedStages)) {
            return true;
        }

        // NBT FAST-PATH: Only allocate the NBT key and hash it IF this item type has NBT locks.
        if (stack.hasTag() && hasNbtLocks.contains(baseItem)) {
            BitSet nbtLock = super.getLock(ItemKey.of(stack));
            return checkMask(nbtLock, activeMask, globalUnlockedStages);
        }

        return false;
    }

    private boolean checkMask(BitSet required, BitSet activeMask, BitSet globalUnlockedStages) {
        if (required == null || required.isEmpty()) return false;

        for (int i = required.nextSetBit(0); i >= 0; i = required.nextSetBit(i + 1)) {
            if (!globalUnlockedStages.get(i) && !activeMask.get(i)) {
                return true;
            }
        }
        return false;
    }
}
