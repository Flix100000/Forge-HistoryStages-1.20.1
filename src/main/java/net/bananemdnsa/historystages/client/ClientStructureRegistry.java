package net.bananemdnsa.historystages.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client-side cache of structure IDs and structure tag IDs synced from the
 * server. Used by the editor UI to populate the searchable structure list
 * (and its Tags tab).
 */
public class ClientStructureRegistry {
    private static final List<String> STRUCTURE_IDS = new ArrayList<>();
    private static final List<String> STRUCTURE_TAG_IDS = new ArrayList<>();

    public static synchronized void set(List<String> ids, List<String> tagIds) {
        STRUCTURE_IDS.clear();
        STRUCTURE_IDS.addAll(ids);
        Collections.sort(STRUCTURE_IDS, String::compareToIgnoreCase);

        STRUCTURE_TAG_IDS.clear();
        STRUCTURE_TAG_IDS.addAll(tagIds);
        Collections.sort(STRUCTURE_TAG_IDS, String::compareToIgnoreCase);
    }

    /** Backwards-compat overload for callers that only had IDs (no tags). */
    public static synchronized void set(List<String> ids) {
        set(ids, Collections.emptyList());
    }

    public static synchronized List<String> get() {
        return new ArrayList<>(STRUCTURE_IDS);
    }

    public static synchronized List<String> getTags() {
        return new ArrayList<>(STRUCTURE_TAG_IDS);
    }
}
