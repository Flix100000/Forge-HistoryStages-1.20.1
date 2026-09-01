package net.bananemdnsa.historystages.client.editor.dialog;

import net.bananemdnsa.historystages.api.editor.widget.AbstractInputScreen;
import net.bananemdnsa.historystages.api.editor.widget.InputField;
import net.bananemdnsa.historystages.api.editor.widget.InputValues;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Asks for a time-of-day window in ticks.
 *
 * <p>{@code from} above {@code to} is allowed and means a window across midnight — the trigger
 * reads it that way, so the dialog must not reject it.
 */
public class TimeWindowScreen extends AbstractInputScreen {

    /** Carries the confirmed window back to the trigger editor. */
    public interface Result {
        void accept(int from, int to);
    }

    private final Screen parent;
    private final int initialFrom;
    private final int initialTo;
    private final Result onDone;

    public TimeWindowScreen(Screen parent, Component title, int from, int to, Result onDone) {
        super(parent, title);
        this.parent = parent;
        this.initialFrom = from;
        this.initialTo = to;
        this.onDone = onDone;
    }

    @Override
    protected List<InputField> fields() {
        return List.of(
                InputField.number("from")
                        .label(Component.translatable("editor.historystages.auto_trigger.world_time.from"))
                        .range(0, 23999)
                        .initial(initialFrom),
                InputField.number("to")
                        .label(Component.translatable("editor.historystages.auto_trigger.world_time.to"))
                        .range(0, 23999)
                        .initial(initialTo));
    }

    @Override
    protected void onConfirm(InputValues values) {
        onDone.accept(values.getInt("from"), values.getInt("to"));
        this.minecraft.setScreen(parent);
    }
}
