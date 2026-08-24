package net.bananemdnsa.historystages.data.dependency;

import net.bananemdnsa.historystages.api.dependency.Requirement;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.api.stage.StageScope;

/**
 * Finds requirements a group declares that its stage's scope cannot answer.
 *
 * <p>Deliberately free of Minecraft, so the rule can be unit tested while its only caller sits
 * inside the loader. That split is what the other four axes do too.
 *
 * <p>Reporting only. The entry stays in the file and evaluation skips it — hand editing is a
 * supported path here, and quietly deleting what somebody wrote is the problem the stage-file
 * overwrite guard exists to prevent.
 */
public final class RequirementScopeScan {

    private RequirementScopeScan() {}

    /**
     * The requirement ids this group declares that the given scope cannot answer.
     *
     * <p>A group is asked rather than a whole stage, because the group is where the declarations
     * are. Ids come back in registry order, each at most once, so a caller can name them in a
     * stable message.
     */
    public static List<String> unusable(DependencyGroup group, StageScope scope) {
        List<String> unusable = new ArrayList<>();
        for (Requirement requirement : RequirementTypes.all()) {
            if (requirement.supportedScopes().contains(scope)) continue;
            if (requirement.declaredIn(group)) unusable.add(requirement.id());
        }
        return unusable;
    }
}
