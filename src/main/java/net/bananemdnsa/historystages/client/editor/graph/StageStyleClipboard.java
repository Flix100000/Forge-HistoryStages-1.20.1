package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.data.graph.GraphStageData;

/**
 * One copied node-style override, client-side, surviving the graph screen being closed.
 *
 * <p>Static because copy and paste happen in two different openings of the context menu, and
 * possibly on two different screens. It lives until the game exits; nothing about a style is
 * worth persisting to disk.
 */
public final class StageStyleClipboard {

    private static GraphStageData.Entry copied;

    private StageStyleClipboard() {}

    public static void copy(GraphStageData.Entry entry) {
        copied = entry == null ? null : entry.copyStyles();
    }

    /** Null when nothing has been copied, or when what was copied turned out to be empty. */
    public static GraphStageData.Entry get() {
        if (copied == null || copied.isEmpty()) return null;
        return copied.copyStyles();
    }

    public static boolean isEmpty() {
        return get() == null;
    }
}
