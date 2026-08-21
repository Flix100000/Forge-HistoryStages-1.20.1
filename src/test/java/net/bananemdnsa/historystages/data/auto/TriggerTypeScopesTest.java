package net.bananemdnsa.historystages.data.auto;

import net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition;
import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggerTypeScopesTest {

    /** Any Gson-deserialisable TriggerCondition will do; the registry only stores the class. */
    private static Class<? extends TriggerCondition> anyCondition() {
        return TriggerTypes.classFor("playtime");
    }

    @BeforeEach
    @AfterEach
    void reset() {
        TriggerTypes.resetForTesting();
    }

    @Test
    void aTypeRegisteredWithoutScopesSupportsBoth() {
        TriggerTypes.register("mymod:relic", anyCondition());

        assertTrue(TriggerTypes.scopesOf("mymod:relic").contains(StageScope.GLOBAL));
        assertTrue(TriggerTypes.scopesOf("mymod:relic").contains(StageScope.INDIVIDUAL));
    }

    @Test
    void aDeclaredScopeNarrowsIt() {
        TriggerTypes.register("mymod:relic", anyCondition(), StageScope.GLOBAL);

        assertTrue(TriggerTypes.scopesOf("mymod:relic").contains(StageScope.GLOBAL));
        assertFalse(TriggerTypes.scopesOf("mymod:relic").contains(StageScope.INDIVIDUAL));
    }

    @Test
    void aBuiltInSupportsBothScopes() {
        assertEquals(2, TriggerTypes.scopesOf("playtime").size());
    }

    /**
     * The rule that must never bite: a trigger from an addon that is not installed already
     * survives load and save untouched, and whether it runs is decided by classFor returning
     * null. Scopes must not become a second, subtler filter on the same data.
     */
    @Test
    void anUnknownTypeReportsBothScopes() {
        assertEquals(2, TriggerTypes.scopesOf("notinstalled:whatever").size());
    }

    @Test
    void registeringWithNoScopeAtAllIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> TriggerTypes.register("mymod:relic", anyCondition(), new StageScope[0]));
    }

    @Test
    void typesForScopeOmitsANarrowedTypeInTheOtherScope() {
        TriggerTypes.register("mymod:relic", anyCondition(), StageScope.GLOBAL);

        assertTrue(TriggerTypes.typesForScope(StageScope.GLOBAL).contains("mymod:relic"));
        assertFalse(TriggerTypes.typesForScope(StageScope.INDIVIDUAL).contains("mymod:relic"));
    }

    @Test
    void typesForScopeStillListsEveryBuiltIn() {
        assertTrue(TriggerTypes.typesForScope(StageScope.INDIVIDUAL).contains("playtime"));
        assertTrue(TriggerTypes.typesForScope(StageScope.GLOBAL).contains("playtime"));
    }

    @Test
    void resetForTestingClearsDeclaredScopes() {
        TriggerTypes.register("mymod:relic", anyCondition(), StageScope.GLOBAL);
        TriggerTypes.resetForTesting();
        TriggerTypes.register("mymod:relic", anyCondition());

        assertEquals(2, TriggerTypes.scopesOf("mymod:relic").size(),
                "a declared scope leaked past resetForTesting into the next test");
    }
}
