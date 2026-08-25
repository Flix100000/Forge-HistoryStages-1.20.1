package net.bananemdnsa.historystages.api.config;

import net.bananemdnsa.historystages.api.config.AddonConfigField;
import net.bananemdnsa.historystages.api.config.ConfigSide;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A section of config fields an addon declares, identified by a namespaced id.
 *
 * <p>Mirrors {@link net.bananemdnsa.historystages.api.settings.StageSettingsGroup} in shape, but
 * for a different kind of value: a settings group's fields belong to a stage and HistoryStages
 * stores them, while a config section's fields belong to the addon itself — see {@link
 * AddonConfigField} and spec §2 for why this class stores nothing and only holds the addon's read
 * and write callbacks.
 */
public final class AddonConfigSection {

    /** No addon may register under this namespace; it is reserved for this mod's own sections. */
    private static final String RESERVED_NAMESPACE = "historystages";

    private final String id;
    private final String titleLangKey;
    private final ConfigSide side;
    private final List<AddonConfigField> fields;

    private AddonConfigSection(Builder builder) {
        this.id = builder.id;
        this.titleLangKey = builder.titleLangKey;
        this.side = builder.side;
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

    public ConfigSide side() {
        return side;
    }

    /** This section's fields, in declaration order. */
    public List<AddonConfigField> fields() {
        return fields;
    }

    public static final class Builder {
        private final String id;
        private final Map<String, AddonConfigField> fields = new LinkedHashMap<>();
        private String titleLangKey;
        private ConfigSide side;

        private Builder(String id) {
            this.id = Objects.requireNonNull(id, "id");
        }

        public Builder titleLangKey(String titleLangKey) {
            this.titleLangKey = titleLangKey;
            return this;
        }

        public Builder side(ConfigSide side) {
            this.side = side;
            return this;
        }

        public Builder field(AddonConfigField field) {
            Objects.requireNonNull(field, "field");
            if (fields.containsKey(field.key())) {
                throw new IllegalArgumentException(
                        "Config section '" + id + "' already declares a field '" + field.key() + "'.");
            }
            fields.put(field.key(), field);
            return this;
        }

        public AddonConfigSection build() {
            int colon = id.indexOf(':');
            if (colon <= 0 || colon == id.length() - 1) {
                throw new IllegalArgumentException(
                        "Config section id '" + id + "' must have a namespace, e.g. 'mymod:" + id + "'.");
            }
            String namespace = id.substring(0, colon);
            if (RESERVED_NAMESPACE.equals(namespace)) {
                throw new IllegalArgumentException(
                        "Config section id '" + id + "' uses the '" + RESERVED_NAMESPACE
                                + "' namespace, which is reserved for this mod's own sections.");
            }
            if (titleLangKey == null) {
                throw new IllegalStateException("Config section '" + id + "' has no titleLangKey.");
            }
            if (side == null) {
                throw new IllegalStateException("Config section '" + id + "' has no side; declare "
                        + "ConfigSide.CLIENT if its fields are written on the client, or "
                        + "ConfigSide.COMMON if they should be shipped to the server and written there.");
            }
            if (fields.isEmpty()) {
                throw new IllegalStateException("Config section '" + id + "' has no fields.");
            }
            return new AddonConfigSection(this);
        }
    }
}
