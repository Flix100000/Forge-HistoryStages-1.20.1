package net.bananemdnsa.historystages.client;

import java.util.List;

import net.bananemdnsa.historystages.data.lock.TradeLockKind;

/**
 * Why the trade window the player is looking at came up empty.
 *
 * <p>The server knows this at the moment it opens the window; the client has to be told, because
 * a merchant with no offers looks exactly like a merchant whose offers were all held back. It is
 * kept here until the window closes, and read once a frame by whatever draws the notice.
 *
 * <p><strong>Tied to the window, not to the player.</strong> Every entry is stamped with the
 * container id the server opened, and asking with a different id gets nothing back. That is what
 * keeps a notice from surviving into the next merchant: opening one whose offers are all visible
 * sends no packet at all, so a notice cleared only by "some later event" would still be sitting
 * here — and the next villager would be accused of hiding trades it never had. Matching the id
 * makes that impossible rather than unlikely.
 *
 * <p>Free of any Minecraft type, so the id rule can be checked by a unit test. The caller reads
 * the open window's id and passes it in.
 */
public final class TradeLockNotice {

    private static int containerId = -1;
    private static List<String> stageNames = List.of();
    private static TradeLockKind kind = TradeLockKind.GLOBAL;

    private TradeLockNotice() {
    }

    /** Remembers why the window with this container id is empty. */
    public static void set(int containerId, List<String> stageNames, TradeLockKind kind) {
        TradeLockNotice.containerId = containerId;
        TradeLockNotice.stageNames = List.copyOf(stageNames);
        TradeLockNotice.kind = kind;
    }

    /**
     * Which lock emptied the window with this id.
     *
     * <p>The global lock for a window that is not ours — same rule as the names, and the same
     * reason: an answer that belongs to one window must not leak into the next one.
     */
    public static TradeLockKind kindFor(int openContainerId) {
        return appliesTo(openContainerId) ? kind : TradeLockKind.GLOBAL;
    }

    /** Whether the window with this id is empty because of stage locks. */
    public static boolean appliesTo(int openContainerId) {
        return containerId != -1 && containerId == openContainerId;
    }

    /**
     * The stages holding the offers back in this window, or an empty list.
     *
     * <p>Empty is a real answer, not a missing one: the notice is shown either way, and whether
     * the stages are named alongside it is the player's config rather than this class's business.
     */
    public static List<String> stageNamesFor(int openContainerId) {
        return appliesTo(openContainerId) ? stageNames : List.of();
    }

    /**
     * Forgets everything. For leaving a server — a stage's display name means something else on
     * the next one, and container ids start over.
     */
    public static void clear() {
        containerId = -1;
        stageNames = List.of();
        kind = TradeLockKind.GLOBAL;
    }
}
