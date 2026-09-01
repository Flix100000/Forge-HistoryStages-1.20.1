package net.bananemdnsa.historystages.data.auto.conditions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XpLevelTriggerTest {

    @Test
    void theLevelThresholdIsMet() {
        XpLevelTrigger t = new XpLevelTrigger(30);

        assertFalse(t.matches(29));
        assertTrue(t.matches(30));
        assertTrue(t.matches(31));
    }

    @Test
    void aNegativeLevelIsRaisedToZero() {
        assertEquals(0, new XpLevelTrigger(-4).requiredLevel());
        assertTrue(new XpLevelTrigger(-4).matches(0));
    }

    @Test
    void theSignatureIsBuiltFromTheNormalisedLevel() {
        assertEquals(new XpLevelTrigger(0).signature(), new XpLevelTrigger(-4).signature());
    }

    @Test
    void differentLevelsGiveDifferentSignatures() {
        assertNotEquals(new XpLevelTrigger(30).signature(), new XpLevelTrigger(31).signature());
    }

    @Test
    void theTypeDiscriminatorIsXpLevel() {
        assertEquals("xp_level", new XpLevelTrigger(30).type());
    }
}
