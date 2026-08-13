package net.bananemdnsa.historystages.client.editor.dialog;

import net.bananemdnsa.historystages.client.editor.widget.dialog.AbstractInputScreen;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputField;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputValues;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.IntConsumer;

/**
 * Asks for a single count / threshold value, showing the dependency's id as the subtitle.
 *
 * <p>Replaces the dialog {@code DependencyEditorScreen} used to hand-draw inline, which had
 * no clipboard, selection or cursor movement because it rendered its own text field.
 */
public class CountInputScreen extends AbstractInputScreen {

    private final Screen parent;
    private final String subjectId;
    private final int initialValue;
    private final int min;
    private final int max;
    private final IntConsumer onDone;

    public CountInputScreen(Screen parent, Component title, String subjectId,
                            int initialValue, int min, int max, IntConsumer onDone) {
        super(parent, title);
        this.parent = parent;
        this.subjectId = subjectId;
        this.initialValue = initialValue;
        this.min = min;
        this.max = max;
        this.onDone = onDone;
    }

    @Override
    protected Component subtitle() {
        return subjectId == null || subjectId.isEmpty() ? null : Component.literal(subjectId);
    }

    @Override
    protected List<InputField> fields() {
        return List.of(InputField.number("count").range(min, max).initial(initialValue));
    }

    @Override
    protected void onConfirm(InputValues values) {
        onDone.accept(values.getInt("count"));
        this.minecraft.setScreen(parent);
    }
}
