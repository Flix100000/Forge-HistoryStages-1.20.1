package net.bananemdnsa.historystages.data;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import net.astr0.historystages.api.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

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
    private final HashMap<UUID, BitSet> PLAYER_UNLOCKED_STAGES = new HashMap<>();
    private final BitSet GLOBAL_UNLOCKED_STAGES = new BitSet();

    // =================================
    //        EPHEMERAL VARIABLES
    // =================================
    // These are rebuilt from scratch at run time using runtime determinism
    // All variables listed here should be rebuilt (or at least checked) every
    // time bake() is called.

    private final Object2IntOpenHashMap<String> stageToBitPositionReferenceMap = new Object2IntOpenHashMap<>();
    private final Int2ObjectOpenHashMap<StageDefinition> bitPositionToStageReferenceMap = new Int2ObjectOpenHashMap<>();
    // THIS SHOULD NEVER BE MODIFIED. WE USE THIS FOR EMPTY BITSETS ONLY
    // To save memory, we will assign/return this anytime we know there is no lock.
    // If this instance is modified, we will start getting some really strange bugs
    private final BitSet EMPTY_BITSET = new ReadOnlyBitSet();
    private boolean STAGE_WITH_STRUCTURE_EXISTS = false;
    // Init to -1 so that we crash the game when using bad logic
    // This will force us to respect the intended use of this value
    private int LAST_GLOBAL_INDEX = -1;


    public List<StageDefinition> getStagesForBlock(Block block) {
        BitSet lock = HistoryStagesAPI.BLOCKS.getLock(block);
        return getStageDefinitionsFromLock(lock);
    }

    @NotNull
    private List<StageDefinition> getStageDefinitionsFromLock(BitSet lock) {
        List<StageDefinition> lockingStages = new ArrayList<>();

        if(lock == null) return lockingStages;

        for (int i = lock.nextSetBit(0); i >= 0; i = lock.nextSetBit(i + 1)) {
            //TODO: It should never happen but it may be possible that we try to test a bit position which doesn't correspond
            // to a stage. Check this code later and consider if this is possible.
            lockingStages.add(bitPositionToStageReferenceMap.get(i));
        }

        return lockingStages;
    }

    // Small util class to assign sequential numbers
    // Just makes it easier to make sure no numbers are ever doubled up
    class Iota {
        static int iota = -1;

        public static int next() {return ++iota;}
        public static int limit() {return iota + 1;}
    }

    // This explanation is very hard to understand. I will try again sometime soon.
    /**
     * For certain functions to be possible using the BitSet based locking system, we sometimes
     * need to be able to track additional special information. To do this, we reserve some bits
     * at the start of every BitSet to store our own metadata/enable specific behaviours. For example,
     * the DUMMY_META_POSITION points to a reserved position in every BitSet and is provided for crash prevention.
     * By reserving the "dummy" bit, we ensure there is always a bit which can be toggled without impacting behaviour.
     * <p>
     * For example, consider the case where our locking system somehow ends up in a bad state and all our stage definitions get corrupted.
     * If we then attempted to lock or unlock the bit for a given stage, our system would no longer be able to work out which bit position
     * corresponds to that stage. To resolve this, we know we can always simply toggle the dummy bit without accidentally effecting anything,
     * because the dummy bit does nothing.
     */
    private static final int DUMMY_META_POSITION = Iota.next();
    // If an item or block has at least one NBT lock associated with it, this bit should be set
    // This means an automatic lock check will fail until the NBT bit is explicitly cleared for that item.
    private static final int NBT_META_POSITION = Iota.next();


    /**
     * Integer representing the index at which standard stage definitions start
     * in the sequence of lock bits. This is the first position after the last "metadata" bit.
     * For more information about the metadata bits, refer to {@link #DUMMY_META_POSITION}
     */
    private static final int START_NONMETA_POSITION = Iota.limit();
    // =========================
    //     Implementation
    // =========================

    //NOTE: This function must be deterministic for any given input.
    // The clients must be able to produce the exact same results based on
    // an arbitrarily ordered list of stage definitions
    // THIS ONLY NEEDS TO BE CALLED WHEN A NEW STAGE IS ADDED
    // IF EXISTING STAGES ARE UPDATED, THE LOCKS CAN BE APPLIED DIRECTLY
    public void bake(List<StageDefinition> stages) {


        LAST_GLOBAL_INDEX = -1; // Always reset in case all the global stages have been removed since last bake

        // Clear out all our previous lock setups as they may now be invalid
        for (LockCategory<?> category : HistoryStagesAPI.getAllCategories()) {
            category.clear();
        }

        // We sort in order to achieve deterministic mappings between bit position and stage
        // If the same stages are loaded on both the client and the server, they are guaranteed
        // to have the same order (Alphabetical)
        stages.sort(
            Comparator
                .comparing((StageDefinition s) -> s.getScope() == StageScope.INDIVIDUAL) // false (Global) first
                .thenComparing(StageDefinition::getName, String.CASE_INSENSITIVE_ORDER)
        );

        // TODO: Explain why I decided to include this. Short answer: performance
        stages.add(DUMMY_META_POSITION, new StageDefinition("DUMMY_STAGE", StageScope.GLOBAL));
        stages.add(NBT_META_POSITION, new StageDefinition("NBT_LOCKED", StageScope.GLOBAL));

        // Load our quick lookup tables for bit position <--> stage
        // We use these to achieve O(1) forward and reverse lookups of bit positions corresponding
        // to each stage, and vice versa.
        for(int i = START_NONMETA_POSITION; i < stages.size(); i++) {

            final int STAGE_INDEX = i; // Needed for compiler reasons

            StageDefinition stage = stages.get(STAGE_INDEX);
            String stageName  = stage.getName();

            if (stage.getScope() == StageScope.GLOBAL) {
                // At the end of the bake this will contain the index of the last global stage
                // The globals are always sorted to be at the start of the stage order (as seen above)
                LAST_GLOBAL_INDEX = i;
            }

            stageToBitPositionReferenceMap.put(stageName, STAGE_INDEX);
            bitPositionToStageReferenceMap.put(STAGE_INDEX, stage);

            //TODO(Astr0): Check if this can be optimised using concurrency for large numbers of stage definitions
            stage.getLockedItems().forEach((item) -> {
                HistoryStagesAPI.ITEMS.applyLock(item, STAGE_INDEX);
            });

            // We bake mod locks. This avoids us having to do a string comparison based check on every
            // single item/dimension/... lock check. Instead, we directly add all the mods items to the locked
            // list. This does have a memory overhead, but its incredibly small (even for large mods), and I think
            // the runtime performance benefits are worth it
            stage.getLockedMods().forEach((mod) -> {
                for (Map.Entry<ResourceKey<Item>, Item> entry : ForgeRegistries.ITEMS.getEntries()) {
                    if (entry.getKey().location().getNamespace().equals(mod.getNamespace())) {
                        HistoryStagesAPI.ITEMS.applyLock(entry.getValue(), STAGE_INDEX);
                    }
                }

                for (Map.Entry<ResourceKey<Block>, Block> entry : ForgeRegistries.BLOCKS.getEntries()) {
                    if (entry.getKey().location().getNamespace().equals(mod.getNamespace())) {
                        HistoryStagesAPI.BLOCKS.applyLock(entry.getValue(), STAGE_INDEX);
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

    public boolean isLockDualPhase(BitSet lock) {
        boolean hasGlobalLocks = false;
        boolean hasIndividualLocks = false;

        // Loop won't run at all if there are no locks applied
        for (int i = lock.nextSetBit(0); i >= 0; i = lock.nextSetBit(i + 1)) {
            if (i > LAST_GLOBAL_INDEX) {
                hasIndividualLocks = true;
            } else {
                hasGlobalLocks = true;
            }
        }

        return hasGlobalLocks && hasIndividualLocks;
    }

    public boolean lockIsLockedByGlobals(BitSet lock) {
        for (int i = lock.nextSetBit(0); i >= 0; i = lock.nextSetBit(i + 1)) {
            if (i <= LAST_GLOBAL_INDEX && !GLOBAL_UNLOCKED_STAGES.get(i)) {
                return true;
            }
        }

        return false;
    }

    private void lockItemWithStage(Item item, String stage) {
        int stageBit = getStageBit(stage);
        HistoryStagesAPI.ITEMS.applyLock(item, stageBit);
    }

    private void lockBlockWithStage(Block block, String stage) {
        int stageBit = getStageBit(stage);
        HistoryStagesAPI.BLOCKS.applyLock(block, stageBit);
    }

    // Tags aren't loaded until the game world loads and can also be reloaded mid-game.
    // So we have to process tags data separately, so they can be handled correctly
    public void bakeTagEntries(List<StageDefinition> stages) {

        for (StageDefinition stage : stages) {
            List<TagKey<Item>> lockedItemTags = stage.getLockedItemTags();
            List<TagKey<Block>> lockedBlockTags = stage.getLockedBlockTags();

            for (TagKey<Item> itemTag : lockedItemTags) {
                ForgeRegistries.ITEMS.tags().getTag(itemTag).forEach(item -> {
                    lockItemWithStage(item, stage.getName());
                });
            }

            for (TagKey<Block> blockTag : lockedBlockTags) {
                ForgeRegistries.BLOCKS.tags().getTag(blockTag).forEach(block -> {
                    lockBlockWithStage(block, stage.getName());
                });
            }
        }
    }

    public boolean anyStageHasStructures() {
        return STAGE_WITH_STRUCTURE_EXISTS;
    }

    /**
     * Checks if a specific individual bit position is locked.
     */
    public boolean isBitPositionLocked(int bitPosition, Player player) {
         return !GLOBAL_UNLOCKED_STAGES.get(bitPosition) && !getBitSetForPlayer(player).get(bitPosition);
    }

    // Get the bit position for the given stage
    // This function is private to prevent other classes attempting to directly manipulate
    // the bits. The order may change at any time so this is not a stable external API.
    // Will return 0 if the stage is not present in the global state -> This will always correspond
    // to the "DummyState", which we can safely modify in any way without impacting game state.
    private int getStageBit(String stage) {
        FAIL_GRACEFULLY(stage);
        return stageToBitPositionReferenceMap.getOrDefault(stage, DUMMY_META_POSITION);
    }

    private int getStageBit(StageDefinition stage) {
        return getStageBit(stage.getName());
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
        if (!stageToBitPositionReferenceMap.containsKey(stage)) {
            LogUtils.getLogger().warn("[HistoryStages] Stage is not defined: {}", stage);
        }
    }

    // This should probably be made private, instead just high level check functions
    // Good enough for now
    public BitSet getBitSetForPlayer(Player player) {
        return getBitSetForPlayerUUID(player.getUUID());
    }

    public BitSet getBitSetForPlayerUUID(UUID playerID) {
        // Intrinsically safe operation. If a player does not exist in our stage
        // tracking we can just generate an empty bitset for them and add to list
        // There is never a situation where we wouldn't want to track a player
        return PLAYER_UNLOCKED_STAGES.computeIfAbsent(playerID, k -> new BitSet());
    }

    @Override
    public boolean isStageUnlockedForPlayer(Player player, String stage) {
        return getBitSetForPlayer(player).get(getStageBit(stage)) || isStageUnlockedGlobally(stage);
    }

    @Override
    public boolean isStageUnlockedGlobally(String stage) {
        return GLOBAL_UNLOCKED_STAGES.get(getStageBit(stage));
    }

    @Override
    public void unlockStageForPlayer(Player player, String stage) {
        int bitPosition = getStageBit(stage);
        getBitSetForPlayer(player).set(bitPosition);
    }

    @Override
    public void unlockStageGlobally(String stage) {
        GLOBAL_UNLOCKED_STAGES.set(getStageBit(stage));
    }

    @Override
    public void lockStageForPlayer(Player player, String stage) {
        int bitPosition = getStageBit(stage);

        getBitSetForPlayer(player).clear(bitPosition);
    }

    @Override
    public void lockStageGlobally(String stage) {
        GLOBAL_UNLOCKED_STAGES.clear(getStageBit(stage));
    }
}
