package net.bananemdnsa.historystages.api.dependency;

import net.bananemdnsa.historystages.api.dependency.RequirementResult;
import net.bananemdnsa.historystages.api.dependency.RequirementContext;
import net.bananemdnsa.historystages.api.dependency.RequirementDisplay;
import net.bananemdnsa.historystages.api.dependency.RequirementOutcome;
import net.bananemdnsa.historystages.api.dependency.RequirementStorage;

import net.bananemdnsa.historystages.api.dependency.Requirement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.api.stage.StageScope;

/**
 * A {@link Requirement} owned by another mod.
 *
 * <p>Built-in requirements are views over typed fields on {@link DependencyGroup}. An addon
 * requirement cannot be — the group has no field for a relic — so it stores through the group's
 * {@code addons} block instead: a map of requirement id to raw JSON that the group keeps unparsed
 * for exactly this reason. A stage file edited or resaved by an instance without the owning addon
 * installed round-trips that block untouched, so the addon's data survives even when nothing on
 * the running instance can interpret it.
 *
 * <p>Turning that raw JSON into {@code List<T>} and back is the one thing each addon supplies for
 * itself; that is {@link RequirementStorage}. Everything else — the empty list on read, clearing
 * the slot instead of storing {@code []}, and assembling the {@code EntryResult} — is handled
 * here so every addon requirement behaves the same way.
 *
 * @param <T> the requirement's entry type
 */
public final class AddonRequirement<T> implements Requirement {

    /** No addon may register under this namespace; it is reserved for the eight built-ins. */
    private static final String RESERVED_NAMESPACE = "historystages";

    /**
     * Turns one stored entry into its outcome. The addon's own logic lives behind this.
     *
     * <p>Returning null skips the entry, which is the right answer when the addon can tell the
     * entry is meaningless — an id that no longer exists, for instance.
     */
    @FunctionalInterface
    public interface Evaluator<T> {
        RequirementOutcome evaluate(T entry, RequirementContext ctx);
    }

    private final String id;
    private final String tabLangKey;
    private final String tooltipLangKey;
    private final String sectionLangKey;
    private final RequirementStorage<T> storage;
    private final Evaluator<T> evaluator;
    private final Set<StageScope> supportedScopes;
    private final RequirementDisplay.Kind displayKind;

    private AddonRequirement(Builder<T> builder) {
        this.id = builder.id;
        this.tabLangKey = builder.tabLangKey;
        this.tooltipLangKey = builder.tooltipLangKey;
        this.sectionLangKey = builder.sectionLangKey;
        this.storage = builder.storage;
        this.evaluator = builder.evaluator;
        this.supportedScopes = builder.supportedScopes;
        this.displayKind = builder.displayKind;
    }

    public static <T> Builder<T> builder(String id) {
        return new Builder<>(id);
    }

    @Override
    public String id() {
        return id;
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
    public String sectionLangKey() {
        return sectionLangKey;
    }

    @Override
    public Set<StageScope> supportedScopes() {
        return supportedScopes;
    }

    @Override
    public RequirementDisplay.Kind displayKind() {
        return displayKind;
    }

    @Override
    public boolean declaredIn(DependencyGroup group) {
        return group.addonEntries(id) != null;
    }

    /** This requirement's entries on the group. Never null; empty when the group declares none. */
    public List<T> read(DependencyGroup group) {
        return storage.read(group.addonEntries(id));
    }

    /** Replaces this requirement's entries. An emptied requirement leaves no stub in the file. */
    public void write(DependencyGroup group, List<T> entries) {
        group.setAddonEntries(id, entries.isEmpty() ? null : storage.write(entries));
    }

    @Override
    public List<RequirementResult.EntryResult> evaluate(DependencyGroup group, RequirementContext ctx) {
        List<RequirementResult.EntryResult> results = new ArrayList<>();
        for (T entry : read(group)) {
            RequirementOutcome outcome = evaluator.evaluate(entry, ctx);
            if (outcome == null) continue;
            // The full overload, with canDeposit false: an addon requirement is evaluated live and
            // has nothing to deposit into. originalRequired 0 means "no booster reduction applied",
            // which is how the UI reads it. Assembled here so an addon never picks an overload.
            results.add(new RequirementResult.EntryResult(id, outcome.id(), outcome.description(),
                    outcome.fulfilled(), outcome.current(), outcome.required(), 0, false));
        }
        return results;
    }

    public static final class Builder<T> {

        private final String id;
        private String tabLangKey;
        private String tooltipLangKey;
        private String sectionLangKey;
        private RequirementStorage<T> storage;
        private Evaluator<T> evaluator;
        private Set<StageScope> supportedScopes = EnumSet.allOf(StageScope.class);
        private RequirementDisplay.Kind displayKind = RequirementDisplay.Kind.BINARY;

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

        public Builder<T> sectionLangKey(String sectionLangKey) {
            this.sectionLangKey = sectionLangKey;
            return this;
        }

        public Builder<T> storage(RequirementStorage<T> storage) {
            this.storage = storage;
            return this;
        }

        /** The addon's own logic: given one stored entry, is it satisfied and how far along? */
        public Builder<T> evaluator(Evaluator<T> evaluator) {
            this.evaluator = evaluator;
            return this;
        }

        /**
         * Which stage scopes this requirement means anything in. Both unless said otherwise —
         * pass only {@code StageScope.INDIVIDUAL} for something measured off a single player, the
         * way kills and advancements are.
         */
        public Builder<T> supportedScopes(StageScope... scopes) {
            this.supportedScopes = EnumSet.noneOf(StageScope.class);
            Collections.addAll(this.supportedScopes, scopes);
            if (this.supportedScopes.isEmpty()) {
                throw new IllegalArgumentException(
                        "A requirement that supports no scope can never be demanded of anything.");
            }
            return this;
        }

        /** How the graph may present this requirement. {@code BINARY} unless said otherwise. */
        public Builder<T> displayKind(RequirementDisplay.Kind displayKind) {
            this.displayKind = displayKind;
            return this;
        }

        public AddonRequirement<T> build() {
            int colon = id.indexOf(':');
            if (colon <= 0 || colon == id.length() - 1) {
                throw new IllegalArgumentException(
                        "Addon requirement id '" + id + "' must have a namespace, e.g. 'mymod:" + id + "'.");
            }
            String namespace = id.substring(0, colon);
            if (RESERVED_NAMESPACE.equals(namespace)) {
                throw new IllegalArgumentException(
                        "Addon requirement id '" + id + "' uses the '" + RESERVED_NAMESPACE
                                + "' namespace, which is reserved for built-in requirements.");
            }
            if (tabLangKey == null) {
                throw new IllegalStateException("Addon requirement '" + id + "' has no tabLangKey.");
            }
            if (tooltipLangKey == null) {
                throw new IllegalStateException("Addon requirement '" + id + "' has no tooltipLangKey.");
            }
            if (sectionLangKey == null) {
                throw new IllegalStateException("Addon requirement '" + id + "' has no sectionLangKey.");
            }
            if (storage == null) {
                throw new IllegalStateException("Addon requirement '" + id + "' has no storage.");
            }
            if (evaluator == null) {
                throw new IllegalStateException("Addon requirement '" + id + "' has no evaluator.");
            }
            return new AddonRequirement<>(this);
        }
    }
}
