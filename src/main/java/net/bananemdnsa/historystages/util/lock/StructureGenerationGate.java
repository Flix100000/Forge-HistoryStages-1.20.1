package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.StructureGenerationRule;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.data.saveddata.StructureGenerationCountData;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Decides which structures are barred from generating right now, and how many of the limited ones
 * have already been placed.
 *
 * <p>Only global stages count. World generation is shared by everyone and baked into the chunk the
 * moment it is created, so a per-player stage has no coherent answer here — an individual stage's
 * generation rules are ignored on purpose.
 *
 * <p>Consequence worth remembering: a chunk generated while the stage was locked stays empty
 * forever. Unlocking does not generate anything retroactively; the structure only appears in chunks
 * created afterwards.
 *
 * <p>Threading: the rule snapshot is volatile and replaced wholesale on rebuild, never mutated,
 * because worldgen worker threads read it per structure per chunk. The counters are atomic for the
 * same reason.
 */
public final class StructureGenerationGate {

    private static volatile GenerationRuleSet rules = GenerationRuleSet.EMPTY;
    private static final StructureGenerationCounters COUNTERS = new StructureGenerationCounters();

    /**
     * What the current thread reserved between HEAD and RETURN of one {@code tryGenerateStructure}
     * call. Worldgen is threaded, so this cannot be a plain field.
     */
    private static final ThreadLocal<List<String>> RESERVED = new ThreadLocal<>();

    private StructureGenerationGate() {}

    /**
     * Recomputes the rule snapshot from the stages that are currently locked. Call after anything
     * that changes stage definitions or unlock state.
     */
    public static void rebuild() {
        Map<String, List<StructureGenerationRule>> byStage = new HashMap<>();
        for (Map.Entry<String, StageEntry> e : StageManager.getStages().entrySet()) {
            byStage.put(e.getKey(), e.getValue().getStructureGenerationRules());
        }
        rules = GenerationRuleSet.build(byStage, Set.copyOf(StageData.SERVER_CACHE));
    }

    /** Cheap pre-check so the common "nothing configured" case costs one field read. */
    public static boolean isActive() {
        return rules.isActive();
    }

    /** True if the structure may not generate at all right now — hard block or spent budget. */
    public static boolean isBlocked(Holder<Structure> holder) {
        GenerationRuleSet snapshot = rules;
        if (!snapshot.isActive() || holder == null) return false;

        String id = idOf(holder);
        List<String> tags = tagsOf(holder);
        if (snapshot.isHardBlocked(id, tags)) return true;

        for (Map.Entry<String, Integer> limit : snapshot.countingLimits(id, tags).entrySet()) {
            if (COUNTERS.get(limit.getKey()) >= limit.getValue()) return true;
        }
        return false;
    }

    /**
     * Claims a slot for a placement that is about to be attempted. Vanilla calls
     * {@code tryGenerateStructure} far more often than it places anything, so the slot is only
     * booked once the attempt actually succeeded — see {@link #commitReservation()} and
     * {@link #releaseReservation()}.
     *
     * @return false if the structure may not generate; the caller cancels in that case
     */
    public static boolean tryReserve(Holder<Structure> holder) {
        // Anything left over on this thread can only come from an attempt that threw between HEAD
        // and RETURN, so the RETURN injection never settled it. Dropping it here matters: a later
        // attempt that reserves nothing would otherwise release those stale keys and hand back a
        // slot that belongs to a structure which really was placed.
        RESERVED.remove();

        GenerationRuleSet snapshot = rules;
        if (!snapshot.isActive() || holder == null) return true;

        String id = idOf(holder);
        List<String> tags = tagsOf(holder);
        if (snapshot.isHardBlocked(id, tags)) return false;

        Map<String, Integer> limits = snapshot.countingLimits(id, tags);
        if (limits.isEmpty()) return true;
        if (!COUNTERS.tryReserve(limits)) return false;

        RESERVED.set(new ArrayList<>(limits.keySet()));
        return true;
    }

    /** The attempt placed something: the reservation stands and has to be persisted. */
    public static void commitReservation() {
        List<String> reserved = RESERVED.get();
        RESERVED.remove();
        if (reserved != null && !reserved.isEmpty()) StructureGenerationCountData.markDirty();
    }

    /** The attempt found no valid spot: give the slot back. */
    public static void releaseReservation() {
        List<String> reserved = RESERVED.get();
        RESERVED.remove();
        if (reserved != null && !reserved.isEmpty()) COUNTERS.release(reserved);
    }

    /**
     * Clears the counters of rules that restart their budget with the phase.
     *
     * @param nowUnlocked true if the stage was just unlocked, false if it was just re-locked
     */
    public static void onStageLockChanged(String stageId, boolean nowUnlocked) {
        Set<String> keys = rules.resetKeysFor(stageId, nowUnlocked);
        if (!keys.isEmpty()) {
            COUNTERS.reset(keys);
            StructureGenerationCountData.markDirty();
        }
    }

    public static Map<String, Integer> snapshotCounts() {
        return COUNTERS.snapshot();
    }

    public static void restoreCounts(Map<String, Integer> values) {
        COUNTERS.restore(values);
    }

    private static String idOf(Holder<Structure> holder) {
        return holder.unwrapKey().map(k -> k.location().toString()).orElse(null);
    }

    private static List<String> tagsOf(Holder<Structure> holder) {
        List<String> tags = new ArrayList<>();
        holder.tags().forEach(t -> tags.add(t.location().toString()));
        return tags.isEmpty() ? Collections.emptyList() : tags;
    }
}
