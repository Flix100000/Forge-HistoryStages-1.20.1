package net.bananemdnsa.historystages.data.auto.conditions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class EffectTriggerTest {

    @Test
    void theTypeDiscriminatorIsEffect() {
        assertEquals("effect", new EffectTrigger("minecraft:blindness").type());
    }

    @Test
    void theSameEffectGivesTheSameSignature() {
        assertEquals(new EffectTrigger("minecraft:blindness").signature(),
                new EffectTrigger("minecraft:blindness").signature());
    }

    @Test
    void differentEffectsGiveDifferentSignatures() {
        assertNotEquals(new EffectTrigger("minecraft:blindness").signature(),
                new EffectTrigger("minecraft:wither").signature());
    }

    /** The value hash must not collide with another type's for the same id. */
    @Test
    void anEffectDoesNotShareASignatureWithAnItemOfTheSameId() {
        assertNotEquals(new EffectTrigger("minecraft:luck").signature(),
                new ItemTrigger("minecraft:luck").signature());
    }
}
