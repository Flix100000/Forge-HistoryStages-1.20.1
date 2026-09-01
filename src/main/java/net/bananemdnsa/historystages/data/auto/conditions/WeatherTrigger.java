package net.bananemdnsa.historystages.data.auto.conditions;

import net.bananemdnsa.historystages.api.trigger.TriggerCondition;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.Nullable;

/**
 * The weather in the player's level.
 *
 * <p>{@code RAIN} matches a thunderstorm as well, because vanilla {@code Level.isRaining()} does.
 * The alternative would be a definition of "raining" that disagrees with the rest of the game.
 */
public record WeatherTrigger(@SerializedName("state") String state) implements TriggerCondition {

    /** Null when this build does not know the state; such a trigger never fires. */
    @Nullable
    public WeatherState resolvedState() { return WeatherState.parse(state); }

    public boolean matches(boolean raining, boolean thundering) {
        WeatherState resolved = resolvedState();
        if (resolved == null) return false;
        return switch (resolved) {
            case CLEAR -> !raining;
            case RAIN -> raining;
            case THUNDER -> thundering;
        };
    }

    @Override public String type() { return "weather"; }

    @Override public long signature() {
        WeatherState resolved = resolvedState();
        return defaultSignature(resolved == null ? String.valueOf(state) : resolved.serialize());
    }
}
