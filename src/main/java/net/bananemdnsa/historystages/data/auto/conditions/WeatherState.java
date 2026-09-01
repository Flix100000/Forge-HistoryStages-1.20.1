package net.bananemdnsa.historystages.data.auto.conditions;

import org.jetbrains.annotations.Nullable;

/** The three weather states a {@link WeatherTrigger} can wait for. */
public enum WeatherState {
    CLEAR("clear"),
    RAIN("rain"),
    THUNDER("thunder");

    private final String serialized;

    WeatherState(String serialized) { this.serialized = serialized; }

    public String serialize() { return serialized; }

    /** Null for an unknown or missing value — a trigger without a state never fires. */
    @Nullable
    public static WeatherState parse(String raw) {
        if (raw == null) return null;
        for (WeatherState s : values()) {
            if (s.serialized.equalsIgnoreCase(raw)) return s;
        }
        return null;
    }
}
