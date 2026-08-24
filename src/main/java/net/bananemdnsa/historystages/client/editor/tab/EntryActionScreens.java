package net.bananemdnsa.historystages.client.editor.tab;

import java.util.function.Consumer;

import com.google.gson.JsonObject;

import net.bananemdnsa.historystages.client.editor.NbtItemEditScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Builds the screens the built-in {@link EntryAction} factories open.
 *
 * <p>Exists for one reason, and it is not tidiness: {@code new NbtItemEditScreen(...)} handed to a
 * {@code Screen} parameter is an assignment between two Minecraft types, and the bytecode verifier
 * loads <em>both</em> the moment it touches the enclosing class. Left inside {@code EntryAction},
 * that took every unit test naming {@code EntryAction} down with a {@code NoClassDefFoundError} —
 * from a test that mentions no screen at all.
 *
 * <p>The same reason {@code ScoreboardLookup} exists on the requirement side. Behind a call, the
 * check happens in this class instead, and this class is one nothing tests.
 */
final class EntryActionScreens {

    private EntryActionScreens() {}

    /** The mod's NBT editor, returned as a plain {@link Screen}. */
    static Screen nbtEditor(String itemId, JsonObject current, Consumer<JsonObject> onSave) {
        return new NbtItemEditScreen(Minecraft.getInstance().screen, itemId, current, onSave);
    }
}
