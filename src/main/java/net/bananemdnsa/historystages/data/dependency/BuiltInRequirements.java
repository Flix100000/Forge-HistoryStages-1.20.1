package net.bananemdnsa.historystages.data.dependency;

import java.util.List;

import net.bananemdnsa.historystages.data.dependency.requirements.AdvancementRequirement;
import net.bananemdnsa.historystages.data.dependency.requirements.EntityKillRequirement;
import net.bananemdnsa.historystages.data.dependency.requirements.IndividualStageRequirement;
import net.bananemdnsa.historystages.data.dependency.requirements.ItemRequirement;
import net.bananemdnsa.historystages.data.dependency.requirements.ScoreboardRequirement;
import net.bananemdnsa.historystages.data.dependency.requirements.StageRequirement;
import net.bananemdnsa.historystages.data.dependency.requirements.StatRequirement;
import net.bananemdnsa.historystages.data.dependency.requirements.XpLevelRequirement;

/**
 * The requirement kinds the mod ships with, in the order the checker evaluated them by hand.
 *
 * <p>That order decides the order entries appear in the UI, so it is not free to change.
 *
 * <p>A plain list and deliberately not a registry: registration is what a later phase adds, and
 * a registry holding nothing but built-ins would suggest the axis is already open when it is not.
 */
final class BuiltInRequirements {

    static final List<Requirement> ALL = List.of(
            new ItemRequirement(),
            new StageRequirement(),
            new IndividualStageRequirement(),
            new AdvancementRequirement(),
            new XpLevelRequirement(),
            new EntityKillRequirement(),
            new StatRequirement(),
            new ScoreboardRequirement());

    private BuiltInRequirements() {}
}
