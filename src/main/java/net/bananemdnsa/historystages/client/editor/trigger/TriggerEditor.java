package net.bananemdnsa.historystages.client.editor.trigger;

import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition;

/**
 * How an addon's auto-trigger type becomes something a maintainer can actually add.
 *
 * <p>Registering the type is enough to load, save and fire it. It is not enough to author one:
 * the editor's add menu was a fixed list, and nothing here can guess what choices an addon's
 * trigger offers. That is what this closes.
 *
 * <p>The same shape as the lock-category side on purpose — a trigger whose choices are ids takes
 * one call and no UI code.
 */
public interface TriggerEditor {

    /** The registered trigger type this authors. */
    String type();

    /** Lang key for the row in the add menu. */
    String labelLangKey();

    /** Lang key for the picker's search hint. */
    String searchPlaceholderLangKey();

    /** The ids a maintainer may choose from. Asked each time the picker opens. */
    Collection<String> candidates();

    /** Turns a chosen id into the trigger to store. */
    TriggerCondition create(String chosenId);

    /**
     * What this trigger holds, for the value column of a trigger list. The counterpart to
     * {@link #create(String)}: only the addon can read its own trigger back out.
     *
     * <p>Empty by default, which lists render as the bare type — the honest answer when nothing
     * here can say more.
     */
    default String valueText(TriggerCondition trigger) {
        return "";
    }

    /**
     * The free tier: a searchable list of ids, and a trigger built from whichever is picked.
     *
     * @param type          the type string this was registered under
     * @param labelLangKey  the add-menu row label
     * @param searchLangKey the picker's search hint
     * @param candidates    what may be chosen
     * @param factory       builds the trigger from the chosen id
     */
    static TriggerEditor ofIdList(String type, String labelLangKey, String searchLangKey,
                                  Supplier<Collection<String>> candidates,
                                  Function<String, TriggerCondition> factory) {
        return ofIdList(type, labelLangKey, searchLangKey, candidates, factory, t -> "");
    }

    /**
     * The same, plus the way back: the id a stored trigger was built from, so a list can show it
     * instead of repeating the type.
     *
     * @param reader reads the chosen id back out of a trigger of this type
     */
    static TriggerEditor ofIdList(String type, String labelLangKey, String searchLangKey,
                                  Supplier<Collection<String>> candidates,
                                  Function<String, TriggerCondition> factory,
                                  Function<TriggerCondition, String> reader) {
        return new TriggerEditor() {
            @Override public String type() { return type; }
            @Override public String labelLangKey() { return labelLangKey; }
            @Override public String searchPlaceholderLangKey() { return searchLangKey; }
            @Override public Collection<String> candidates() { return candidates.get(); }
            @Override public TriggerCondition create(String chosenId) { return factory.apply(chosenId); }
            @Override public String valueText(TriggerCondition trigger) { return reader.apply(trigger); }
        };
    }

    /** Convenience for callers that already hold the place-callback. */
    default Consumer<String> placingInto(Consumer<TriggerCondition> place) {
        return id -> place.accept(create(id));
    }
}
