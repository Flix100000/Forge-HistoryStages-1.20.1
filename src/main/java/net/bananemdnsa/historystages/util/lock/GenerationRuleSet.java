package net.bananemdnsa.historystages.util.lock;

import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import net.bananemdnsa.historystages.data.lock.StructureGenerationRule;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable snapshot of what the currently locked stages allow to generate. Deliberately free of
 * Minecraft types so it can be unit-tested; {@link StructureGenerationGate} resolves holders into
 * the plain id and tag strings this class works with.
 *
 * <p>Exhaustion is <em>not</em> part of the snapshot. A snapshot is rebuilt when stages change,
 * while the counters move during world generation, so "limit reached" has to be read live from the
 * counters at the point of use.
 */
public final class GenerationRuleSet {

    public static final GenerationRuleSet EMPTY =
            new GenerationRuleSet(Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap());

    /** Rule ids ({@code a:b} or {@code #a:b}) that may not generate at all right now. */
    private final Set<String> hardBlocked;
    /** Rule id -> counter key -> limit. */
    private final Map<String, Map<String, Integer>> counting;
    /** Stage id -> phase that restarts -> counter keys to clear. */
    private final Map<String, Map<Boolean, Set<String>>> resets;

    private GenerationRuleSet(Set<String> hardBlocked,
                              Map<String, Map<String, Integer>> counting,
                              Map<String, Map<Boolean, Set<String>>> resets) {
        this.hardBlocked = hardBlocked;
        this.counting = counting;
        this.resets = resets;
    }

    public static String key(String stageId, String ruleId) {
        return stageId + "|" + ruleId;
    }

    /**
     * @param rulesByStage rules of every known global stage, keyed by stage id
     * @param unlocked     ids of the stages that are currently unlocked
     */
    public static GenerationRuleSet build(Map<String, List<StructureGenerationRule>> rulesByStage,
                                          Set<String> unlocked) {
        Set<String> hard = new HashSet<>();
        Map<String, Map<String, Integer>> counting = new HashMap<>();
        Map<String, Map<Boolean, Set<String>>> resets = new HashMap<>();

        for (Map.Entry<String, List<StructureGenerationRule>> stage : rulesByStage.entrySet()) {
            boolean stageUnlocked = unlocked.contains(stage.getKey());
            for (StructureGenerationRule rule : stage.getValue()) {
                if (rule == null || rule.id() == null || rule.id().isEmpty()) continue;

                boolean counts = (rule.phase() == GenerationPhase.WHILE_LOCKED) != stageUnlocked;
                if (counts) {
                    if (rule.max() == 0) {
                        hard.add(rule.id());
                    } else {
                        counting.computeIfAbsent(rule.id(), k -> new HashMap<>())
                                .put(key(stage.getKey(), rule.id()), rule.max());
                    }
                } else if (rule.phase() == GenerationPhase.AFTER_UNLOCK) {
                    // Locked stage, limit applies only afterwards: nothing generates yet.
                    hard.add(rule.id());
                }

                if (rule.resetOnRelock()) {
                    // The counter clears when its own counting phase restarts.
                    boolean restartsOnUnlock = rule.phase() == GenerationPhase.AFTER_UNLOCK;
                    resets.computeIfAbsent(stage.getKey(), k -> new HashMap<>())
                            .computeIfAbsent(restartsOnUnlock, k -> new HashSet<>())
                            .add(key(stage.getKey(), rule.id()));
                }
            }
        }

        if (hard.isEmpty() && counting.isEmpty() && resets.isEmpty()) return EMPTY;

        // Deep-freeze: Map.copyOf only hardens the outer map, and worldgen worker threads share
        // this snapshot, so the nested collections must not stay mutable either.
        Map<String, Map<String, Integer>> frozenCounting = new HashMap<>();
        counting.forEach((id, limits) -> frozenCounting.put(id, Map.copyOf(limits)));
        Map<String, Map<Boolean, Set<String>>> frozenResets = new HashMap<>();
        resets.forEach((stageId, byPhase) -> {
            Map<Boolean, Set<String>> frozen = new HashMap<>();
            byPhase.forEach((phase, keys) -> frozen.put(phase, Set.copyOf(keys)));
            frozenResets.put(stageId, Map.copyOf(frozen));
        });

        return new GenerationRuleSet(Set.copyOf(hard), Map.copyOf(frozenCounting), Map.copyOf(frozenResets));
    }

    /** Cheap pre-check so the common "nothing configured" case costs one field read. */
    public boolean isActive() {
        return !hardBlocked.isEmpty() || !counting.isEmpty();
    }

    public boolean isHardBlocked(String structureId, List<String> tagIds) {
        if (hardBlocked.isEmpty()) return false;
        if (structureId != null && hardBlocked.contains(structureId)) return true;
        for (String tag : tagIds) {
            if (hardBlocked.contains("#" + tag)) return true;
        }
        return false;
    }

    /** Counter key -> limit for every counting rule that covers this structure. */
    public Map<String, Integer> countingLimits(String structureId, List<String> tagIds) {
        if (counting.isEmpty()) return Collections.emptyMap();
        Map<String, Integer> out = new HashMap<>();
        if (structureId != null) {
            Map<String, Integer> direct = counting.get(structureId);
            if (direct != null) out.putAll(direct);
        }
        for (String tag : tagIds) {
            Map<String, Integer> viaTag = counting.get("#" + tag);
            if (viaTag != null) out.putAll(viaTag);
        }
        return out;
    }

    /**
     * Counter keys to clear when a stage changes lock state.
     *
     * @param nowUnlocked true if the stage was just unlocked, false if it was just re-locked
     */
    public Set<String> resetKeysFor(String stageId, boolean nowUnlocked) {
        Map<Boolean, Set<String>> forStage = resets.get(stageId);
        if (forStage == null) return Collections.emptySet();
        return forStage.getOrDefault(nowUnlocked, Collections.emptySet());
    }
}
