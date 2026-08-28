package net.bananemdnsa.historystages.data.dependency;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where a player's deposited progress is filed, and what happens to it when the stage's group
 * list is edited afterwards.
 *
 * <p>The keys here are the literal strings sitting in {@code DepositedDependencies} on every
 * research scroll in every existing world, which is why the first test spells one out rather
 * than building it from the same helper it checks.
 */
class DependencyProgressTest {

    private static final Gson GSON = new Gson();

    private static DependencyGroup group(String id) {
        DependencyGroup group = new DependencyGroup();
        group.setId(id);
        return group;
    }

    @Test
    void aGroupWithoutAnIdIsFiledUnderItsPosition() {
        DependencyGroup legacy = new DependencyGroup();

        String key = DependencyProgress.key(DependencyProgress.groupKey(legacy, 0),
                DependencyProgress.itemSuffix("minecraft:diamond"));

        assertEquals("Group_0_Item_minecraft:diamond", key);
    }

    @Test
    void assigningIdsToAFileThatHasNoneLeavesEveryKeyWhereItWas() {
        List<DependencyGroup> groups = new ArrayList<>(
                List.of(new DependencyGroup(), new DependencyGroup(), new DependencyGroup()));

        assertTrue(DependencyProgress.assignIds(groups).isEmpty());

        // The whole point of handing out positions rather than fresh ids on load: a scroll
        // carrying Group_2_XP still points at the third group afterwards.
        assertEquals("Group_2_XP", DependencyProgress.key(
                DependencyProgress.groupKey(groups.get(2), 2), DependencyProgress.XP_SUFFIX));
    }

    @Test
    void progressFollowsTheGroupWhenAnEarlierGroupIsDeleted() {
        List<DependencyGroup> groups = new ArrayList<>(
                List.of(new DependencyGroup(), new DependencyGroup()));
        DependencyProgress.assignIds(groups);
        DependencyGroup second = groups.get(1);

        String before = DependencyProgress.key(DependencyProgress.groupKey(second, 1),
                DependencyProgress.itemSuffix("minecraft:diamond"));

        groups.remove(0); // the editor's "remove group", which used to shift everything down

        String after = DependencyProgress.key(DependencyProgress.groupKey(second, 0),
                DependencyProgress.itemSuffix("minecraft:diamond"));

        assertEquals(before, after,
                "what a player deposited into the second group has to stay with that group when"
                        + " the first one is deleted, not move onto whatever takes its place");
    }

    @Test
    void twoGroupsNeverShareAKey() {
        List<DependencyGroup> groups = new ArrayList<>(
                List.of(new DependencyGroup(), new DependencyGroup()));
        DependencyProgress.assignIds(groups);
        groups.remove(0);

        // The group that moved into position 0 must not answer to the deleted group's key.
        assertNotEquals("Group_0_XP", DependencyProgress.key(
                DependencyProgress.groupKey(groups.get(0), 0), DependencyProgress.XP_SUFFIX));
    }

    @Test
    void aDuplicateIdIsTakenAwayFromTheLaterGroup() {
        List<DependencyGroup> groups = new ArrayList<>(List.of(group("g1"), group("g1")));

        List<String> duplicates = DependencyProgress.assignIds(groups);

        assertEquals(List.of("g1"), duplicates);
        assertEquals("g1", groups.get(0).getId());
        assertNotEquals("g1", groups.get(1).getId());
    }

    @Test
    void aReplacementNeverStealsAnIdAnotherGroupAlreadyOwns() {
        // The second group is unnamed and sits at position 1, but "1" is taken by the third.
        List<DependencyGroup> groups = new ArrayList<>(
                List.of(group("0"), new DependencyGroup(), group("1")));

        DependencyProgress.assignIds(groups);

        assertEquals(3, groups.stream().map(DependencyGroup::getId).distinct().count());
    }

    @Test
    void aFreshIdIsNeverOneOfTheIdsInUse() {
        List<DependencyGroup> groups = new ArrayList<>(List.of(group("0"), group("1")));

        for (int i = 0; i < 100; i++) {
            String fresh = DependencyGroup.freshId(groups);
            assertFalse(fresh.equals("0") || fresh.equals("1"), fresh);
        }
    }

    @Test
    void aGroupsIdSurvivesTheWholeEditorSaveRoundTrip() {
        // Hop 1 — StageManager reads the file and hands out ids.
        StageEntry onServer = GSON.fromJson(
                "{\"display_name\":\"Bronze Age\",\"dependencies\":[{},{}]}", StageEntry.class);
        DependencyProgress.assignIds(onServer.getDependencies());

        // Hop 2 — the stage goes to the client, hop 3 — the editor works on a copy of it.
        StageEntry onClient = GSON.fromJson(GSON.toJson(onServer), StageEntry.class);
        StageEntry snapshot = onClient.copy();

        // Hop 4 — the editor deletes the first group and sends what is left.
        snapshot.getDependencies().remove(0);
        StageEntry backOnServer = GSON.fromJson(snapshot.toCompactJson(), StageEntry.class);

        // Hop 5 — saveStage writes this over the file, and it is read once more.
        StageEntry reloaded = GSON.fromJson(backOnServer.toJson(), StageEntry.class);

        assertEquals("1", reloaded.getDependencies().get(0).getId(),
                "the surviving group has to come back out of the file under the id it went in"
                        + " with — losing it anywhere along this chain resets what every player"
                        + " has deposited into that group");
    }

    @Test
    void aDuplicatedGroupCarriesItsIdUntilTheEditorReplacesIt() {
        DependencyGroup original = group("gabc");

        assertEquals("gabc", original.copy().getId(),
                "copy() is how the editor's save path reaches the file; losing the id there would"
                        + " reset progress on every save");
    }
}
