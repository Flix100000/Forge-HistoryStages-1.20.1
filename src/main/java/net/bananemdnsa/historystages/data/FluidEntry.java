package net.bananemdnsa.historystages.data;

import net.bananemdnsa.historystages.data.display.TextOverrideHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * One gated fluid: a registry id, optionally narrowed to some of the actions its category
 * offers, optionally carrying the text a hidden container should show instead.
 *
 * <p>{@link ItemEntry} without the {@code nbt} field, and deliberately so. A fluid criterion
 * would need a live {@code FluidStack} to be settled, and only one of the four paths that ask
 * about a fluid has one — the container item. The fluid block in the world carries a
 * {@code FluidState}, which has no components at all, and a recipe viewer entry is a bare id.
 * A criterion would therefore do nothing on exactly the two surfaces a pack author checks
 * first, with no way to tell why.
 *
 * <p>The text overrides reach the container item and not the fluid itself, because
 * {@code HiddenDisplayResolver} works on item ids. A foreign tank GUI keeps showing the real
 * name. That limit is stated in the editor field's tooltip rather than left to be discovered.
 */
public class FluidEntry implements TextOverrideHolder {

    private final String id;

    /** null = every action this category offers is locked. */
    private final List<String> lockActions;

    // Per-fluid text overrides for the stage's hidden-display REPLACE mode.
    // null = no override → fall back to the stage default text.
    private final String nameTextOverride;
    private final String tooltipTextOverride;

    public FluidEntry(String id) {
        this(id, null, null, null);
    }

    public FluidEntry(String id, List<String> lockActions,
                      String nameTextOverride, String tooltipTextOverride) {
        this.id = id;
        this.lockActions = (lockActions != null && !lockActions.isEmpty())
                ? new ArrayList<>(lockActions) : null;
        this.nameTextOverride = emptyToNull(nameTextOverride);
        this.tooltipTextOverride = emptyToNull(tooltipTextOverride);
    }

    private static String emptyToNull(String s) {
        return (s != null && !s.isEmpty()) ? s : null;
    }

    public String getId() { return id; }

    /** Returns null if all actions are locked, otherwise the explicit list of locked actions. */
    public List<String> getLockActions() { return lockActions; }

    public boolean hasLockActions() { return lockActions != null && !lockActions.isEmpty(); }

    /** Per-fluid REPLACE name override, or null to use the stage default. */
    public String getNameTextOverride() { return nameTextOverride; }

    /** Per-fluid REPLACE tooltip override, or null to use the stage default. */
    public String getTooltipTextOverride() { return tooltipTextOverride; }

    public boolean hasNameTextOverride() { return nameTextOverride != null; }

    public boolean hasTooltipTextOverride() { return tooltipTextOverride != null; }

    public FluidEntry copy() {
        return new FluidEntry(
                id,
                lockActions != null ? new ArrayList<>(lockActions) : null,
                nameTextOverride,
                tooltipTextOverride
        );
    }
}
