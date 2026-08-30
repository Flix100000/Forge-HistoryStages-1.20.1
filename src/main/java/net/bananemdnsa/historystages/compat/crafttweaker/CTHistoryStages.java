package net.bananemdnsa.historystages.compat.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import net.bananemdnsa.historystages.compat.script.ScriptStageApi;
import net.bananemdnsa.historystages.compat.script.ScriptStageListeners;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.openzen.zencode.java.ZenCodeType;

import java.util.List;
import java.util.function.Consumer;

/**
 * {@code mods.historystages.HistoryStages} in ZenScript. Same capabilities as the KubeJS
 * bindings, same facade underneath, so a fix lands in both languages at once.
 *
 * <p>Loaded only when CraftTweaker is installed: nothing in HistoryStages references this class,
 * and {@code @ZenRegister} is found by CraftTweaker's own scanner.
 */
@ZenRegister
@ZenCodeType.Name("mods.historystages.HistoryStages")
public final class CTHistoryStages {

    private CTHistoryStages() {}

    // --- reading -----------------------------------------------------------

    @ZenCodeType.Method
    public static List<String> stages() {
        return ScriptStageApi.globalStageIds();
    }

    @ZenCodeType.Method
    public static List<String> individualStages() {
        return ScriptStageApi.individualStageIds();
    }

    @ZenCodeType.Method
    public static List<String> categories() {
        return ScriptStageApi.categoryIds();
    }

    @ZenCodeType.Method
    public static boolean isUnlocked(String stageId) {
        return ScriptStageApi.isUnlocked(stageId);
    }

    @ZenCodeType.Method
    public static boolean isUnlockedFor(Player player, String stageId) {
        return ScriptStageApi.isUnlockedFor(server(player), stageId);
    }

    @ZenCodeType.Method
    public static List<String> unlockedStages() {
        return ScriptStageApi.unlockedStages();
    }

    @ZenCodeType.Method
    public static List<String> unlockedStagesFor(Player player) {
        return ScriptStageApi.unlockedStagesFor(server(player));
    }

    // --- writing -----------------------------------------------------------

    @ZenCodeType.Method
    public static boolean unlock(String stageId) {
        return ScriptStageApi.unlock(stageId);
    }

    @ZenCodeType.Method
    public static boolean lock(String stageId) {
        return ScriptStageApi.lock(stageId);
    }

    @ZenCodeType.Method
    public static boolean unlockFor(Player player, String stageId) {
        return ScriptStageApi.unlockFor(server(player), stageId);
    }

    @ZenCodeType.Method
    public static boolean lockFor(Player player, String stageId) {
        return ScriptStageApi.lockFor(server(player), stageId);
    }

    // --- lock questions ----------------------------------------------------

    @ZenCodeType.Method
    public static boolean isLocked(String categoryId, String subject,
                                   @ZenCodeType.Optional Player player) {
        return ScriptStageApi.isLocked(categoryId, subject, server(player));
    }

    @ZenCodeType.Method
    public static List<String> missingStages(String categoryId, String subject,
                                             @ZenCodeType.Optional Player player) {
        return ScriptStageApi.missingStages(categoryId, subject, server(player));
    }

    // --- reacting ----------------------------------------------------------

    @ZenCodeType.Method
    public static void onStageUnlocked(Consumer<CTStageEvent> listener) {
        ScriptStageListeners.onUnlocked((stage, name) -> listener.accept(new CTStageEvent(stage, name)));
    }

    @ZenCodeType.Method
    public static void onStageLocked(Consumer<CTStageEvent> listener) {
        ScriptStageListeners.onLocked((stage, name) -> listener.accept(new CTStageEvent(stage, name)));
    }

    @ZenCodeType.Method
    public static void onIndividualStageUnlocked(Consumer<CTIndividualStageEvent> listener) {
        ScriptStageListeners.onIndividualUnlocked((stage, name, uuid) ->
                listener.accept(new CTIndividualStageEvent(stage, name, CTScriptReloadHook.playerOf(uuid))));
    }

    @ZenCodeType.Method
    public static void onIndividualStageLocked(Consumer<CTIndividualStageEvent> listener) {
        ScriptStageListeners.onIndividualLocked((stage, name, uuid) ->
                listener.accept(new CTIndividualStageEvent(stage, name, CTScriptReloadHook.playerOf(uuid))));
    }

    /**
     * ZenScript hands over the vanilla {@link Player}, which on a dedicated server is always a
     * {@link ServerPlayer} — but a script running client-side would not be, and the facade needs
     * the server one. Null rather than a cast failure, which the facade already treats as "no".
     */
    private static ServerPlayer server(Player player) {
        return player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
    }
}
