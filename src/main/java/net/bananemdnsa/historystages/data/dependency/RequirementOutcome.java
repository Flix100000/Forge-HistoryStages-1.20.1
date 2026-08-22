package net.bananemdnsa.historystages.data.dependency;

/**
 * What an addon returns for one of its entries, before HistoryStages turns it into a
 * {@link DependencyResult.EntryResult}.
 *
 * <p>Addons do not build {@code EntryResult} themselves on purpose. It has four constructors and
 * the shortest silently sets {@code id} to the empty string, which drops the tooltip icon to
 * "unknown" — a trap that has already cost time inside this project, where the constructors are
 * visible. A foreign mod has no way to know about it, so the choice is not offered.
 *
 * @param id          the entry's machine id, used for the tooltip icon — never a display string
 * @param description human-readable, already translated by the addon if it wants that
 * @param fulfilled   whether this entry is satisfied right now
 * @param current     progress towards {@code required}; pass {@code fulfilled ? 1 : 0} when the
 *                    requirement is a yes/no rather than a count
 * @param required    what {@code current} is measured against; pass 1 for a yes/no
 */
public record RequirementOutcome(String id, String description, boolean fulfilled,
        int current, int required) {
}
