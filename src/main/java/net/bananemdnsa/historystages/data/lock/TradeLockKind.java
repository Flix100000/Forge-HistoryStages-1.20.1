package net.bananemdnsa.historystages.data.lock;

/**
 * Which kind of stage emptied a trade window, so the notice in it can show the matching lock.
 *
 * <p>The client cannot work this out for itself. What it is told are display names, and for an
 * individual stage there is no way back from a name to the stage it belongs to. The server knows
 * and says so.
 *
 * <p>Free of Minecraft, so the values that go over the wire can be pinned by a unit test.
 */
public enum TradeLockKind {
    GLOBAL(0),
    INDIVIDUAL(1),
    DUAL(2);

    private final int code;

    TradeLockKind(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    /**
     * Reads a wire value.
     *
     * <p>An unknown code falls back to the global lock rather than throwing: a client on a
     * different version should show the wrong icon, not lose the window it was drawing.
     */
    public static TradeLockKind fromCode(int code) {
        for (TradeLockKind kind : values()) {
            if (kind.code == code) return kind;
        }
        return GLOBAL;
    }

    /**
     * Which lock to show for a set of stages that may hold both kinds.
     *
     * <p>Neither means global. A notice is only ever sent because something held the offers back,
     * so "nothing did" cannot happen; naming the global lock is the answer that is at worst
     * incomplete rather than invented.
     */
    public static TradeLockKind of(boolean anyGlobal, boolean anyIndividual) {
        if (anyGlobal && anyIndividual) return DUAL;
        return anyIndividual ? INDIVIDUAL : GLOBAL;
    }
}
