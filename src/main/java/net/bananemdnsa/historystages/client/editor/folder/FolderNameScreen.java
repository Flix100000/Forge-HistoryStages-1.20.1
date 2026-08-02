package net.bananemdnsa.historystages.client.editor.folder;

import net.bananemdnsa.historystages.client.editor.widget.dialog.AbstractInputScreen;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputField;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputValues;
import net.bananemdnsa.historystages.data.StagePaths;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Asks for a folder name — used both for creating a folder and for renaming one. The name
 * is a single segment; the parent path is decided by the caller.
 */
public class FolderNameScreen extends AbstractInputScreen {

    private final boolean individual;
    private final String parentPath;
    private final String currentName;
    private final Consumer<String> onAccept;

    /**
     * @param parentPath  folder the new/renamed folder sits in, {@code ""} for the tree root
     * @param currentName existing name when renaming, null when creating
     */
    public FolderNameScreen(Screen parent, Component title, boolean individual,
                            String parentPath, String currentName, Consumer<String> onAccept) {
        super(parent, title);
        this.individual = individual;
        this.parentPath = parentPath;
        this.currentName = currentName;
        this.onAccept = onAccept;
    }

    @Override
    protected int dialogWidth() { return 300; }

    @Override
    protected List<InputField> fields() {
        return List.of(InputField.text("name")
                .label(Component.translatable("editor.historystages.folder.name"))
                .maxLength(64)
                .regex("[a-zA-Z0-9_\\-]*")
                .initial(currentName == null ? "" : currentName)
                .validator(this::checkName));
    }

    /**
     * Emptiness, charset and collision checks. The charset branch is a backstop: the field's
     * regex already refuses the characters while typing, including on paste.
     */
    private Component checkName(String name) {
        if (name.isEmpty()) return Component.translatable("editor.historystages.input.empty");
        if (!StagePaths.isValidSegment(name)) return Component.translatable("editor.historystages.id_invalid");
        // Renaming a folder to the name it already has is a no-op, not a collision.
        if (name.equals(currentName)) return null;
        String candidate = StagePaths.join(parentPath, name);
        if (StageFolderTree.exists(individual, candidate)) {
            return Component.translatable("editor.historystages.folder.name_exists");
        }
        return null;
    }

    @Override
    protected void onConfirm(InputValues values) {
        onAccept.accept(values.getString("name"));
        this.minecraft.setScreen(parent);
    }
}
