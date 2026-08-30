package net.bananemdnsa.historystages.compat.kubejs;

import net.bananemdnsa.historystages.compat.script.ScriptStageApi;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * What a script sees as {@code HistoryStages.*} in {@code server_scripts}. Every method forwards
 * to {@link ScriptStageApi}; none decides anything, so a fix there reaches ZenScript at the same
 * time.
 *
 * <p>The client half lives in {@link HistoryStagesClientBindings} rather than behind a flag here.
 * A method that does not exist cannot be called by accident: a client script reaching for
 * {@code unlock} gets "not a function" instead of a silent no-op.
 */
public final class HistoryStagesBindings {

    private HistoryStagesBindings() {}

    // --- reading -----------------------------------------------------------

    public static List<String> stages() {
        return ScriptStageApi.globalStageIds();
    }

    public static List<String> individualStages() {
        return ScriptStageApi.individualStageIds();
    }

    public static List<String> categories() {
        return ScriptStageApi.categoryIds();
    }

    public static boolean isUnlocked(String stageId) {
        return ScriptStageApi.isUnlocked(stageId);
    }

    public static boolean isUnlockedFor(ServerPlayer player, String stageId) {
        return ScriptStageApi.isUnlockedFor(player, stageId);
    }

    public static boolean hasStage(ServerPlayer player, String stageId) {
        return ScriptStageApi.hasStageEitherScope(player, stageId);
    }

    public static List<String> unlockedStages() {
        return ScriptStageApi.unlockedStages();
    }

    public static List<String> unlockedStagesFor(ServerPlayer player) {
        return ScriptStageApi.unlockedStagesFor(player);
    }

    // --- writing -----------------------------------------------------------

    public static boolean unlock(String stageId) {
        return ScriptStageApi.unlock(stageId);
    }

    public static boolean lock(String stageId) {
        return ScriptStageApi.lock(stageId);
    }

    public static boolean unlockFor(ServerPlayer player, String stageId) {
        return ScriptStageApi.unlockFor(player, stageId);
    }

    public static boolean lockFor(ServerPlayer player, String stageId) {
        return ScriptStageApi.lockFor(player, stageId);
    }

    // --- lock questions ----------------------------------------------------

    public static boolean isLocked(String categoryId, String subject, ServerPlayer player) {
        return ScriptStageApi.isLocked(categoryId, subject, player);
    }

    /** Global-only categories such as {@code recipes} have no per-player answer to give. */
    public static boolean isLocked(String categoryId, String subject) {
        return ScriptStageApi.isLocked(categoryId, subject, null);
    }

    public static List<String> missingStages(String categoryId, String subject, ServerPlayer player) {
        return ScriptStageApi.missingStages(categoryId, subject, player);
    }

    public static List<String> missingStages(String categoryId, String subject) {
        return ScriptStageApi.missingStages(categoryId, subject, null);
    }
}
