package net.bananemdnsa.historystages.client.editor.graph;

import net.bananemdnsa.historystages.client.cache.ClientIndividualStageCache;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;

/**
 * Resolves a namespaced graph key against the client's unlock caches.
 *
 * <p>The namespace prefix is what makes this more than a one-liner: a global and an individual
 * stage may share an id, so the two caches must be asked separately.</p>
 */
final class GraphUnlocks {

    private GraphUnlocks() {
    }

    /** {@code g:steinzeit} / {@code i:erste_quest} — see {@code StageManager.graphKey}. */
    static boolean isUnlocked(String graphKey) {
        boolean individual = graphKey.startsWith("i:");
        String id = graphKey.substring(2);
        return individual
                ? ClientIndividualStageCache.isStageUnlocked(id)
                : ClientStageCache.isStageUnlocked(id);
    }
}
