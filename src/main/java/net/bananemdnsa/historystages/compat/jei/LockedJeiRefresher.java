package net.bananemdnsa.historystages.compat.jei;

import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Stateful applier that holds the current "hidden in JEI" set and applies diffs
 * against a {@link JeiOps} sink. The sink is abstracted so the logic stays
 * decoupled from JEI's runtime types.
 *
 * Single-threaded usage assumed: all entry points run on the client render thread.
 * Methods are {@code synchronized} as defensive belt-and-suspenders since stage
 * sync packets are decoded on the network thread and re-posted via {@code mc.execute}.
 */
public final class LockedJeiRefresher {

    /** Minimal abstraction over the JEI runtime calls we use. */
    public interface JeiOps {
        void removeItems(Set<ItemStack> items);
        void addItems(Set<ItemStack> items);
    }

    private final JeiOps ops;
    private Set<ItemStack> currentlyHiddenItems = Set.of();

    public LockedJeiRefresher(JeiOps ops) {
        this.ops = ops;
    }

    /**
     * Initial application: full compute, single remove call if enabled.
     */
    public synchronized void applyInitial(boolean hideItemsEnabled,
                                          Supplier<Set<ItemStack>> lockedItemsSupplier) {
        if (!hideItemsEnabled) {
            currentlyHiddenItems = Set.of();
            return;
        }
        Set<ItemStack> locked = new HashSet<>(lockedItemsSupplier.get());
        if (!locked.isEmpty()) ops.removeItems(locked);
        currentlyHiddenItems = locked;
    }

    /**
     * Recompute the locked set, diff against {@link #currentlyHiddenItems},
     * and call {@link JeiOps#addItems} / {@link JeiOps#removeItems} only for
     * the delta. Empty delta → no API call.
     */
    public synchronized void applyDiff(boolean hideItemsEnabled,
                                       Supplier<Set<ItemStack>> lockedItemsSupplier) {
        Set<ItemStack> newLocked = hideItemsEnabled
                ? new HashSet<>(lockedItemsSupplier.get())
                : Set.of();

        Set<ItemStack> toAdd = new HashSet<>(currentlyHiddenItems);
        toAdd.removeAll(newLocked);

        Set<ItemStack> toRemove = new HashSet<>(newLocked);
        toRemove.removeAll(currentlyHiddenItems);

        if (!toAdd.isEmpty())    ops.addItems(toAdd);
        if (!toRemove.isEmpty()) ops.removeItems(toRemove);

        currentlyHiddenItems = newLocked;
    }

    /** Snapshot of the currently-hidden set (defensive copy). */
    public synchronized Set<ItemStack> currentlyHiddenItems() {
        return Set.copyOf(currentlyHiddenItems);
    }
}
