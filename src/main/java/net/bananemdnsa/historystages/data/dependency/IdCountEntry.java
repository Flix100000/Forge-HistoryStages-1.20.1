package net.bananemdnsa.historystages.data.dependency;

/**
 * The entry shape the free-tier editor understands: an id, and optionally an amount.
 *
 * <p>An addon that stores its requirement as a list of these gets a working editor tab from one
 * registration and writes no UI at all. A requirement with a richer shape stores whatever it
 * likes and supplies its own tab instead.
 *
 * @param id    what the entry names — an item, a mob, a relic; the addon decides
 * @param count how many are required. 1 for a yes/no requirement, which is what
 *              {@code RequirementEditor.ofIdList} always writes.
 */
public record IdCountEntry(String id, int count) {
}
