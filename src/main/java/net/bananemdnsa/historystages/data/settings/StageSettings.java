package net.bananemdnsa.historystages.data.settings;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.engine.StageScope;

/**
 * The entry point for an addon reading its own settings at runtime.
 *
 * <p>Thin facade over {@link StageSettingsGroups#valuesOf(String, StageEntry, StageScope)}: picks
 * {@link StageManager#getStages()} or {@link StageManager#getIndividualStages()} by {@code scope},
 * looks {@code stageId} up, and delegates. A stage that does not exist passes {@code null}
 * through, which yields every field's own default — asking is always safe.
 *
 * <p>This class touches {@link StageManager} and therefore Minecraft, so unlike {@link
 * StageSettingsGroups} it is not unit-tested. That is exactly why it must stay this trivial: all
 * the logic worth testing lives in {@link StageSettingsGroups#valuesOf}, which takes the stage as
 * an argument instead of reaching into {@code StageManager} itself.
 */
public final class StageSettings {

    private StageSettings() {}

    public static SettingsValues valuesOf(String groupId, String stageId, StageScope scope) {
        StageEntry stage = (scope == StageScope.INDIVIDUAL
                ? StageManager.getIndividualStages()
                : StageManager.getStages()).get(stageId);
        return StageSettingsGroups.valuesOf(groupId, stage, scope);
    }
}
