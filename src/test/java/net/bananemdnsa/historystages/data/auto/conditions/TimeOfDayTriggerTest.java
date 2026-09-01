package net.bananemdnsa.historystages.data.auto.conditions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeOfDayTriggerTest {

    @Test
    void everyPresetParsesBackToItself() {
        for (TimePreset p : TimePreset.values()) {
            assertEquals(p, TimePreset.parse(p.serialize()));
        }
    }

    @Test
    void anUnknownPresetIsNullRatherThanADefault() {
        assertNull(TimePreset.parse("midnight"));
        assertNull(TimePreset.parse(null));
    }

    @Test
    void theDayPresetCoversZeroToElevenNineNineNine() {
        TimeOfDayTrigger t = TimeOfDayTrigger.of(TimePreset.DAY);

        assertTrue(t.matches(0L));
        assertTrue(t.matches(11999L));
        assertFalse(t.matches(12000L));
    }

    @Test
    void theSunsetPresetCoversTwelveThousandToTwelveNineNineNine() {
        TimeOfDayTrigger t = TimeOfDayTrigger.of(TimePreset.SUNSET);

        assertFalse(t.matches(11999L));
        assertTrue(t.matches(12000L));
        assertTrue(t.matches(12999L));
        assertFalse(t.matches(13000L));
    }

    @Test
    void theNightPresetCoversThirteenThousandToTwentyTwoNineNineNine() {
        TimeOfDayTrigger t = TimeOfDayTrigger.of(TimePreset.NIGHT);

        assertFalse(t.matches(12999L));
        assertTrue(t.matches(13000L));
        assertTrue(t.matches(22999L));
        assertFalse(t.matches(23000L));
    }

    @Test
    void theSunrisePresetCoversTwentyThreeThousandToTwentyThreeNineNineNine() {
        TimeOfDayTrigger t = TimeOfDayTrigger.of(TimePreset.SUNRISE);

        assertFalse(t.matches(22999L));
        assertTrue(t.matches(23000L));
        assertTrue(t.matches(23999L));
    }

    /** A day time past one day has to be folded back before it is compared. */
    @Test
    void aLaterDayIsFoldedBackIntoTheSameWindow() {
        assertTrue(TimeOfDayTrigger.of(TimePreset.NIGHT).matches(7L * 24000L + 15000L));
        assertFalse(TimeOfDayTrigger.of(TimePreset.NIGHT).matches(7L * 24000L + 1000L));
    }

    @Test
    void aCustomWindowMatchesInsideItsBounds() {
        TimeOfDayTrigger t = TimeOfDayTrigger.custom(6000, 7000);

        assertFalse(t.matches(5999L));
        assertTrue(t.matches(6000L));
        assertTrue(t.matches(7000L));
        assertFalse(t.matches(7001L));
    }

    /** from &gt; to is a window across midnight, not an empty one. */
    @Test
    void aCustomWindowThatWrapsPastMidnightMatchesBothHalves() {
        TimeOfDayTrigger t = TimeOfDayTrigger.custom(22000, 2000);

        assertTrue(t.matches(22000L));
        assertTrue(t.matches(23999L));
        assertTrue(t.matches(0L));
        assertTrue(t.matches(2000L));
        assertFalse(t.matches(2001L));
        assertFalse(t.matches(21999L));
    }

    @Test
    void aCustomWindowIsClampedIntoOneDay() {
        TimeOfDayTrigger t = TimeOfDayTrigger.custom(-500, 99999);

        assertEquals(0, t.windowFrom());
        assertEquals(23999, t.windowTo());
    }

    @Test
    void anUnknownPresetNeverMatches() {
        TimeOfDayTrigger t = new TimeOfDayTrigger("midnight", null, null);

        assertFalse(t.matches(0L));
        assertFalse(t.matches(13000L));
    }

    @Test
    void differentPresetsGiveDifferentSignatures() {
        assertNotEquals(TimeOfDayTrigger.of(TimePreset.DAY).signature(),
                TimeOfDayTrigger.of(TimePreset.NIGHT).signature());
    }

    @Test
    void differentCustomWindowsGiveDifferentSignatures() {
        assertNotEquals(TimeOfDayTrigger.custom(6000, 7000).signature(),
                TimeOfDayTrigger.custom(6000, 7001).signature());
    }

    @Test
    void theSignatureIsBuiltFromTheClampedWindow() {
        assertEquals(TimeOfDayTrigger.custom(0, 23999).signature(),
                TimeOfDayTrigger.custom(-500, 99999).signature());
    }

    @Test
    void theTypeDiscriminatorIsWorldTime() {
        assertEquals("world_time", TimeOfDayTrigger.of(TimePreset.NIGHT).type());
    }
}
