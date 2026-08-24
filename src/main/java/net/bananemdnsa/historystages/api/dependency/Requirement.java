package net.bananemdnsa.historystages.api.dependency;

import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.api.dependency.RequirementContext;
import net.bananemdnsa.historystages.api.dependency.RequirementDisplay;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.api.stage.StageScope;

/**
 * One kind of thing a dependency group can demand — items, stages, advancements, and so on.
 *
 * <p>A requirement is a <em>view</em> over a {@link DependencyGroup}, not a store. The built-in
 * entries still live in the same typed fields they always have, and the on-disk format is
 * unchanged; the requirement just makes them reachable without naming the field. That is the same
 * relationship {@link net.bananemdnsa.historystages.api.lock.LockCategory} has to a
 * stage, and it is what lets the checker stop knowing eight kinds by hand.
 *
 * <p>A requirement from another mod has no typed field to be a view over, so it stores through
 * the group's raw {@code addons} block instead — see {@link AddonRequirement}. That is the one
 * place the two halves differ.
 */
public interface Requirement {

    /** Namespaced, stable, used as a map key — never a display string. */
    String id();

    /**
     * Which stage scopes this requirement means anything in.
     *
     * <p>A fact about the data, not about the editor: a kill belongs to a player, so demanding
     * one of a global stage has no answer — there is no single player to ask. Both scopes unless
     * a requirement says otherwise, which is the rule the other three axes already use.
     */
    default Set<StageScope> supportedScopes() {
        return EnumSet.allOf(StageScope.class);
    }

    /** Lang key for the editor tab label. */
    String tabLangKey();

    /** Lang key for the editor tab tooltip. */
    String tooltipLangKey();

    /** Lang key for this requirement's section heading in the graph detail panel. */
    String sectionLangKey();

    /**
     * How the graph may honestly present this requirement. {@code BINARY} is the safe default:
     * a status glyph and no invented count.
     */
    default RequirementDisplay.Kind displayKind() {
        return RequirementDisplay.Kind.BINARY;
    }

    /**
     * Whether this group declares anything for this requirement, without evaluating it.
     *
     * <p>Separate from {@link #evaluate} because evaluation needs a player and a level, and the
     * loader has neither — it runs at mod setup, long before anyone is in a world. Not a default:
     * a new requirement that forgot this would silently report itself absent everywhere.
     */
    boolean declaredIn(DependencyGroup group);

    /**
     * This requirement's entries on the given group. Empty when the group declares none.
     *
     * <p>The order entries are added in is the order they appear in the UI, so a view returns
     * them in the order its field holds them.
     */
    List<RequirementResult.EntryResult> evaluate(DependencyGroup group, RequirementContext ctx);
}
