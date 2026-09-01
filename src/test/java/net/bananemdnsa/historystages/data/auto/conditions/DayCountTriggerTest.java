package net.bananemdnsa.historystages.data.auto.conditions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayCountTriggerTest {

    /** Day 1 begins at tick 24000, so 23999 is still day 0. */
    @Test
    void theDayBoundarySitsAtTwentyFourThousand() {
        DayCountTrigger t = new DayCountTrigger(1);

        assertFalse(t.matches(23999L));
        assertTrue(t.matches(24000L));
    }

    @Test
    void aLaterDayStillMatches() {
        assertTrue(new DayCountTrigger(10).matches(10L * 24000L + 5000L));
    }

    @Test
    void anEarlierDayDoesNot() {
        assertFalse(new DayCountTrigger(10).matches(9L * 24000L + 23999L));
    }

    @Test
    void aNegativeDayCountIsRaisedToZero() {
        assertEquals(0, new DayCountTrigger(-3).requiredDays());
        assertTrue(new DayCountTrigger(-3).matches(0L));
    }

    @Test
    void theSignatureIsBuiltFromTheNormalisedDayCount() {
        assertEquals(new DayCountTrigger(0).signature(), new DayCountTrigger(-3).signature());
    }

    @Test
    void differentDayCountsGiveDifferentSignatures() {
        assertNotEquals(new DayCountTrigger(10).signature(), new DayCountTrigger(11).signature());
    }

    /** Same shape as PlaytimeTrigger's, so the two must still not collide. */
    @Test
    void aDayCountDoesNotShareASignatureWithThePlaytimeOfTheSameNumber() {
        assertNotEquals(new DayCountTrigger(7).signature(), new PlaytimeTrigger(7).signature());
    }

    @Test
    void theTypeDiscriminatorIsDayCount() {
        assertEquals("day_count", new DayCountTrigger(7).type());
    }
}
