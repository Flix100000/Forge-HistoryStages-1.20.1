package net.bananemdnsa.historystages.data.scroll;

import java.util.List;

/**
 * Everything the open scroll screen shows for one stage, already grouped and deduplicated.
 * Built by {@link OpenScrollContent}; free of Minecraft types so it can be built and asserted on
 * in unit tests.
 */
public record OpenScrollDocument(String stageId, boolean individual, String iconId, String displayName,
                                 String description, List<String> itemIds, List<OpenScrollEntry> creatures,
                                 List<OpenScrollWorldGroup> world) {

    public OpenScrollDocument {
        itemIds = itemIds == null ? List.of() : List.copyOf(itemIds);
        creatures = creatures == null ? List.of() : List.copyOf(creatures);
        world = world == null ? List.of() : List.copyOf(world);
    }

    public int itemCount() {
        return itemIds.size();
    }

    public int creatureCount() {
        return creatures.size();
    }

    /** Every world id across all three groups — the number shown on the overview page. */
    public int worldCount() {
        int total = 0;
        for (OpenScrollWorldGroup group : world) total += group.ids().size();
        return total;
    }

    /** True when a chapter has nothing to show and must not get a tab. */
    public boolean isEmpty(OpenScrollChapter chapter) {
        return switch (chapter) {
            case OVERVIEW -> false;
            case ITEMS -> itemIds.isEmpty();
            case CREATURES -> creatures.isEmpty();
            case WORLD -> worldCount() == 0;
        };
    }
}
