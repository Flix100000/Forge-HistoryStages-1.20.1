package net.astr0.historystages.api;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * ItemLockCategory is a specialised lock category for handling {@link Item} AND {@link ItemStack}.
 * The {@link ItemKey} class has compatibility with both, and you can create keys using {@link ItemKey#of(Item)}
 * and {@link ItemKey#of(ItemStack)} respectively. It should be noted that creating a key using the base {@link Item}
 * class will lock the underlying item, whilst applying a lock with the {@link ItemStack} variant will only lock ItemStacks
 * with the same exact NBT.
 */
public class ItemLockCategory extends LockCategory<Item> {

    private final HashMap<Item, List<NBTLock>> itemNbtLocks = new HashMap<>();
    public record NBTLock(StageDefinition stage, JsonObject lockCriteria) {}
    private int NBT_META_POSITION;

    public ItemLockCategory(String id, Map<Item, BitSet> map) {
        super(id, map);
    }

    /**
     * The specialized overload for ItemStacks.
     * This fully encapsulates the Sentinel Bit and NBT resolution logic.
     */
    public boolean isLocked(ItemStack stack, Player player) {
        if (stack.isEmpty()) return false;
        
        BitSet lock = getLock(stack.getItem());
        if (lock == null || lock.isEmpty()) return false;

        if (manager.hasMissingStages(lock, player)) {
            return true;
        }

        if (lock.get(NBT_META_POSITION)) {
            List<NBTLock> nbtLocks = itemNbtLocks.get(stack.getItem());
            
            if (nbtLocks != null) {
                for (NBTLock nbtLock : nbtLocks) {
                    if (NbtMatcher.matches(stack, nbtLock.lockCriteria())) {
                        
                        int stageBitPosition = manager.getStageBit(nbtLock.stage());
                        
                        // Check if this specific NBT stage is still locked
                        if (manager.isBitPositionLocked(stageBitPosition, player)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Get the stages for a given item stack. This method considers both the underlying item as well as any Nbt locks that apply to that item.
     * To simply check any item lock without considering Nbt data, it is better to use {@link RuntimeStageManager#getStagesFor(LockCategory, Object)}
     * @param itemLockCategory
     * @param stack
     * @return A list of stages which lock this item stack
     */
    public List<StageDefinition> getStagesFor(ItemStack stack) {

        BitSet lock = getLock(stack.getItem());

        if (lock == null) return EMPTY_LIST;
        List<StageDefinition> stages =  manager.getStageDefinitionsFromLock(lock);

        if(lock.get(NBT_META_POSITION)) {
            for(NBTLock nbtLock : itemNbtLocks.get(stack.getItem())) {

                // If there is an NBT lock on this item, and it matches the tested item stack
                // then we should list this stage as locking it
                if (NbtMatcher.matches(stack, nbtLock.lockCriteria())) {
                    stages.add(nbtLock.stage());
                }
            }
        }

        return stages;
    }

    public void addNBTLock(StageDefinition stage, Item item, JsonObject nbtCriteria) {
        List<NBTLock> locks = itemNbtLocks.computeIfAbsent(item, (key) -> new ArrayList<>());
        locks.add(new NBTLock(stage, nbtCriteria));
    }

    @Override
    public void register(IStageManager manager) {
        super.register(manager);

        // We need to track addition info for this lock
        // Register a bit which can be set if an item also has NBT data related to its locking
        NBT_META_POSITION = manager.registerMetadataBit("ITEM_NBT_METADATA");
    }

    @Override
    public void clear() {
        super.clear();
        itemNbtLocks.clear();
    }
}
