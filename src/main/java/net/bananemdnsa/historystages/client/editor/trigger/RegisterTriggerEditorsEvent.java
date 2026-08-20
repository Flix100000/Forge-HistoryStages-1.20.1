package net.bananemdnsa.historystages.client.editor.trigger;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired once on the client so an addon's auto-trigger type can be authored in the editor.
 *
 * <pre>{@code
 * modEventBus.addListener(RegisterTriggerEditorsEvent.class, event -> event.register(
 *         TriggerEditor.ofIdList("mymod:relic_found",
 *                 "editor.mymod.auto_trigger.relic_found",
 *                 "editor.mymod.search.relics",
 *                 MyRelics::allIds,
 *                 RelicFoundTrigger::new)));
 * }</pre>
 */
public class RegisterTriggerEditorsEvent extends Event implements IModBusEvent {

    public void register(TriggerEditor editor) {
        TriggerEditors.register(editor);
    }
}
