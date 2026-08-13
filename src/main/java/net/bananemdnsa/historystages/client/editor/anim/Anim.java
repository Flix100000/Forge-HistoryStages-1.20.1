package net.bananemdnsa.historystages.client.editor.anim;

import net.minecraft.Util;

/**
 * A single animated float that advances in real time instead of per rendered frame.
 *
 * <p>Editor animations used to step by a fixed amount every frame ({@code progress += 0.1f}),
 * which tied every transition to the player's frame rate: barely visible at 240 FPS, sluggish
 * at 30. An {@code Anim} measures the wall-clock time since its own previous update, so a
 * transition takes the same number of milliseconds on every machine.
 *
 * <p>Each instance owns its timestamp and is expected to be updated exactly once per frame by
 * whoever draws it. A widget that goes unrendered for a while comes back with a large delta,
 * which {@link #STALE_MS} clamps to a step big enough to finish any normal transition — so it
 * appears settled rather than replaying a stale animation.
 *
 * <p>Values stay linear on purpose. Shape them at the draw site with {@link Ease}, so one
 * progress value can drive an eased fade and a linear offset at the same time.
 */
public final class Anim {

    /**
     * Largest delta a single update may apply. Longer gaps (screen switch, stutter, hover
     * returning to a row) are clamped to this, which still exceeds every duration in
     * {@link Timing} and therefore lands the value on its target in one step.
     */
    private static final long STALE_MS = 400L;

    private float value;
    /** Zero until the first update, so a fresh instance never applies a bogus first delta. */
    private long lastMs = 0L;

    public Anim() {
        this(0.0f);
    }

    public Anim(float initial) {
        this.value = initial;
    }

    /**
     * Advances towards {@code target} at a constant rate that covers the full 0..1 range in
     * {@code durationMs}. Suits hover and reveal states, where the transition has a defined
     * length.
     */
    public float ramp(float target, float durationMs) {
        return ramp(target, durationMs, durationMs);
    }

    /**
     * {@link #ramp} with separate speeds per direction. Interface animations generally read
     * better when they arrive a little faster than they leave: the response to the cursor
     * feels immediate, while the decay stays calm instead of snapping away.
     */
    public float ramp(float target, float inMs, float outMs) {
        float dt = delta();
        float duration = target > value ? inMs : outMs;
        if (duration <= 0.0f) {
            value = target;
            return value;
        }
        float step = dt / duration;
        if (value < target) {
            value = Math.min(target, value + step);
        } else if (value > target) {
            value = Math.max(target, value - step);
        }
        return value;
    }

    /**
     * Convenience for the common boolean case, e.g. {@code hover.ramp(isHovered, Timing.HOVER_IN_MS,
     * Timing.HOVER_OUT_MS)}.
     */
    public float ramp(boolean on, float inMs, float outMs) {
        return ramp(on ? 1.0f : 0.0f, inMs, outMs);
    }

    /**
     * Eases towards {@code target}, halving the remaining distance every {@code halfLifeMs}.
     * Suits following a target that keeps moving, such as a scroll offset, where a fixed
     * duration would fight the input.
     */
    public float approach(float target, float halfLifeMs) {
        float dt = delta();
        if (halfLifeMs <= 0.0f) {
            value = target;
            return value;
        }
        value += (target - value) * (1.0f - (float) Math.pow(0.5, dt / halfLifeMs));
        return value;
    }

    /**
     * Snaps {@code value} onto {@code target} once they are within {@code epsilon}. Callers
     * that round the value for drawing use this so a scroll offset settles exactly instead of
     * creeping by sub-pixel amounts forever.
     */
    public float settle(float target, float epsilon) {
        if (Math.abs(target - value) < epsilon) {
            value = target;
        }
        return value;
    }

    public float value() {
        return value;
    }

    /** Jumps to {@code v} without animating, e.g. when a list is rebuilt under the cursor. */
    public void set(float v) {
        this.value = v;
    }

    /** True once the value has arrived, useful for skipping work behind a finished fade. */
    public boolean isAt(float target) {
        return Math.abs(target - value) < 1.0e-4f;
    }

    /** Milliseconds since the previous update, clamped by {@link #STALE_MS}. */
    private float delta() {
        long now = Util.getMillis();
        if (lastMs == 0L) {
            lastMs = now;
            return 0.0f;
        }
        long dt = now - lastMs;
        lastMs = now;
        return Math.min(dt, STALE_MS);
    }
}
