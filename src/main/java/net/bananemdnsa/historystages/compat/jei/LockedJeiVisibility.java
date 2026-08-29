package net.bananemdnsa.historystages.compat.jei;

import net.bananemdnsa.historystages.Config.Visual.MultiStagePolicy;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pure logic for deciding which JEI ingredients/recipes are locked.
 * No reference to IJeiRuntime — keeps this decoupled and easy to reason about.
 *
 * Matching delegates to {@link StageLockHelper} so JEI hiding stays consistent
 * with the lock overlay decorator and the rest of the lock system.
 */
public final class LockedJeiVisibility {

    private LockedJeiVisibility() {}

    /**
     * Returns the subset of {@code allStacks} that is currently locked for the
     * local player, using the configured multi-stage policy.
     *
     * Iterating real JEI ingredients (rather than expanding tags ourselves)
     * automatically covers NBT variants — every Tipped Arrow potion variant
     * appears as its own ingredient. This is the fix for Issue #16.
     */
    public static Set<ItemStack> computeLockedItems(Iterable<ItemStack> allStacks,
                                                     MultiStagePolicy policy) {
        Predicate<ItemStack> isLocked = switch (policy) {
            case STRICT  -> StageLockHelper::isItemLockedForClient;
            case LENIENT -> StageLockHelper::isItemLockedForClientLenient;
        };

        Set<ItemStack> locked = new HashSet<>();
        for (ItemStack stack : allStacks) {
            if (stack == null || stack.isEmpty()) continue;
            if (isLocked.test(stack)) locked.add(stack);
        }
        return locked;
    }

    /**
     * Filters recipes down to those whose OUTPUT stack matches an item in
     * {@code lockedItems}. Output extraction is caller-supplied so this stays
     * decoupled from JEI's recipe model. INPUT slots are intentionally ignored —
     * we only hide recipes whose result is locked.
     *
     * Item-equality is by {@link Item} (not full stack equality), so NBT
     * variants of a locked item are also matched as locked outputs.
     */
    public static <R> List<R> filterRecipesWithLockedOutput(
            Iterable<R> recipes,
            Function<R, List<ItemStack>> outputExtractor,
            Set<ItemStack> lockedItems) {

        if (lockedItems.isEmpty()) return List.of();

        Set<Item> lockedItemsByItem = new HashSet<>();
        for (ItemStack s : lockedItems) lockedItemsByItem.add(s.getItem());

        List<R> result = new ArrayList<>();
        for (R recipe : recipes) {
            List<ItemStack> outputs = outputExtractor.apply(recipe);
            if (outputs == null) continue;
            for (ItemStack out : outputs) {
                if (out == null || out.isEmpty()) continue;
                if (lockedItemsByItem.contains(out.getItem())) {
                    result.add(recipe);
                    break;
                }
            }
        }
        return result;
    }
}
