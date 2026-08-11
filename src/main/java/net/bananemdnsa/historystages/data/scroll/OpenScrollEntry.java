package net.bananemdnsa.historystages.data.scroll;

import java.util.Set;

/**
 * One creature in the document. An entity that is spawn-locked <em>and</em> attack-locked appears
 * once, carrying both markers, rather than twice.
 */
public record OpenScrollEntry(String id, Set<OpenScrollMarker> markers) {

    public OpenScrollEntry {
        markers = markers == null ? Set.of() : Set.copyOf(markers);
    }
}
