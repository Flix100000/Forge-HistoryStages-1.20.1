package net.bananemdnsa.historystages.data.scroll;

import java.util.List;

/**
 * One labelled block in the world chapter — dimensions, structures or biomes. {@code labelKey} is
 * a lang key; the ids are raw registry ids and are turned into display names by the screen.
 */
public record OpenScrollWorldGroup(String labelKey, List<String> ids) {

    public OpenScrollWorldGroup {
        ids = ids == null ? List.of() : List.copyOf(ids);
    }
}
