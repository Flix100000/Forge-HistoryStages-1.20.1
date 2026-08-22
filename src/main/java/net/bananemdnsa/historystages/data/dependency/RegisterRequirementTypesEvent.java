package net.bananemdnsa.historystages.data.dependency;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired once so other mods can add their own kinds of dependency requirement.
 *
 * <p>This is the only moment registration is legal: when dispatch ends the registry freezes, and
 * everything that walks it — the checker, the editor's tab strip, the graph's sections — may then
 * assume the list never changes again.
 *
 * <p>Registering here is enough to store and to gate. It is not enough to <em>edit</em>: a tab in
 * the dependency editor is a separate, client-side registration, because HistoryStages cannot
 * guess what a relic is nor which ones exist. See {@code RegisterRequirementEditorsEvent}.
 *
 * <pre>{@code
 * modEventBus.addListener(RegisterRequirementTypesEvent.class, event -> event.register(
 *         AddonRequirement.<RelicDep>builder("mymod:relic")
 *                 .tabLangKey("editor.mymod.dep.tab.relics")
 *                 .tooltipLangKey("editor.mymod.dep.tooltip.relics")
 *                 .sectionLangKey("editor.mymod.graph.section.relics")
 *                 .storage(RequirementStorage.gson(RelicDep.class))
 *                 .displayKind(RequirementDisplay.Kind.COUNTED)
 *                 .evaluator(MyMod::checkRelic)
 *                 .build()));
 * }</pre>
 */
public class RegisterRequirementTypesEvent extends Event implements IModBusEvent {

    public void register(Requirement requirement) {
        RequirementTypes.register(requirement);
    }
}
