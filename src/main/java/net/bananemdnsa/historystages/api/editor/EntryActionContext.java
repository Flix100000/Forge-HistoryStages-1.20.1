package net.bananemdnsa.historystages.api.editor;

import java.util.function.Consumer;

import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.minecraft.client.gui.screens.Screen;

/**
 * What a right-click action is given when it runs.
 *
 * <p>Three shapes of action exist and one context covers all of them: change data and call
 * {@link #markChanged()}, put up an overlay, or push a screen. Both of the latter belong to the
 * host — a tab has no screen to push onto, and an overlay has to be rendered and fed input by
 * whoever owns the frame.
 *
 * @param index       the row that was right-clicked
 * @param dirtySink   where {@link #markChanged} delivers to
 * @param screenSink  where {@link #openScreen} delivers to
 * @param overlaySink where {@link #openOverlay} delivers to
 */
public record EntryActionContext(int index, Runnable dirtySink,
                                 Consumer<Screen> screenSink,
                                 Consumer<PickerOverlay> overlaySink) {

    /** A do-nothing sink, kept on a wildcard so its erased type names no Minecraft class. */
    private static final Consumer<?> NO_SINK = value -> { };

    /**
     * A context for an action that only changes data.
     *
     * <p>Exists so a unit test can build one at all. The two sinks are typed on Minecraft classes,
     * and naming those from test source is a compile error — Minecraft is on neither test
     * classpath — so a test that wanted to check an action's handler could not construct the
     * argument it has to pass.
     */
    @SuppressWarnings("unchecked")
    public static EntryActionContext dataOnly(int index, Runnable dirtySink) {
        return new EntryActionContext(index, dirtySink,
                (Consumer<Screen>) NO_SINK, (Consumer<PickerOverlay>) NO_SINK);
    }

    /**
     * Call after changing anything, so the editor knows the stage is dirty.
     *
     * <p>Call the methods here, never the accessors: {@code dirtySink()} hands back the
     * {@code Runnable} and does nothing at all, which compiles and throws nothing.
     */
    public void markChanged() {
        dirtySink.run();
    }

    /** Asks the host to push a screen. */
    public void openScreen(Screen screen) {
        screenSink.accept(screen);
    }

    /** Asks the host to show an overlay and feed it input until it hides itself. */
    public void openOverlay(PickerOverlay overlay) {
        overlaySink.accept(overlay);
    }
}
