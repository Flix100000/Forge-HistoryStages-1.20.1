package net.bananemdnsa.historystages.data.temporary;

import com.google.gson.annotations.SerializedName;

/**
 * Configuration for a {@code mode=temporary} stage: how long it stays unlocked
 * after a trigger fires, how many times it may be unlocked in total, and the
 * cooldown that must pass before it can be triggered again.
 *
 * <p>JSON shape (nested under the stage's {@code "temporary"} key):</p>
 * <pre>{@code
 * "temporary": {
 *   "duration": 3,
 *   "duration_unit": "days",       // "seconds" | "minutes" | "hours" | "days"
 *   "max_triggers": 5,             // 1 = once, N = N times, 0 = unlimited
 *   "cooldown": 0,
 *   "cooldown_unit": "hours"
 * }
 * }</pre>
 *
 * <p>{@code max_triggers} controls re-triggering: {@code 1} means the stage can
 * be unlocked exactly once and then stays locked forever (only an admin
 * {@code /history global unlock} can re-open it, and that manual unlock starts
 * no new timer). A value of {@code N > 1} allows {@code N} unlocks total, and
 * {@code 0} allows unlimited unlocks. Whenever more than one unlock is possible,
 * the {@code cooldown} must elapse after a re-lock before the next trigger is
 * accepted (a cooldown of 0 means immediate eligibility).</p>
 *
 * <p>The legacy boolean {@code re_triggerable} is still read for backward
 * compatibility: {@code false} maps to {@code max_triggers = 1}, {@code true} to
 * {@code 0} (unlimited). {@code max_triggers} takes precedence when both are
 * present.</p>
 */
public class TemporaryConfig {

    @SerializedName("duration")
    private int duration = 1;

    @SerializedName("duration_unit")
    private String durationUnit = DurationUnit.HOURS.serialize();

    @SerializedName("max_triggers")
    private Integer maxTriggers; // null → derive from legacy re_triggerable / default 1

    @SerializedName("re_triggerable")
    private Boolean reTriggerable; // legacy; only consulted when max_triggers absent

    @SerializedName("cooldown")
    private int cooldown = 0;

    @SerializedName("cooldown_unit")
    private String cooldownUnit = DurationUnit.HOURS.serialize();

    public TemporaryConfig() {}

    public TemporaryConfig(int duration, DurationUnit durationUnit, int maxTriggers,
                           int cooldown, DurationUnit cooldownUnit) {
        this.duration = duration;
        this.durationUnit = (durationUnit != null ? durationUnit : DurationUnit.HOURS).serialize();
        this.maxTriggers = Math.max(0, maxTriggers);
        this.cooldown = cooldown;
        this.cooldownUnit = (cooldownUnit != null ? cooldownUnit : DurationUnit.HOURS).serialize();
    }

    public int getDuration() { return duration; }

    public void setDuration(int duration) { this.duration = Math.max(0, duration); }

    public DurationUnit getDurationUnit() { return DurationUnit.parse(durationUnit); }

    public String getRawDurationUnit() { return durationUnit; }

    public void setDurationUnit(DurationUnit unit) {
        this.durationUnit = (unit != null ? unit : DurationUnit.HOURS).serialize();
    }

    /**
     * Maximum number of times the stage may be unlocked. {@code 1} = once,
     * {@code N > 1} = N times, {@code 0} = unlimited. Resolves the legacy
     * {@code re_triggerable} flag when {@code max_triggers} is absent.
     */
    public int getMaxTriggers() {
        if (maxTriggers != null) return Math.max(0, maxTriggers);
        if (reTriggerable != null) return reTriggerable ? 0 : 1;
        return 1;
    }

    public void setMaxTriggers(int max) {
        this.maxTriggers = Math.max(0, max);
        this.reTriggerable = null; // explicit value supersedes the legacy flag
    }

    public Integer getRawMaxTriggers() { return maxTriggers; }

    /** True iff the stage allows unlimited unlocks ({@code max_triggers == 0}). */
    public boolean isUnlimited() { return getMaxTriggers() == 0; }

    /**
     * True iff the stage may be unlocked more than once (unlimited or {@code > 1}).
     * The cooldown only applies in this case.
     */
    public boolean allowsMultiple() { return getMaxTriggers() != 1; }

    public int getCooldown() { return cooldown; }

    public void setCooldown(int cooldown) { this.cooldown = Math.max(0, cooldown); }

    public DurationUnit getCooldownUnit() { return DurationUnit.parse(cooldownUnit); }

    public String getRawCooldownUnit() { return cooldownUnit; }

    public void setCooldownUnit(DurationUnit unit) {
        this.cooldownUnit = (unit != null ? unit : DurationUnit.HOURS).serialize();
    }

    /** Total unlock duration in game ticks. Always at least 1 so the timer can expire. */
    public long durationTicks() {
        return Math.max(1L, (long) duration * getDurationUnit().ticksPerUnit());
    }

    /** Cooldown in game ticks (0 = no cooldown). Only meaningful when {@link #allowsMultiple()}. */
    public long cooldownTicks() {
        return Math.max(0L, (long) cooldown * getCooldownUnit().ticksPerUnit());
    }

    public TemporaryConfig copy() {
        return new TemporaryConfig(duration, getDurationUnit(), getMaxTriggers(), cooldown, getCooldownUnit());
    }
}
