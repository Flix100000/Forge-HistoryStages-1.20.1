package net.bananemdnsa.historystages.data.auto.conditions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherTriggerTest {

    private static WeatherTrigger of(WeatherState state) {
        return new WeatherTrigger(state.serialize());
    }

    @Test
    void everyStateParsesBackToItself() {
        for (WeatherState s : WeatherState.values()) {
            assertEquals(s, WeatherState.parse(s.serialize()));
        }
    }

    @Test
    void anUnknownStateIsNullRatherThanADefault() {
        assertNull(WeatherState.parse("drizzle"));
        assertNull(WeatherState.parse(null));
    }

    @Test
    void clearMatchesOnlyDryWeather() {
        WeatherTrigger t = of(WeatherState.CLEAR);

        assertTrue(t.matches(false, false));
        assertFalse(t.matches(true, false));
        assertFalse(t.matches(true, true));
    }

    /**
     * The one that surprises people: vanilla {@code Level.isRaining()} is true during a
     * thunderstorm, so "rain" has to match one too. Anything else would be this mod inventing its
     * own definition of rain.
     */
    @Test
    void rainMatchesAThunderstormToo() {
        WeatherTrigger t = of(WeatherState.RAIN);

        assertFalse(t.matches(false, false));
        assertTrue(t.matches(true, false));
        assertTrue(t.matches(true, true));
    }

    @Test
    void thunderMatchesOnlyTheStorm() {
        WeatherTrigger t = of(WeatherState.THUNDER);

        assertFalse(t.matches(false, false));
        assertFalse(t.matches(true, false));
        assertTrue(t.matches(true, true));
    }

    @Test
    void anUnknownStateNeverMatches() {
        WeatherTrigger t = new WeatherTrigger("drizzle");

        assertFalse(t.matches(false, false));
        assertFalse(t.matches(true, false));
        assertFalse(t.matches(true, true));
    }

    @Test
    void differentStatesGiveDifferentSignatures() {
        assertNotEquals(of(WeatherState.CLEAR).signature(), of(WeatherState.RAIN).signature());
        assertNotEquals(of(WeatherState.RAIN).signature(), of(WeatherState.THUNDER).signature());
    }

    @Test
    void theTypeDiscriminatorIsWeather() {
        assertEquals("weather", of(WeatherState.RAIN).type());
    }
}
