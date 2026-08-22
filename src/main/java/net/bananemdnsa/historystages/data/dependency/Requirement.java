package net.bananemdnsa.historystages.data.dependency;

import java.util.List;

import net.bananemdnsa.historystages.data.DependencyGroup;

/**
 * One kind of thing a dependency group can demand — items, stages, advancements, and so on.
 *
 * <p>A requirement is a <em>view</em> over a {@link DependencyGroup}, not a store. The entries
 * still live in the same typed fields they always have, and the on-disk format is unchanged;
 * the requirement just makes them reachable without naming the field. That is the same
 * relationship {@link net.bananemdnsa.historystages.data.lock.category.LockCategory} has to a
 * stage, and it is what lets the checker stop knowing eight kinds by hand.
 */
public interface Requirement {

    /** Namespaced, stable, used as a map key — never a display string. */
    String id();

    /**
     * This requirement's entries on the given group. Empty when the group declares none.
     *
     * <p>The order entries are added in is the order they appear in the UI, so a view returns
     * them in the order its field holds them.
     */
    List<DependencyResult.EntryResult> evaluate(DependencyGroup group, RequirementContext ctx);
}
