package net.bananemdnsa.historystages.demo;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.dependency.AddonRequirement;
import net.bananemdnsa.historystages.data.dependency.IdCountEntry;
import net.bananemdnsa.historystages.data.dependency.RegisterRequirementTypesEvent;
import net.bananemdnsa.historystages.data.dependency.RequirementDisplay;
import net.bananemdnsa.historystages.data.dependency.RequirementOutcome;
import net.bananemdnsa.historystages.data.dependency.RequirementStorage;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * The stand-in addon's own kind of dependency: "hand in N of a relic".
 *
 * <p>Shares the enable flag with {@link DemoAddonCategory}, so like the rest of the demo it never
 * exists for a player. Written the way a real addon would: register a type, say how its entries
 * serialise, supply the logic that answers whether one is satisfied.
 *
 * <p>Stores {@link IdCountEntry} because that is what the free-tier editor understands — one
 * registration on the client and the tab looks and behaves like a built-in, with no UI code here.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DemoRequirement {

    /** Namespaced like any addon must be — {@code historystages} is reserved for the built-ins. */
    public static final String REQUIREMENT_ID = "hsdemo:relic";

    private DemoRequirement() {}

    @SubscribeEvent
    public static void onRegisterRequirementTypes(RegisterRequirementTypesEvent event) {
        if (!DemoAddonCategory.enabled()) return;

        event.register(AddonRequirement.<IdCountEntry>builder(REQUIREMENT_ID)
                .tabLangKey("editor.historystages.demo.dep.tab.relics")
                .tooltipLangKey("editor.historystages.demo.dep.tooltip.relics")
                .sectionLangKey("editor.historystages.demo.graph.section.relics")
                .storage(RequirementStorage.gson(IdCountEntry.class))
                .displayKind(RequirementDisplay.Kind.COUNTED)
                .evaluator(DemoRequirement::check)
                .build());
    }

    /**
     * A real addon would count what the player actually has. The demo counts the letters of the
     * relic id, which is deterministic, needs no world state, and is enough to see a requirement
     * flip from open to met when the required amount is lowered past it.
     */
    private static RequirementOutcome check(IdCountEntry entry, net.bananemdnsa.historystages.data
            .dependency.RequirementContext ctx) {
        int found = entry.id().length();
        return new RequirementOutcome(entry.id(), entry.count() + "x " + entry.id(),
                found >= entry.count(), found, entry.count());
    }
}
