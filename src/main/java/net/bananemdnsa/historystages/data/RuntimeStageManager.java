package net.bananemdnsa.historystages.data;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.astr0.historystages.api.StageScope;

import java.util.BitSet;
import java.util.Comparator;
import java.util.List;

public final class RuntimeStageManager {


    /*
     * Notes on state synchronization:
     * - Any time the editor is used to make changes, the new config must be sent to the server
     * - The server then redistributes the config to all other clients.
     * - The clients and server all deterministically bake the locks
     */
    private BitSet GLOBAL_UNLOCKED_STAGES = new BitSet();

    private static RuntimeStageManager INSTANCE;

    private RuntimeStageManager() {}

    public static RuntimeStageManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new RuntimeStageManager();
        }

        return INSTANCE;
    }

    Object2IntOpenHashMap<String> stageToBitPositionReferenceMap = new Object2IntOpenHashMap<>();
    Int2ObjectOpenHashMap<String> bitPositionToStageReferenceMap = new Int2ObjectOpenHashMap<>();

    //NOTE: This function must be deterministic for any given input.
    // The clients must be able to produce the exact same results based on
    // an arbitrarily ordered list of stage definitions
    public void bake(List<StageDefinition> stages) {

        // We sort in order to achieve deterministic mappings between bit position and stage
        // If the same stages are loaded on both the client and the server, they are guaranteed
        // to have the same order (Alphabetical)
        stages.sort(
            Comparator
                .comparing((StageDefinition s) -> s.getStageScope() == StageScope.INDIVIDUAL) // false (Global) first
                .thenComparing(StageDefinition::getName, String.CASE_INSENSITIVE_ORDER)
        );

        // Load our quick lookup tables for bit position <--> stage
        // We use these to achieve O(1) forward and reverse lookups of bit positions corresponding
        // to each stage, and vice versa.
        for(int i = 0; i < stages.size(); i++) {

            final int STAGE_INDEX = i; // Needed for compiler reasons

            StageDefinition stage = stages.get(STAGE_INDEX);
            String stageName  = stage.getName();

            stageToBitPositionReferenceMap.put(stageName, STAGE_INDEX);
            bitPositionToStageReferenceMap.put(STAGE_INDEX, stageName);

            //TODO(Astr0): Check if this can be optimised using concurrency for large numbers of stage definitions
            stage.getLockedItems().forEach((item) -> {
                BitSet itemLock = itemLocks.computeIfAbsent(item, k -> new BitSet());
                itemLock.set(STAGE_INDEX);
            });

            stage.getLockedMods().forEach((mod) -> {
                BitSet modLock = modLocks.computeIfAbsent(mod, k -> new BitSet());
                modLock.set(STAGE_INDEX);
            });
        }

    }

    private BitSet getOrCreateBitSet(Item item) {
        return itemLocks.computeIfAbsent(item, k -> new BitSet());
    }

    // TODO(Astr0): To optimise, consider an approach to initialise lock hashmaps at
    // a reasonably size. We want to minimise the amount of resize and rehash operations at runtime
    // Either use a reasonably base size or simply assume double whatever the last known value was
    // could also place this behind a develop config flag. When dev mode is off, we run in a low
    // mem profile which limits this dict size to the expected size at initialisation
    // BAKE TIME: Use Reference map for O(1) pointer-equality lookups
    private final Reference2ObjectMap<Item, BitSet> itemLocks = new Reference2ObjectOpenHashMap<>();

    public boolean isItemLocked(Item item, BitSet activeMask) {
        // 1. Get the BitSet via pointer comparison (very fast)
        BitSet required = itemLocks.get(item);
        // 2. Null check (Items with no stages aren't in the map)
        if (required == null) return false;

        // Direct traversal of the required bits
        // Most items have 1 bit. nextSetBit(0) returns that bit,
        // nextSetBit is implemented using pure bitwise operations, so its fast
        //TODO(Astr0): Check if we can implement a long based class to do bitwise operations
        //on the stack, rather than traversing java heap allocated BitSet
        for (int i = required.nextSetBit(0); i >= 0; i = required.nextSetBit(i + 1)) {
            // If the bit is NOT in global AND NOT in the player mask, the item is locked.
            if (!GLOBAL_UNLOCKED_STAGES.get(i) && !activeMask.get(i)) {
                return true;
            }
        }

        return false;
    }

    private final Object2ObjectOpenHashMap<ResourceLocation, BitSet> modLocks = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<ResourceLocation, BitSet> dimensionLocks = new Object2ObjectOpenHashMap<>();

}
