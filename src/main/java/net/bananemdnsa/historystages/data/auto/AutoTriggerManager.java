package net.bananemdnsa.historystages.data.auto;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.StageUnlockHelper;
import net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import net.bananemdnsa.historystages.data.saveddata.AutoTriggerGlobalData;
import net.bananemdnsa.historystages.data.saveddata.AutoTriggerProgressData;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Central evaluator for AUTO-mode stage triggers. Server-side only.
 *
 * <p>Lifecycle: {@link #rebuildIndex()} after every stage-config reload.
 * Event hooks call {@link #process(String, Predicate, ServerPlayer)} with the
 * trigger type discriminator and a predicate that tests an individual trigger
 * against the event payload.</p>
 */
public final class AutoTriggerManager {

    private AutoTriggerManager() {}

    /** type discriminator → list of (stageId, triggers-of-this-type) entries. */
    private static final Map<String, List<IndexedTrigger>> INDEX = new ConcurrentHashMap<>();

    public record IndexedTrigger(String stageId, boolean isIndividual, TriggerCondition trigger) {}

    /** Rebuilds the dispatch index. Call after StageManager.load(). */
    public static void rebuildIndex() {
        INDEX.clear();
        addFrom(StageManager.getStages(), false);
        addFrom(StageManager.getIndividualStages(), true);
    }

    private static void addFrom(Map<String, StageEntry> stages, boolean isIndividual) {
        StageScope scope = isIndividual ? StageScope.INDIVIDUAL : StageScope.GLOBAL;
        for (var entry : stages.entrySet()) {
            StageEntry se = entry.getValue();
            if (!se.getMode().usesAutoTrigger()) continue; // AUTO or TEMPORARY
            AutoTrigger at = se.getAutoTrigger();
            if (at == null || at.isEmpty()) continue;
            for (TriggerCondition t : at.getTriggers()) {
                // A type that declared it does not apply here must not fire here either.
                // Without this the declaration would only hide rows in the editor, which reads
                // as a guarantee while being none.
                if (!TriggerTypes.scopesOf(t.type()).contains(scope)) continue;
                INDEX.computeIfAbsent(t.type(), k -> new ArrayList<>())
                        .add(new IndexedTrigger(entry.getKey(), isIndividual, t));
            }
        }
    }

    /**
     * Fast check whether any AUTO stage has at least one trigger of the given type.
     * Lets event listeners skip registry lookups / object allocations when nothing
     * could possibly match anyway.
     */
    public static boolean hasType(String triggerType) {
        List<IndexedTrigger> list = INDEX.get(triggerType);
        return list != null && !list.isEmpty();
    }

    /** Set of all auto-stage ids currently indexed; used for orphan pruning. */
    public static Set<String> indexedStageIds() {
        Set<String> ids = new HashSet<>();
        for (List<IndexedTrigger> list : INDEX.values()) {
            for (IndexedTrigger it : list) ids.add(it.stageId());
        }
        return ids;
    }

    /**
     * Process an event. {@code triggerType} is the discriminator of the trigger
     * fired from this event ({@code "biome"}, {@code "entity"}, ...). The
     * matcher returns true when the supplied trigger matches the event payload.
     */
    public static void process(String triggerType,
                               Predicate<TriggerCondition> matcher,
                               ServerPlayer player) {
        List<IndexedTrigger> candidates = INDEX.get(triggerType);
        if (candidates == null || candidates.isEmpty()) return;
        if (player == null || player.serverLevel() == null) return;

        ServerLevel level = player.serverLevel();
        for (IndexedTrigger it : candidates) {
            if (!matcher.test(it.trigger())) continue;
            handleMatch(it, player, level);
        }
    }

    private static void handleMatch(IndexedTrigger it, ServerPlayer player, ServerLevel level) {
        String stageId = it.stageId();
        StageEntry stage = it.isIndividual()
                ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);
        if (stage == null) return; // stale index entry; will be cleared on next rebuild

        if (isUnlocked(stageId, it.isIndividual(), player, level)) return;

        // TEMPORARY stages re-lock on their own. Skip while the stage is spent
        // (non-re-triggerable, already used) or in cooldown — no progress is
        // recorded so triggers fired during that window are simply discarded.
        boolean temporary = stage.getMode() == StageMode.TEMPORARY;
        if (temporary && !isTemporaryEligible(stageId, it.isIndividual(), player, level, stage.getTemporary())) return;

        // Dedupe by signature BEFORE running dependency checks — polled triggers
        // (biome/structure) fire every second and we don't want to re-evaluate
        // dependencies for a signature that's already been recorded.
        Set<Long> set = resolveProgressSet(stageId, it.isIndividual(), player, level);
        long sig = it.trigger().signature();
        if (set.contains(sig)) return;

        if (!areDependenciesSatisfied(stage, player, level)) return;

        if (!set.add(sig)) return;
        markDirty(it.isIndividual(), level);

        AutoTrigger cfg = stage.getAutoTrigger();
        if (cfg == null) return;
        if (isModeSatisfied(cfg, set)) {
            unlockStage(stageId, it.isIndividual(), player, level);
            clearProgress(stageId, it.isIndividual(), player, level);
            if (temporary) {
                startTemporaryTimer(stageId, it.isIndividual(), player, level, stage.getTemporary());
            }
        }
    }

    private static boolean isTemporaryEligible(String stageId, boolean isIndividual,
                                               ServerPlayer player, ServerLevel level,
                                               net.bananemdnsa.historystages.data.temporary.TemporaryConfig cfg) {
        int maxTriggers = cfg != null ? cfg.getMaxTriggers() : 1;
        var data = net.bananemdnsa.historystages.data.saveddata.TemporaryStageData.get(level);
        return isIndividual
                ? data.isIndividualEligible(player.getUUID(), stageId, maxTriggers)
                : data.isGlobalEligible(stageId, maxTriggers);
    }

    private static void startTemporaryTimer(String stageId, boolean isIndividual, ServerPlayer player,
                                            ServerLevel level,
                                            net.bananemdnsa.historystages.data.temporary.TemporaryConfig cfg) {
        if (cfg == null) {
            cfg = new net.bananemdnsa.historystages.data.temporary.TemporaryConfig();
        }
        var data = net.bananemdnsa.historystages.data.saveddata.TemporaryStageData.get(level);
        if (isIndividual) {
            data.startIndividualTimer(player.getUUID(), stageId, cfg.durationTicks());
        } else {
            data.startGlobalTimer(stageId, cfg.durationTicks());
        }
    }

    private static boolean isModeSatisfied(AutoTrigger cfg, Set<Long> satisfied) {
        if (cfg.resolvedMode() == CombineMode.ALL) {
            for (TriggerCondition t : cfg.getTriggers()) {
                if (!satisfied.contains(t.signature())) return false;
            }
            return true;
        }
        return !satisfied.isEmpty(); // ANY mode
    }

    // ---- glue to existing data classes -------------------------------------

    private static boolean isUnlocked(String stageId, boolean isIndividual,
                                      ServerPlayer player, ServerLevel level) {
        if (isIndividual) {
            return IndividualStageData.get(level).hasStage(player.getUUID(), stageId);
        }
        return StageData.SERVER_CACHE.contains(stageId);
    }

    private static boolean areDependenciesSatisfied(StageEntry stage,
                                                    ServerPlayer player, ServerLevel level) {
        if (!stage.hasDependencies()) return true;
        DependencyResult result = DependencyChecker.checkAll(stage, player, level, null);
        return result.isFulfilled();
    }

    private static Set<Long> resolveProgressSet(String stageId, boolean isIndividual,
                                                ServerPlayer player, ServerLevel level) {
        if (isIndividual) {
            return AutoTriggerProgressData.get(level).getOrCreate(player.getUUID(), stageId);
        }
        return AutoTriggerGlobalData.get(level).getOrCreate(stageId);
    }

    private static void markDirty(boolean isIndividual, ServerLevel level) {
        if (isIndividual) AutoTriggerProgressData.get(level).touch();
        else AutoTriggerGlobalData.get(level).touch();
    }

    private static void unlockStage(String stageId, boolean isIndividual,
                                    ServerPlayer player, ServerLevel level) {
        if (isIndividual) {
            StageUnlockHelper.unlockIndividual(stageId, player);
        } else {
            StageUnlockHelper.unlockGlobal(stageId, level);
        }
    }

    public static void clearProgress(String stageId, boolean isIndividual,
                                     ServerPlayer player, ServerLevel level) {
        if (isIndividual) {
            AutoTriggerProgressData.get(level).clear(player.getUUID(), stageId);
        } else {
            AutoTriggerGlobalData.get(level).clear(stageId);
        }
    }

    /** Convenience overload for the global path that doesn't require a player. */
    public static void clearProgress(String stageId, ServerLevel level) {
        AutoTriggerGlobalData.get(level).clear(stageId);
    }

    public static void pruneOrphans(ServerLevel level) {
        Set<String> keep = indexedStageIds();
        AutoTriggerProgressData.get(level).pruneOrphans(keep);
        AutoTriggerGlobalData.get(level).pruneOrphans(keep);
    }
}
