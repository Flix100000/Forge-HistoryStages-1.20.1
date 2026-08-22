package net.bananemdnsa.historystages.client.editor.dep;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired once on the client so an addon can give its requirement type a tab in the dependency
 * editor.
 *
 * <p>Separate from the event that registers the requirement itself: that one is common-side,
 * because the server gates with it, while a tab is pure UI. Registering a requirement without an
 * editor is fine and means exactly what it looks like — the requirement works, it just cannot be
 * edited in game.
 *
 * <pre>{@code
 * modEventBus.addListener(RegisterRequirementEditorsEvent.class, event -> event.register(
 *         RequirementEditor.ofIdCount("mymod:relic",
 *                 "editor.mymod.search.relics",
 *                 "editor.mymod.dep.dialog.relic_count",
 *                 MyRelics::allKnownRelicIds)));
 * }</pre>
 */
public class RegisterRequirementEditorsEvent extends Event implements IModBusEvent {

    public void register(RequirementEditor editor) {
        RequirementEditors.register(editor);
    }
}
