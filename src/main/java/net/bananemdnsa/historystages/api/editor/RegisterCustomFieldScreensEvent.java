package net.bananemdnsa.historystages.api.editor;

import net.bananemdnsa.historystages.api.editor.CustomFieldScreens;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired once on the client so an addon can supply the screens for its {@code CUSTOM_SCREEN}
 * fields, on stage settings and in the config screen alike.
 *
 * <p>One event for both axes because it is one question: which screen edits this field. Declaring
 * the field stays common-side, where the value is read, written and synced.
 *
 * <pre>{@code
 * modEventBus.addListener(RegisterCustomFieldScreensEvent.class, event -> event.register(
 *         MySettings.LAYOUT,
 *         (parent, current, onDone) -> new MyLayoutScreen(parent, current, onDone)));
 * }</pre>
 */
public class RegisterCustomFieldScreensEvent extends Event implements IModBusEvent {

    public void register(Object field, CustomFieldScreens.Factory factory) {
        CustomFieldScreens.register(field, factory);
    }
}
