package net.astr0.historystages.api;

import com.mojang.logging.LogUtils;

public class HistoryStagesAPI {

    private static IStageManager _stageManager;

    /**
     * Retrieve a reference to the active StageManager.
     * @return The active {@link IStageManager} object
     */
    public static IStageManager getStageManager() {
        return _stageManager;
    }

    /**
     * Set the StageManager instance for API usage. For internal use only!
     * @param stageManager An initialised instance of a stage manager object
     */
    public static void _setStageManager(IStageManager stageManager) {
        _stageManager = stageManager; //TODO(Astr0): Move this function behind a package-private barrier
                                      //             so that other mods can't over-write our manager
    }

    // Internal method. For use in testing bridge between main mod and API
    @Deprecated
    public static void _APIHelloWorld() {
        LogUtils.getLogger().info("[HistoryStages API] Hello World! from the History Stages API");
    }
}
