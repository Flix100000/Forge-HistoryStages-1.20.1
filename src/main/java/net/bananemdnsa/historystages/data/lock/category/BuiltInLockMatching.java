package net.bananemdnsa.historystages.data.lock.category;

import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;
import net.bananemdnsa.historystages.data.lock.engine.LockSubjects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The match rules that have to touch Minecraft: NBT comparison, tag membership, mod exceptions.
 *
 * <p>They live here rather than in {@link BuiltInLockCategories} on purpose. Eleven test classes
 * load {@code LockCategories} and therefore that class. A Minecraft type assigned to a variable
 * of another Minecraft type inside it — and a {@code new X(...)} passed straight into a parameter
 * of a supertype counts as one — triggers the bytecode verifier's assignability check the first
 * time the class is touched. The verifier loads both types, neither is on the test classpath, and
 * every one of those eleven test classes dies with a {@code NoClassDefFoundError}. That has
 * happened four times in this project already.
 *
 * <p>A method <em>call</em> across a class boundary is resolved lazily and is safe, so the
 * categories call in here instead. Same reason {@code ScoreboardLookup} and
 * {@code EntryActionScreens} exist; this time it is preventive rather than a repair.
 */
public final class BuiltInLockMatching {

    private BuiltInLockMatching() {}

    /**
     * An item entry gates a subject when the ids match and, where the entry carries an NBT
     * criterion, the stack satisfies it.
     *
     * <p>No stack means the criterion cannot be confirmed, which counts as "does not match" —
     * the answer the stackless paths got before this method existed.
     */
    public static boolean itemEntryMatches(ItemEntry entry, LockSubjects.ItemSubject subject) {
        if (!entry.getId().equals(subject.itemId())) return false;
        if (!entry.hasNbt()) return true;
        return subject.stack() != null && NbtMatcher.matches(subject.stack(), entry.getNbt());
    }

    /**
     * A fluid entry gates the subject when the stack is carrying that fluid.
     *
     * <p>No criterion branch, unlike the item and tag forms: a fluid entry has no NBT to match
     * on, because only one of the four paths that ask about a fluid could ever supply the
     * {@code FluidStack} a criterion would need.
     */
    public static boolean fluidEntryMatches(net.bananemdnsa.historystages.data.FluidEntry entry,
                                            LockSubjects.ItemSubject subject) {
        return subject.fluidId() != null && entry.getId().equals(subject.fluidId());
    }

    /**
     * The criterion half of a trade entry: does this offer's stack satisfy it?
     *
     * <p>Only this half lives here. Which item and which side of the offer an entry gates is
     * decided by {@code TradeOfferEntry.gates}, which names no Minecraft type and is therefore
     * provable by a unit test. Keeping the split means a test that uses no criterion never
     * reaches this class at all — and a test that touches this class dies, because eleven test
     * classes load {@code LockCategories} and the verifier would drag {@code ItemStack} in with
     * it.
     *
     * <p>No stack means the criterion cannot be confirmed, which counts as "does not match" —
     * the same answer every other stackless path gets.
     */
    public static boolean tradeCriterionMatches(com.google.gson.JsonObject criterion, Object stack) {
        return stack instanceof ItemStack itemStack && NbtMatcher.matches(itemStack, criterion);
    }

    /** The same rule for a tag entry, preceded by the tag-membership test itself. */
    public static boolean tagEntryMatches(NamedLockEntry entry, LockSubjects.ItemSubject subject) {
        return tagEntryMatches(entry, subject.stack(), subject.item());
    }

    /**
     * The stack-and-item form, for the display and tooltip code that has those two to hand but no
     * reason to build a whole subject.
     *
     * <p>A null item cannot be in any tag. A null stack cannot confirm an NBT criterion, so an
     * entry that carries one answers "no" there rather than guessing.
     */
    public static boolean tagEntryMatches(NamedLockEntry entry, @Nullable ItemStack stack,
                                          @Nullable Item item) {
        if (item == null) return false;
        if (!item.builtInRegistryHolder().is(entry.getItemTagKey())) return false;
        if (!entry.hasNbt()) return true;
        return stack != null && NbtMatcher.matches(stack, entry.getNbt());
    }

    /**
     * A mod entry gates the subject unless this stage excepts that exact item.
     *
     * <p>Takes the stage, not just the entry: the exception list is stage-level, which is why the
     * mods category answers through {@link LockCategory#gates} instead of the entry loop.
     */
    public static boolean modEntryMatches(NamedLockEntry entry, StageEntry stage,
                                          LockSubjects.ItemSubject subject) {
        if (!entry.getId().equals(subject.modId())) return false;
        return !stage.isModExcepted(subject.itemId(), subject.stack());
    }
}
