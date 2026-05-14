package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.data.RuntimeStageManager;

/**
 * Provides groups of lock handlers with an easy reference to the RuntimeStageManager instance.
 * Purely a programmer convenience, no special functionality
 */
public class AbstractHandlerGroup {

    protected static final RuntimeStageManager stageManager = RuntimeStageManager.getInstance();
}
