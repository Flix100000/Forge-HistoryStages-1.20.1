package net.bananemdnsa.historystages.client.editor.tab;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.category.LockCategory;
import org.jetbrains.annotations.Nullable;

/**
 * A tab whose entries carry per-entry extras: NBT to match on, which actions the lock covers, and
 * name and tooltip overrides.
 *
 * <p>The editor has always kept those extras in maps keyed by the entry's <em>position</em> in the
 * list rather than by its id, because the same item can appear twice with different NBT. That
 * makes removal awkward: every key above the removed row has to shift down, and getting it wrong
 * silently attaches one entry's NBT to another. The screen used to do that shifting inline, once
 * per tab; here it happens in {@link #removeAt} for every rich tab at once.
 *
 * @param <T> the entry type the category stores — {@code ItemEntry} or {@code NamedLockEntry}
 */
public class RichEntryCategoryTab<T> extends AbstractCategoryTab {

    /** Splits a stored entry into id plus extras, and puts it back together again. */
    public interface EntryAdapter<T> {
        String id(T entry);

        @Nullable
        JsonObject nbt(T entry);

        @Nullable
        List<String> lockActions(T entry);

        @Nullable
        String nameText(T entry);

        @Nullable
        String tooltipText(T entry);

        T build(String id, @Nullable JsonObject nbt, @Nullable List<String> lockActions,
                @Nullable String nameText, @Nullable String tooltipText);
    }

    private final LockCategory<T> category;
    private final EntryAdapter<T> adapter;

    private final Map<Integer, JsonObject> nbt = new HashMap<>();
    private final Map<Integer, List<String>> lockActions = new HashMap<>();
    private final Map<Integer, String> nameText = new HashMap<>();
    private final Map<Integer, String> tooltipText = new HashMap<>();

    public RichEntryCategoryTab(LockCategory<T> category,
                                boolean availableForIndividualStages,
                                PickerFactory pickerFactory,
                                Runnable onChanged,
                                EntryAdapter<T> adapter) {
        super(category, availableForIndividualStages, pickerFactory, onChanged);
        this.category = category;
        this.adapter = adapter;
    }

    @Override
    public void load(StageEntry stage) {
        entries().clear();
        nbt.clear();
        lockActions.clear();
        nameText.clear();
        tooltipText.clear();

        int index = 0;
        for (T entry : category.read(stage)) {
            entries().add(adapter.id(entry));
            put(nbt, index, adapter.nbt(entry));
            put(lockActions, index, adapter.lockActions(entry));
            put(nameText, index, adapter.nameText(entry));
            put(tooltipText, index, adapter.tooltipText(entry));
            index++;
        }
    }

    @Override
    public void store(StageEntry stage) {
        List<T> rebuilt = new ArrayList<>(entries().size());
        for (int i = 0; i < entries().size(); i++) {
            rebuilt.add(adapter.build(entries().get(i), nbt.get(i), lockActions.get(i),
                    nameText.get(i), tooltipText.get(i)));
        }
        category.write(stage, rebuilt);
    }

    @Override
    public void removeAt(int index) {
        if (index < 0 || index >= entries().size()) return;
        entries().remove(index);
        shift(nbt, index);
        shift(lockActions, index);
        shift(nameText, index);
        shift(tooltipText, index);
    }

    public Map<Integer, JsonObject> nbtByIndex() {
        return nbt;
    }

    public Map<Integer, List<String>> lockActionsByIndex() {
        return lockActions;
    }

    public Map<Integer, String> nameTextByIndex() {
        return nameText;
    }

    public Map<Integer, String> tooltipTextByIndex() {
        return tooltipText;
    }

    /** Drops every entry belonging to one mod, extras included, without leaving keys behind. */
    public void removeAllFromMod(String prefix) {
        for (int i = entries().size() - 1; i >= 0; i--) {
            if (entries().get(i).startsWith(prefix)) removeAt(i);
        }
    }

    private static <V> void put(Map<Integer, V> target, int index, @Nullable V value) {
        if (value == null) return;
        if (value instanceof JsonObject json && json.size() == 0) return;
        if (value instanceof List<?> list && list.isEmpty()) return;
        if (value instanceof String text && text.isEmpty()) return;
        target.put(index, value);
    }

    /** Removes the key at {@code index} and pulls every higher key down by one. */
    private static <V> void shift(Map<Integer, V> target, int index) {
        target.remove(index);
        Map<Integer, V> shifted = new HashMap<>();
        for (Map.Entry<Integer, V> e : target.entrySet()) {
            int key = e.getKey();
            shifted.put(key > index ? key - 1 : key, e.getValue());
        }
        target.clear();
        target.putAll(shifted);
    }
}
