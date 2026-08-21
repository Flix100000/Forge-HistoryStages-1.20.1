package net.bananemdnsa.historystages.data.settings;

import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageSettingsGroupsTest {

    private static final Setting<Integer> PRICE =
            Setting.integer("price").range(0, 64).defaultValue(12).langKey("l.price").build();

    private static StageSettingsGroup group(String id) {
        return StageSettingsGroup.builder(id).titleLangKey("l." + id).field(PRICE).build();
    }

    @BeforeEach
    @AfterEach
    void reset() {
        StageSettingsGroups.resetForTesting();
    }

    @Test
    void aRegisteredGroupIsFindableById() {
        StageSettingsGroups.register(group("mymod:trades"));
        assertEquals("mymod:trades", StageSettingsGroups.byId("mymod:trades").id());
    }

    @Test
    void anUnknownIdReadsAsNull() {
        assertNull(StageSettingsGroups.byId("nope:nope"));
    }

    @Test
    void allIsSortedByIdSoTheEditorOrderIsStable() {
        StageSettingsGroups.register(group("zmod:z"));
        StageSettingsGroups.register(group("amod:a"));

        assertEquals(java.util.List.of("amod:a", "zmod:z"), StageSettingsGroups.ids());
    }

    @Test
    void registeringTheSameIdTwiceIsRejected() {
        StageSettingsGroups.register(group("mymod:trades"));
        assertThrows(IllegalArgumentException.class,
                () -> StageSettingsGroups.register(group("mymod:trades")));
    }

    @Test
    void registeringAfterFreezeIsRejected() {
        StageSettingsGroups.freeze();
        assertThrows(IllegalStateException.class,
                () -> StageSettingsGroups.register(group("mymod:trades")));
    }

    @Test
    void groupsAreFilteredByScope() {
        StageSettingsGroups.register(StageSettingsGroup.builder("mymod:global")
                .titleLangKey("l").field(PRICE).supportedScopes(StageScope.GLOBAL).build());
        StageSettingsGroups.register(group("mymod:both"));

        assertEquals(java.util.List.of("mymod:both"),
                StageSettingsGroups.forScope(StageScope.INDIVIDUAL).stream()
                        .map(StageSettingsGroup::id).toList());
    }

    @Test
    void forScopeIsAlsoSortedById() {
        StageSettingsGroups.register(group("zmod:z"));
        StageSettingsGroups.register(group("amod:a"));

        assertEquals(java.util.List.of("amod:a", "zmod:z"),
                StageSettingsGroups.forScope(StageScope.GLOBAL).stream()
                        .map(StageSettingsGroup::id).toList());
    }

    @Test
    void isFrozenReportsTheState() {
        assertFalse(StageSettingsGroups.isFrozen());
        StageSettingsGroups.freeze();
        assertTrue(StageSettingsGroups.isFrozen());
    }
}
