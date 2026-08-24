package net.bananemdnsa.historystages.api.lock;

import net.bananemdnsa.historystages.api.lock.CategoryMatcher;
import net.bananemdnsa.historystages.api.lock.CategoryStorage;

import net.bananemdnsa.historystages.api.lock.LockCategory;

import java.util.List;
import java.util.Objects;

import com.google.gson.JsonElement;
import net.bananemdnsa.historystages.data.StageEntry;

/**
 * A {@link LockCategory} for entries owned by another mod.
 *
 * <p>Built-in categories read and write typed fields declared on {@link StageEntry}. An addon
 * category cannot do that — {@code StageEntry} does not know its entry type — so it stores
 * through the {@code addons} block instead: a map of category id to raw {@link JsonElement} that
 * {@code StageEntry} keeps unparsed for exactly this reason. A stage file edited or resaved by an
 * instance that does not have the owning addon installed still round-trips that block untouched,
 * so the addon's data survives even when nothing on the running instance can interpret it. See
 * {@link StageEntry#addonEntries(String)} for the full rationale.
 *
 * <p>Turning that raw JSON into {@code List<T>} and back is the one thing each addon category
 * needs to supply for itself; that is {@link CategoryStorage}. Everything else — the empty list
 * on read, clearing the slot instead of storing {@code []} on an empty write, opting out of
 * dual-phase detection — is handled here so every addon category behaves the same way.
 *
 * @param <T> the category's entry type
 */
public final class AddonLockCategory<T> implements LockCategory<T> {

    /** No addon may register under this namespace; it is reserved for the eleven built-ins. */
    private static final String RESERVED_NAMESPACE = "historystages";

    private final String id;
    private final String tabLangKey;
    private final String tooltipLangKey;
    private final CategoryStorage<T> storage;
    private final java.util.Set<net.bananemdnsa.historystages.api.stage.StageScope> supportedScopes;
    private final Class<?> subjectType;
    private final CategoryMatcher<T, Object> matcher;

    private AddonLockCategory(Builder<T> builder) {
        this.id = builder.id;
        this.tabLangKey = builder.tabLangKey;
        this.tooltipLangKey = builder.tooltipLangKey;
        this.storage = builder.storage;
        this.supportedScopes = builder.supportedScopes;
        this.subjectType = builder.subjectType;
        this.matcher = builder.matcher;
    }

    public static <T> Builder<T> builder(String id) {
        return new Builder<>(id);
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public java.util.Set<net.bananemdnsa.historystages.api.stage.StageScope> supportedScopes() {
        return supportedScopes;
    }

    @Override
    public String tabLangKey() {
        return tabLangKey;
    }

    @Override
    public String tooltipLangKey() {
        return tooltipLangKey;
    }

    @Override
    public List<T> read(StageEntry stage) {
        return storage.read(stage.addonEntries(id));
    }

    @Override
    public void write(StageEntry stage, List<T> entries) {
        // An emptied category leaves no stub in the file — see StageEntry#setAddonEntries.
        stage.setAddonEntries(id, entries.isEmpty() ? null : storage.write(entries));
    }

    // globalDualPhaseIds, individualDualPhaseIds and dualPhaseLabel keep LockCategory's opt-out
    // defaults. Addon participation in dual-phase detection is a later question.

    @Override
    public boolean matches(T entry, Object subject) {
        if (matcher == null || subject == null) return false;
        if (!subjectType.isInstance(subject)) {
            throw new IllegalArgumentException("Category '" + id + "' was asked about a "
                    + subject.getClass().getName() + ", but it matches "
                    + subjectType.getName() + ".");
        }
        return matcher.matches(entry, subject);
    }

    public static final class Builder<T> {
        private java.util.Set<net.bananemdnsa.historystages.api.stage.StageScope> supportedScopes =
                java.util.EnumSet.allOf(net.bananemdnsa.historystages.api.stage.StageScope.class);
        private final String id;
        private String tabLangKey;
        private String tooltipLangKey;
        private CategoryStorage<T> storage;
        private Class<?> subjectType;
        private CategoryMatcher<T, Object> matcher;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder<T> tabLangKey(String tabLangKey) {
            this.tabLangKey = tabLangKey;
            return this;
        }

        public Builder<T> tooltipLangKey(String tooltipLangKey) {
            this.tooltipLangKey = tooltipLangKey;
            return this;
        }

        /**
         * Which stage scopes this category means anything in. Both unless said otherwise —
         * pass only {@code StageScope.GLOBAL} for something that cannot sensibly be gated per
         * player, the way recipes and spawn locks cannot.
         */
        public Builder<T> supportedScopes(net.bananemdnsa.historystages.api.stage.StageScope... scopes) {
            this.supportedScopes = java.util.EnumSet.noneOf(
                    net.bananemdnsa.historystages.api.stage.StageScope.class);
            java.util.Collections.addAll(this.supportedScopes, scopes);
            if (this.supportedScopes.isEmpty()) {
                throw new IllegalArgumentException("A category that supports no scope can never gate anything.");
            }
            return this;
        }

        public Builder<T> storage(CategoryStorage<T> storage) {
            this.storage = storage;
            return this;
        }

        /**
         * Teaches the category how to recognise its own subjects at runtime. Optional: a category
         * without one can still store entries and appear in the editor, it just never gates
         * anything.
         *
         * @param subjectType the runtime type this category is asked about; checked before the
         *                    matcher is called so a wrong-type query fails with a clear message
         */
        @SuppressWarnings("unchecked")
        public <S> Builder<T> matcher(Class<S> subjectType, CategoryMatcher<T, S> matcher) {
            this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
            this.matcher = (CategoryMatcher<T, Object>) Objects.requireNonNull(matcher, "matcher");
            return this;
        }

        public AddonLockCategory<T> build() {
            int colon = id.indexOf(':');
            if (colon <= 0 || colon == id.length() - 1) {
                throw new IllegalArgumentException(
                        "Addon lock category id '" + id + "' must have a namespace, e.g. 'mymod:" + id + "'.");
            }
            String namespace = id.substring(0, colon);
            if (RESERVED_NAMESPACE.equals(namespace)) {
                throw new IllegalArgumentException(
                        "Addon lock category id '" + id + "' uses the '" + RESERVED_NAMESPACE
                                + "' namespace, which is reserved for built-in categories.");
            }
            if (tabLangKey == null) {
                throw new IllegalStateException("Addon lock category '" + id + "' has no tabLangKey.");
            }
            if (tooltipLangKey == null) {
                throw new IllegalStateException("Addon lock category '" + id + "' has no tooltipLangKey.");
            }
            if (storage == null) {
                throw new IllegalStateException("Addon lock category '" + id + "' has no storage.");
            }
            return new AddonLockCategory<>(this);
        }
    }
}
