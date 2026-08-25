package net.bananemdnsa.historystages.api.editor;

import net.bananemdnsa.historystages.api.editor.EntryActionContext;
import net.bananemdnsa.historystages.api.editor.EntryActionScreens;
import net.bananemdnsa.historystages.client.editor.tab.PopupOverlays;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import com.google.gson.JsonObject;

import net.bananemdnsa.historystages.client.editor.widget.popup.DimensionFilterPopup;
import net.bananemdnsa.historystages.client.editor.widget.popup.InteractionActionsPopup;
import net.bananemdnsa.historystages.client.editor.widget.popup.SpawnSourcesPopup;

/**
 * One extra entry in a tab row's right-click menu, declared by whoever owns the tab.
 *
 * <p>The built-in menus were assembled inside the editor screens, in an if-chain on the category
 * id, so an addon could offer nothing of its own beyond copy and remove. A declared action is how
 * it offers something — and the factories below are how it offers one of the mod's own.
 *
 * <p>Takes the row index rather than the entry: the tab already owns its rows, and passing a typed
 * entry would mean threading that type through every declaration for nothing — the addon closes
 * over its own tab and looks the entry up itself.
 *
 * <p>Client-side, like everything else that draws. A category or requirement is declared on the
 * common side, where the server reads it; its menu is not.
 */
public interface EntryAction {

    /** Lang key for the menu row. */
    String langKey();

    /** Runs the action. Everything it may need from the host is on the context. */
    void run(EntryActionContext ctx);

    static EntryAction of(String langKey, Consumer<EntryActionContext> handler) {
        Objects.requireNonNull(langKey, "langKey");
        Objects.requireNonNull(handler, "handler");
        return new EntryAction() {
            @Override
            public String langKey() {
                return langKey;
            }

            @Override
            public void run(EntryActionContext ctx) {
                handler.accept(ctx);
            }
        };
    }

    /**
     * The mod's NBT editor, for a tab that keeps an NBT payload per entry.
     *
     * <p>A screen rather than an overlay, which is why the context carries both — this one is the
     * reason {@code openScreen} exists beside {@code openOverlay}.
     *
     * @param itemId  the item whose NBT is being edited, by row
     * @param current that row's NBT, or null for none
     * @param apply   called with the row and the new NBT once the editor is confirmed
     */
    static EntryAction editNbt(IntFunction<String> itemId, IntFunction<JsonObject> current,
                               BiConsumer<Integer, JsonObject> apply) {
        return of("editor.historystages.context.edit_nbt", ctx -> {
            int index = ctx.index();
            // Built elsewhere on purpose — see EntryActionScreens for what happens otherwise.
            ctx.openScreen(EntryActionScreens.nbtEditor(itemId.apply(index), current.apply(index),
                    nbt -> {
                        apply.accept(index, nbt);
                        ctx.markChanged();
                    }));
        });
    }

    /**
     * The mod's dimension filter.
     *
     * @param entryId the entry the filter is about, by row
     * @param current the dimensions currently allowed for that row
     * @param apply   called with the row and the new allow-list once confirmed
     */
    static EntryAction dimensionFilter(IntFunction<String> entryId, IntFunction<List<String>> current,
                                       BiConsumer<Integer, List<String>> apply) {
        return of("editor.historystages.context.dimension_filter", ctx -> {
            int index = ctx.index();
            DimensionFilterPopup popup = new DimensionFilterPopup((id, allowed) -> {
                apply.accept(index, allowed);
                ctx.markChanged();
            });
            // Not shown here: the host shows it, because only the host knows where its centre is.
            ctx.openOverlay(PopupOverlays.wrap(popup, entryId.apply(index), current.apply(index)));
        });
    }

    /**
     * The mod's spawn-source filter.
     *
     * @param entryId the entry the filter is about, by row
     * @param current the sources currently blocked for that row
     * @param apply   called with the row and the new block-list once confirmed
     */
    static EntryAction spawnSources(IntFunction<String> entryId, IntFunction<List<String>> current,
                                    BiConsumer<Integer, List<String>> apply) {
        return of("editor.historystages.context.spawn_sources", ctx -> {
            int index = ctx.index();
            SpawnSourcesPopup popup = new SpawnSourcesPopup((id, blocked) -> {
                apply.accept(index, blocked);
                ctx.markChanged();
            });
            ctx.openOverlay(PopupOverlays.wrap(popup, entryId.apply(index), current.apply(index)));
        });
    }

    /**
     * The mod's interaction-action filter.
     *
     * @param entryId the entry the filter is about, by row
     * @param current the actions currently blocked for that row
     * @param apply   called with the row and the new block-list once confirmed
     */
    static EntryAction interactionActions(IntFunction<String> entryId, IntFunction<List<String>> current,
                                          BiConsumer<Integer, List<String>> apply) {
        return of("editor.historystages.context.interaction_actions", ctx -> {
            int index = ctx.index();
            InteractionActionsPopup popup = new InteractionActionsPopup((id, blocked) -> {
                apply.accept(index, blocked);
                ctx.markChanged();
            });
            ctx.openOverlay(PopupOverlays.wrap(popup, entryId.apply(index), current.apply(index)));
        });
    }
}
