package net.bananemdnsa.historystages.data.settings;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.engine.StageScope;

/**
 * A group of stage settings an addon declares, identified by a namespaced id.
 *
 * <p>Mirrors {@link net.bananemdnsa.historystages.data.lock.category.AddonLockCategory}: the
 * builder validation and the reserved namespace are the same contract, because both types round-
 * trip addon data through a raw {@link com.google.gson.JsonElement} block on {@link StageEntry}
 * so a stage file survives being loaded and saved without the owning addon installed. See {@link
 * StageEntry#addonSettings(String)} for the full rationale.
 */
public final class StageSettingsGroup {

    /** No addon may register under this namespace; it is reserved for this mod's own groups. */
    private static final String RESERVED_NAMESPACE = "historystages";

    private final String id;
    private final String titleLangKey;
    private final Set<StageScope> supportedScopes;
    private final List<Setting<?>> fields;

    private StageSettingsGroup(Builder builder) {
        this.id = builder.id;
        this.titleLangKey = builder.titleLangKey;
        this.supportedScopes = Set.copyOf(builder.supportedScopes);
        this.fields = List.copyOf(builder.fields.values());
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    public String id() {
        return id;
    }

    public String titleLangKey() {
        return titleLangKey;
    }

    public Set<StageScope> supportedScopes() {
        return supportedScopes;
    }

    /** This group's fields, in declaration order. */
    public List<Setting<?>> fields() {
        return fields;
    }

    /**
     * Reads this group's values for one stage scope. Only fields whose {@link
     * Setting#supportedScopes()} contains {@code scope} are handed to {@link SettingsValues#read};
     * an out-of-scope field's stored JSON key therefore becomes an unknown key that {@link
     * SettingsValues} preserves verbatim, rather than something this method has to special-case.
     */
    public SettingsValues load(StageEntry stage, StageScope scope) {
        List<Setting<?>> inScope = fields.stream()
                .filter(field -> field.supportedScopes().contains(scope))
                .toList();
        return SettingsValues.read(inScope, stage.addonSettings(id));
    }

    /**
     * Stores {@code values} into the stage's settings block for this group. Always writes —
     * {@link SettingsValues#write()} returning {@code null} (everything at its default) removes
     * the block, which is the intended behaviour, not a no-op.
     */
    public void store(StageEntry stage, SettingsValues values) {
        stage.setAddonSettings(id, values.write());
    }

    /**
     * Convenience overload for the editor's snapshot loop, which holds one {@link SettingsValues}
     * per group id it displayed. When {@code byGroupId} has no entry for this group, this method
     * does nothing at all — a group the editor never showed (wrong scope, for instance) must not
     * have its stored block wiped just because it was absent from the snapshot.
     */
    public void store(StageEntry stage, Map<String, SettingsValues> byGroupId) {
        SettingsValues values = byGroupId.get(id);
        if (values == null) return;
        store(stage, values);
    }

    public static final class Builder {
        private final String id;
        private final Map<String, Setting<?>> fields = new LinkedHashMap<>();
        private Set<StageScope> supportedScopes = EnumSet.allOf(StageScope.class);
        private String titleLangKey;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder titleLangKey(String titleLangKey) {
            this.titleLangKey = titleLangKey;
            return this;
        }

        /**
         * Which stage scopes this group applies to. Both unless said otherwise — pass only
         * {@code StageScope.GLOBAL} for settings that cannot sensibly vary per player.
         */
        public Builder supportedScopes(StageScope... scopes) {
            this.supportedScopes = EnumSet.noneOf(StageScope.class);
            java.util.Collections.addAll(this.supportedScopes, scopes);
            if (this.supportedScopes.isEmpty()) {
                throw new IllegalArgumentException("A group that supports no scope can never be shown.");
            }
            return this;
        }

        public Builder field(Setting<?> field) {
            Objects.requireNonNull(field, "field");
            if (fields.containsKey(field.key())) {
                throw new IllegalArgumentException(
                        "Settings group '" + id + "' already declares a field '" + field.key() + "'.");
            }
            fields.put(field.key(), field);
            return this;
        }

        public StageSettingsGroup build() {
            int colon = id.indexOf(':');
            if (colon <= 0 || colon == id.length() - 1) {
                throw new IllegalArgumentException(
                        "Settings group id '" + id + "' must have a namespace, e.g. 'mymod:" + id + "'.");
            }
            String namespace = id.substring(0, colon);
            if (RESERVED_NAMESPACE.equals(namespace)) {
                throw new IllegalArgumentException(
                        "Settings group id '" + id + "' uses the '" + RESERVED_NAMESPACE
                                + "' namespace, which is reserved for this mod's own groups.");
            }
            if (titleLangKey == null) {
                throw new IllegalStateException("Settings group '" + id + "' has no titleLangKey.");
            }
            if (fields.isEmpty()) {
                throw new IllegalStateException("Settings group '" + id + "' has no fields.");
            }
            return new StageSettingsGroup(this);
        }
    }
}
