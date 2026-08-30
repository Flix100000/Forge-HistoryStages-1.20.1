package net.bananemdnsa.historystages.compat.script;

import net.bananemdnsa.historystages.api.lock.CategoryLocks;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStates;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;

/**
 * The one place a script — KubeJS or CraftTweaker — reaches the mod. Both bridges translate their
 * own argument types into these calls and translate the return values back; neither decides
 * anything.
 *
 * <p>That matters most for the setters. {@link StageStates#unlockGlobal} writes the SavedData,
 * broadcasts the sync packet, drops the structure and biome caches, reloads recipes, fires the
 * event and sends sound, chat and toast. A bridge that reproduced any of that by hand would be
 * the third copy of a mistake this codebase has already made twice — see the class javadoc on
 * {@link StageStates}.
 *
 * <p>Nothing here throws. A script that misspells a stage id on a running server should get a
 * {@code false} it can check and one line in the log, not an exception in the middle of an
 * unlock.
 */
public final class ScriptStageApi {

    private ScriptStageApi() {}

    /**
     * The game log, not {@code DebugLogger}. DebugLogger collects into the stage-load report,
     * which is written once at load and is not where a pack author looks when their script is
     * misbehaving — they look at the console, next to the KubeJS and CraftTweaker errors.
     */
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Stands in for "no player" when asking a global-only category. Individual stages are never
     * recorded against it, so the individual half of the question answers empty instead of wrong.
     */
    private static final UUID NO_PLAYER = new UUID(0L, 0L);

    // --- reading -----------------------------------------------------------

    public static List<String> globalStageIds() {
        return new ArrayList<>(StageManager.getStages().keySet());
    }

    public static List<String> individualStageIds() {
        return new ArrayList<>(StageManager.getIndividualStages().keySet());
    }

    public static List<String> categoryIds() {
        return LockCategories.ids();
    }

    public static boolean isUnlocked(String stageId) {
        if (!checkStage(stageId, StageScope.GLOBAL)) return false;
        ServerLevel level = overworld();
        if (level == null) return false;
        return StageData.get(level).hasStage(stageId);
    }

    public static boolean isUnlockedFor(ServerPlayer player, String stageId) {
        if (player == null) return false;
        if (!checkStage(stageId, StageScope.INDIVIDUAL)) return false;
        return IndividualStageData.get(player.serverLevel()).hasStage(player.getUUID(), stageId);
    }

    /**
     * Does this player have this stage, in either scope, without the scope warning: this is the
     * one call where naming a global stage is correct rather than a mistake. It backs
     * {@code player.hasStage(...)}, which pack authors coming from GameStages reach for first and
     * which has no notion of two scopes.
     */
    public static boolean hasStageEitherScope(ServerPlayer player, String stageId) {
        if (player == null) return false;
        boolean known = StageManager.getStages().containsKey(stageId)
                || StageManager.getIndividualStages().containsKey(stageId);
        if (!known) {
            report(ScriptCallResolution.stage(stageId, StageScope.GLOBAL,
                    StageManager.getStages().keySet(),
                    StageManager.getIndividualStages().keySet()), "stage:any:" + stageId);
            return false;
        }
        ServerLevel level = player.serverLevel();
        return IndividualStageData.get(level).hasStage(player.getUUID(), stageId)
                || StageData.get(level).hasStage(stageId);
    }

    public static List<String> unlockedStages() {
        ServerLevel level = overworld();
        if (level == null) return List.of();
        return new ArrayList<>(StageData.get(level).getUnlockedStages());
    }

    public static List<String> unlockedStagesFor(ServerPlayer player) {
        if (player == null) return List.of();
        Set<String> stages = IndividualStageData.get(player.serverLevel())
                .getUnlockedStages(player.getUUID());
        return stages == null ? List.of() : new ArrayList<>(stages);
    }

    // --- writing -----------------------------------------------------------

    public static boolean unlock(String stageId) {
        if (!checkStage(stageId, StageScope.GLOBAL)) return false;
        ServerLevel level = overworld();
        if (level == null) return false;
        return StageStates.unlockGlobal(stageId, level);
    }

    public static boolean lock(String stageId) {
        if (!checkStage(stageId, StageScope.GLOBAL)) return false;
        ServerLevel level = overworld();
        if (level == null) return false;
        return StageStates.relockGlobal(stageId, level);
    }

    public static boolean unlockFor(ServerPlayer player, String stageId) {
        if (player == null) return false;
        if (!checkStage(stageId, StageScope.INDIVIDUAL)) return false;
        return StageStates.unlockIndividual(stageId, player);
    }

    public static boolean lockFor(ServerPlayer player, String stageId) {
        if (player == null) return false;
        if (!checkStage(stageId, StageScope.INDIVIDUAL)) return false;
        return StageStates.relockIndividual(stageId, player);
    }

    // --- lock questions ----------------------------------------------------

    /**
     * A null player asks the global half only, which is what a global-only category such as
     * {@code recipes} wants — there, a player argument would suggest an answer that varies per
     * player when it cannot.
     */
    public static boolean isLocked(String categoryId, String subject, ServerPlayer player) {
        String category = resolvedCategory(categoryId);
        if (category == null) return false;
        return CategoryLocks.isLockedForPlayer(category, subject, uuidOf(player));
    }

    public static List<String> missingStages(String categoryId, String subject, ServerPlayer player) {
        String category = resolvedCategory(categoryId);
        if (category == null) return List.of();
        return CategoryLocks.missingStagesForPlayer(category, subject, uuidOf(player));
    }

    // --- shared checking ---------------------------------------------------

    private static UUID uuidOf(ServerPlayer player) {
        return player == null ? NO_PLAYER : player.getUUID();
    }

    private static boolean checkStage(String stageId, StageScope scope) {
        ScriptCallResolution.Check check = ScriptCallResolution.stage(
                stageId, scope,
                StageManager.getStages().keySet(),
                StageManager.getIndividualStages().keySet());
        return report(check, "stage:" + scope + ":" + stageId);
    }

    /**
     * The registry id for what a script wrote, or null when it names nothing. Scripts may leave
     * off this mod's namespace for the built-in categories; see
     * {@link ScriptCallResolution#canonicalCategoryId}.
     */
    private static String resolvedCategory(String categoryId) {
        List<String> known = LockCategories.ids();
        if (!report(ScriptCallResolution.category(categoryId, known), "category:" + categoryId)) {
            return null;
        }
        return ScriptCallResolution.canonicalCategoryId(categoryId, known);
    }

    /** Logs at most once per distinct mistake; see {@link ScriptCallResolution#shouldWarn}. */
    private static boolean report(ScriptCallResolution.Check check, String warnKey) {
        if (check.ok()) return true;
        if (ScriptCallResolution.shouldWarn(warnKey)) {
            LOGGER.warn(check.message());
        }
        return false;
    }

    private static ServerLevel overworld() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            if (ScriptCallResolution.shouldWarn("no-server")) {
                LOGGER.warn("HistoryStages: a script asked about stages while no server was running");
            }
            return null;
        }
        return server.overworld();
    }
}
