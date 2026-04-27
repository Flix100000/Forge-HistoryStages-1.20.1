package net.bananemdnsa.historystages.data;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import net.astr0.historystages.api.IStageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.astr0.historystages.api.StageScope;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public final class RuntimeStageManager implements IStageManager {


    /*
     * Notes on state synchronization:
     * - Any time the editor is used to make changes, the new config must be sent to the server
     * - The server then redistributes the config to all other clients.
     * - The clients and server all deterministically bake the locks
     */

    private static RuntimeStageManager INSTANCE;

    private RuntimeStageManager() {}

    public static RuntimeStageManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new RuntimeStageManager();
        }

        return INSTANCE;
    }


    // =================================
    //       SERIALISED VARIABLES
    // =================================
    // These are saved to disk as part of the games save state
    // They are loaded from disk at runtime
    private final HashMap<String, BitSet> PLAYER_UNLOCKED_STAGES = new HashMap<>();
    private final BitSet GLOBAL_UNLOCKED_STAGES = new BitSet();

    // =================================
    //        EPHEMERAL VARIABLES
    // =================================
    // These are rebuilt from scratch at run time using runtime determinism
    // All variables listed here should be rebuilt (or at least checked) every
    // time bake() is called.

    private final Object2IntOpenHashMap<String> stageToBitPositionReferenceMap = new Object2IntOpenHashMap<>();
    private final Int2ObjectOpenHashMap<String> bitPositionToStageReferenceMap = new Int2ObjectOpenHashMap<>();
    // THIS SHOULD NEVER BE MODIFIED. WE USE THIS FOR EMPTY BITSETS ONLY
    // To save memory, we will assign/return this anytime we know there is no lock.
    // If this instance is modified, we will start getting some really strange bugs
    private final BitSet EMPTY_BITSET = new ReadOnlyBitSet();
    private boolean STAGE_WITH_STRUCTURE_EXISTS = false;

    // TODO(Astr0): To optimise, consider an approach to initialise lock hashmaps at
    // a reasonably size. We want to minimise the amount of resize and rehash operations at runtime
    // Either use a reasonably base size or simply assume double whatever the last known value was
    // could also place this behind a develop config flag. When dev mode is off, we run in a low
    // mem profile which limits this dict size to the expected size at initialisation
    // BAKE TIME: Use Reference map for O(1) pointer-equality lookups
    private final Reference2ObjectMap<Item, BitSet> itemLocks = new Reference2ObjectOpenHashMap<>(300);
    //TODO: Should we try automatically lock blocks associated with items? Do we maybe already do this?
    private final Reference2ObjectMap<Block, BitSet> blockLocks = new Reference2ObjectOpenHashMap<>(300);
    private final Object2ObjectOpenHashMap<ResourceLocation, BitSet> modLocks = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectOpenHashMap<ResourceLocation, BitSet> dimensionLocks = new Object2ObjectOpenHashMap<>(20);
    private final Object2ObjectOpenHashMap<ResourceLocation, BitSet> entityLocks = new Object2ObjectOpenHashMap<>(20);
    private final Object2ObjectOpenHashMap<ResourceLocation, BitSet> structureLocks = new Object2ObjectOpenHashMap<>(20);

    // =========================
    //     Implementation
    // =========================

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

        // TODO: Explain why I decided to include this. Short answer: performance
        stages.add(0, new StageDefinition("DummyStage", StageScope.GLOBAL));

        // Load our quick lookup tables for bit position <--> stage
        // We use these to achieve O(1) forward and reverse lookups of bit positions corresponding
        // to each stage, and vice versa.
        for(int i = 1; i < stages.size(); i++) {

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

            // We bake mod locks. This avoids us having to do a string comparison based check on every
            // single item/dimension/... lock check. Instead, we directly add all the mods items to the locked
            // list. This does have a memory overhead, but its incredibly small (even for large mods), and I think
            // the runtime performance benefits are worth it
            stage.getLockedMods().forEach((mod) -> {
                for (Map.Entry<ResourceKey<Item>, Item> entry : ForgeRegistries.ITEMS.getEntries()) {
                    if (entry.getKey().location().getNamespace().equals(mod.getNamespace())) {
                        BitSet itemLock = itemLocks.computeIfAbsent(entry.getValue(), k -> new BitSet());
                        itemLock.set(STAGE_INDEX);
                    }
                }

                for (Map.Entry<ResourceKey<Block>, Block> entry : ForgeRegistries.BLOCKS.getEntries()) {
                    if (entry.getKey().location().getNamespace().equals(mod.getNamespace())) {
                        BitSet blockLock = blockLocks.computeIfAbsent(entry.getValue(), k -> new BitSet());
                        blockLock.set(STAGE_INDEX);
                    }
                }
            });

            // Reset this cached result
            STAGE_WITH_STRUCTURE_EXISTS = false;
            stage.getLockedStructures().forEach((structure) -> {

                // Cache that we have ANY locked structure
                // This is returned by anyStageHasStructures()
                STAGE_WITH_STRUCTURE_EXISTS = true;
            });
        }

        bakeTagEntries(stages);

    }

    private void lockItemWithStage(Item item, String stage) {
        int stageBit = getStageBit(stage);
        BitSet itemLock = itemLocks.computeIfAbsent(item, k -> new BitSet());
        itemLock.set(stageBit);
    }

    // Tags aren't loaded until the game world loads and can also be reloaded mid-game.
    // So we have to process tags data separately, so they can be handled correctly
    public void bakeTagEntries(List<StageDefinition> stages) {

        for (StageDefinition stage : stages) {
            List<TagKey<Item>> lockedTags = stage.getLockedItemTags();

            for (TagKey<Item> tag : lockedTags) {
                ForgeRegistries.ITEMS.tags().getTag(tag).forEach(item -> {
                    lockItemWithStage(item, stage.getName());
                });
            }
        }
    }


    public boolean anyStageHasStructures() {
        return STAGE_WITH_STRUCTURE_EXISTS;
    }

    public List<String> getStagesForItem(Item item) {

        List<String> lockingStages = new ArrayList<>();

        BitSet lockReference = itemLocks.getOrDefault(item, EMPTY_BITSET);

        for (int i = lockReference.nextSetBit(0); i >= 0; i = lockReference.nextSetBit(i + 1)) {
            //TODO: It should never happen but it may be possible that we try to test a bit position which doesn't correspond
            // to a stage. Check this code later and consider if this is possible.
            lockingStages.add(bitPositionToStageReferenceMap.get(i));
        }

        return lockingStages;
    }



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

    // Get the bit position for the given stage
    // This function is private to prevent other classes attempting to directly manipulate
    // the bits. The order may change at any time so this is not a stable external API.
    // Will return 0 if the stage is not present in the global state -> This will always correspond
    // to the "DummyState", which we can safely modify in any way without impacting game state.
    private int getStageBit(String stage) {

        //Todo: Pick which one of these error behaviours to use
        ASSERT_VALID_STAGE(stage);
        FAIL_GRACEFULLY(stage);
        return stageToBitPositionReferenceMap.getOrDefault(stage, 0);
    }

    private String getUUIDAsString(Player player) {
        return player.getUUID().toString();
    }

    private void ASSERT_VALID_STAGE(String stage) {
        if (!stageToBitPositionReferenceMap.containsKey(stage)) {
            throw new IllegalArgumentException("Invalid stage: " + stage);
        }
    }

    private void FAIL_GRACEFULLY(String stage) {
        LogUtils.getLogger().warn("[HistoryStages] Stage is not defined: {}", stage);
    }

    private BitSet getBitSetForPlayer(Player player) {
        // Intrinsically safe operation. If a player does not exist in our stage
        // tracking we can just generate an empty bitset for them and add to list
        // There is never a situation where we wouldn't want to track a player
        return PLAYER_UNLOCKED_STAGES.computeIfAbsent(getUUIDAsString(player), k -> new BitSet());
    }

    @Override
    public boolean isStageUnlockedForPlayer(ServerPlayer player, String stage) {
        return getBitSetForPlayer(player).get(getStageBit(stage));
    }

    @Override
    public boolean isStageUnlockedGlobally(String stage) {
        return GLOBAL_UNLOCKED_STAGES.get(getStageBit(stage));
    }

    @Override
    public void unlockStageForPlayer(ServerPlayer player, String stage) {
        int bitPosition = getStageBit(stage);
        getBitSetForPlayer(player).set(bitPosition);
    }

    @Override
    public void unlockStageGlobally(String stage) {
        GLOBAL_UNLOCKED_STAGES.set(getStageBit(stage));
    }

    @Override
    public void lockStageForPlayer(ServerPlayer player, String stage) {
        int bitPosition = getStageBit(stage);

        getBitSetForPlayer(player).clear(bitPosition);
    }

    @Override
    public void lockStageGlobally(String stage) {
        GLOBAL_UNLOCKED_STAGES.clear(getStageBit(stage));
    }
}
