package net.bananemdnsa.historystages.data.saveddata;

import net.bananemdnsa.historystages.util.lock.StructureGenerationGate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Persists how often each limited structure has already generated.
 *
 * <p>Lives on the overworld storage like {@link StageData}, which makes the counters world-global
 * rather than per dimension — "at most three" means three in the world.
 *
 * <p>The live counters sit in {@link StructureGenerationGate}; this class only loads them at world
 * load and writes them back on save. World generation threads must not touch data storage, so the
 * gate never calls back into here except to mark the instance dirty.
 */
public class StructureGenerationCountData extends SavedData {

    private static final String DATA_NAME = "historystages_structure_counts";

    /** The instance of the currently loaded world, so the gate can mark it dirty after a booking. */
    private static volatile StructureGenerationCountData INSTANCE;

    /** A fresh world has no counters yet — drop whatever a previous world left in the gate. */
    public StructureGenerationCountData() {
        StructureGenerationGate.restoreCounts(Map.of());
    }

    public static StructureGenerationCountData load(CompoundTag nbt, HolderLookup.Provider registries) {
        StructureGenerationCountData data = new StructureGenerationCountData();
        CompoundTag counts = nbt.getCompound("counts");
        Map<String, Integer> restored = new HashMap<>();
        for (String key : counts.getAllKeys()) restored.put(key, counts.getInt(key));
        StructureGenerationGate.restoreCounts(restored);
        INSTANCE = data;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        CompoundTag counts = new CompoundTag();
        for (Map.Entry<String, Integer> e : StructureGenerationGate.snapshotCounts().entrySet()) {
            counts.putInt(e.getKey(), e.getValue());
        }
        nbt.put("counts", counts);
        return nbt;
    }

    /**
     * Call once per world load; primes {@link #INSTANCE}.
     *
     * <p>Takes a {@link ServerLevel} rather than a {@link Level} on purpose: the constructor wipes
     * the live counters for a fresh world, so a client-side call would silently reset a running
     * server's budgets.
     */
    public static StructureGenerationCountData get(ServerLevel level) {
        StructureGenerationCountData data = level.getServer().overworld().getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<>(StructureGenerationCountData::new,
                                StructureGenerationCountData::load),
                        DATA_NAME
                );
        INSTANCE = data;
        return data;
    }

    /** Marks the loaded instance dirty after a booking. Safe to call from worldgen threads. */
    public static void markDirty() {
        StructureGenerationCountData instance = INSTANCE;
        if (instance != null) instance.setDirty();
    }
}
