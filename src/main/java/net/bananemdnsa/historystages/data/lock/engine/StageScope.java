package net.bananemdnsa.historystages.data.lock.engine;

/**
 * Which of the two stage maps a lock question is asked against.
 *
 * <p>Global stages are unlocked once for the whole world; individual stages are unlocked
 * per player. Almost every subject can be gated by both at the same time, which is why
 * callers usually ask the engine twice — once per scope — and combine the answers through
 * {@link LockResolution}.
 */
public enum StageScope {
    GLOBAL,
    INDIVIDUAL
}
