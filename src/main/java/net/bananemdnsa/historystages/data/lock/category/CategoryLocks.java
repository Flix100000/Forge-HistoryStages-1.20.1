package net.bananemdnsa.historystages.data.lock.category;

import java.util.List;
import java.util.UUID;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import org.jetbrains.annotations.Nullable;

/**
 * What an addon calls to ask whether one of its own objects is gated.
 *
 * <p>The addon wires its own game hook — HistoryStages has no idea when a villager trade is about
 * to be offered — and asks here. Both scopes are consulted: a subject is locked when a global
 * stage gating it is not yet unlocked for the world, or an individual stage gating it is not yet
 * unlocked for this player.
 *
 * <p>Deliberately thin. Every decision worth getting wrong lives in {@link CategoryLockResolver},
 * which is free of Minecraft and therefore properly tested; this class only supplies the stage
 * maps and the right viewer. If a branch ever wants to grow here, it belongs there instead.
 *
 * <p>This is destined to become part of the public API surface. It sits in an internal package
 * because that surface is settled in a later phase, once there is something to be stable about.
 */
public final class CategoryLocks {

    private CategoryLocks() {}

    /** Server side: is this subject gated for this player, in either scope? */
    public static boolean isLockedForPlayer(String categoryId, Object subject, UUID playerUuid) {
        LockCategory<?> category = LockCategories.byId(categoryId);
        if (category == null) return false;

        // A category that means nothing per player is not asked about individual stages; the
        // answer would be meaningless rather than merely empty.
        return (CategoryLockResolver.supports(category, StageScope.GLOBAL)
                        && CategoryLockResolver.isLocked(category, subject,
                                StageManager.getStages(), StageLocks.serverGlobal()))
                || (CategoryLockResolver.supports(category, StageScope.INDIVIDUAL)
                        && CategoryLockResolver.isLocked(category, subject,
                                StageManager.getIndividualStages(), StageLocks.serverIndividual(playerUuid)));
    }

    /**
     * Server side: the stages this player still needs before the subject becomes available,
     * global ones first. Empty when the subject is not gated at all.
     */
    public static List<String> missingStagesForPlayer(String categoryId, Object subject, UUID playerUuid) {
        LockCategory<?> category = LockCategories.byId(categoryId);
        if (category == null) return List.of();

        return CategoryLockResolver.join(
                CategoryLockResolver.supports(category, StageScope.GLOBAL)
                        ? CategoryLockResolver.missingStages(category, subject,
                                StageManager.getStages(), StageLocks.serverGlobal())
                        : List.of(),
                CategoryLockResolver.supports(category, StageScope.INDIVIDUAL)
                        ? CategoryLockResolver.missingStages(category, subject,
                                StageManager.getIndividualStages(), StageLocks.serverIndividual(playerUuid))
                        : List.of());
    }

    /** Null when nothing is registered under this id — lets a mod check that it registered in time. */
    @Nullable
    public static LockCategory<?> category(String categoryId) {
        return LockCategories.byId(categoryId);
    }
}
