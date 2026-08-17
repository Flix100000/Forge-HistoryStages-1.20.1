package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Decides which structures are barred from generating right now.
 *
 * <p>Only global stages count. World generation is shared by everyone and baked into the chunk
 * the moment it is created, so a per-player stage has no coherent answer here — an individual
 * stage's {@code block_generation} entries are ignored on purpose.
 *
 * <p>Consequence worth remembering: a chunk generated while the stage was locked stays empty
 * forever. Unlocking does not generate anything retroactively; the structure only appears in
 * chunks created afterwards.
 *
 * <p>The blocked set is cached because worldgen asks per structure per chunk, and it is read
 * from worldgen worker threads — hence the volatile snapshot, replaced wholesale on rebuild
 * rather than mutated in place.
 */
public final class StructureGenerationGate {

    /** Structure IDs and {@code #tags} barred from generating. Replaced, never mutated. */
    private static volatile Set<String> blocked = Collections.emptySet();

    private StructureGenerationGate() {}

    /**
     * Recomputes the blocked set from the stages that are currently locked. Call after anything
     * that changes stage definitions or unlock state.
     */
    public static void rebuild() {
        Set<String> next = new HashSet<>();
        for (Map.Entry<String, StageEntry> e : StageManager.getStages().entrySet()) {
            if (StageData.SERVER_CACHE.contains(e.getKey())) continue;
            next.addAll(e.getValue().getStructureBlockGeneration());
        }
        blocked = next.isEmpty() ? Collections.emptySet() : Set.copyOf(next);
    }

    /** Cheap pre-check so the common "nothing configured" case costs one field read. */
    public static boolean isActive() {
        return !blocked.isEmpty();
    }

    public static boolean isBlocked(Holder<Structure> holder) {
        Set<String> snapshot = blocked;
        if (snapshot.isEmpty() || holder == null) return false;

        String id = holder.unwrapKey().map(k -> k.location().toString()).orElse(null);
        if (id != null && snapshot.contains(id)) return true;

        return holder.tags().anyMatch(t -> snapshot.contains("#" + t.location()));
    }
}
