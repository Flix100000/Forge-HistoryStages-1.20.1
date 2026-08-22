package net.bananemdnsa.historystages.data.dependency;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.bananemdnsa.historystages.data.DependencyGroup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The addon block on a dependency group. A stage file saved by an instance that does not have the
 * owning addon installed has to come back out unchanged, which is the whole reason the block is
 * raw JSON rather than a parsed type.
 */
class DependencyGroupAddonBlockTest {

    private static final Gson GSON = new Gson();

    private static JsonElement relics(int count) {
        return JsonParser.parseString("[{\"id\":\"mymod:shard\",\"count\":" + count + "}]");
    }

    @Test
    void anUninstalledAddonsRequirementSurvivesAReadWriteRoundTrip() {
        DependencyGroup group = new DependencyGroup();
        group.setAddonEntries("mymod:relic", relics(3));

        String json = GSON.toJson(group);
        DependencyGroup reloaded = GSON.fromJson(json, DependencyGroup.class);

        assertEquals(relics(3), reloaded.addonEntries("mymod:relic"));
    }

    @Test
    void anEmptyWriteClearsTheSlotInsteadOfLeavingAStub() {
        DependencyGroup group = new DependencyGroup();
        group.setAddonEntries("mymod:relic", relics(3));

        group.setAddonEntries("mymod:relic", null);

        assertNull(group.addonEntries("mymod:relic"));
        assertFalse(GSON.toJson(group).contains("mymod:relic"));
    }

    @Test
    void aGroupHoldingOnlyAnAddonRequirementIsNotEmpty() {
        DependencyGroup group = new DependencyGroup();
        assertTrue(group.isEmpty());

        group.setAddonEntries("mymod:relic", relics(1));

        assertFalse(group.isEmpty());
    }

    @Test
    void anAddonRequirementCountsAsANonStageRequirement() {
        DependencyGroup group = new DependencyGroup();
        assertFalse(group.hasNonStageRequirements());

        group.setAddonEntries("mymod:relic", relics(1));

        assertTrue(group.hasNonStageRequirements());
    }

    @Test
    void copyDeepCopiesTheAddonBlock() {
        DependencyGroup group = new DependencyGroup();
        group.setAddonEntries("mymod:relic", relics(1));

        DependencyGroup copy = group.copy();

        assertEquals(group.addonEntries("mymod:relic"), copy.addonEntries("mymod:relic"));
        assertNotSame(group.addonEntries("mymod:relic"), copy.addonEntries("mymod:relic"));
    }

    @Test
    void addonIdsListsWhatIsStored() {
        DependencyGroup group = new DependencyGroup();
        group.setAddonEntries("b:two", relics(1));
        group.setAddonEntries("a:one", relics(1));

        assertEquals(java.util.List.of("b:two", "a:one"),
                java.util.List.copyOf(group.addonRequirementIds()));
    }
}
