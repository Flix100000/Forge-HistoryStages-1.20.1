package net.bananemdnsa.historystages.data.lock.engine;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The subjects the built-in lock categories are asked about.
 *
 * <p>{@link net.bananemdnsa.historystages.data.lock.category.LockCategory#matches} takes an
 * {@code Object}, because a category cannot constrain what it is asked about — an addon asks
 * about its own type. Three of the built-in questions need more than a bare id, and these records
 * are that "more". They are built in the engine and nowhere else.
 *
 * <p>Two of them name Minecraft types in their canonical constructors, which makes them types no
 * unit test can construct. That is fine while they are internal. It is <em>not</em> fine to freeze
 * them into the public API in Phase 9 without a Minecraft-free factory beside them — the same
 * trap {@code EntryActionContext.dataOnly} exists for. Noted in the Phase 8 design, §6.1.
 */
public final class LockSubjects {

    private LockSubjects() {}

    /**
     * An item being asked about.
     *
     * <p>Both the ids and the stack are carried because the stackless paths are real: a recipe
     * result or a tooltip may only know an item id. An entry with an NBT criterion cannot confirm
     * a match without a stack and therefore answers "no" there — which is what the code did
     * before this record existed.
     */
    public record ItemSubject(String itemId, String modId,
                              @Nullable ItemStack stack, @Nullable Item item) {}

    /**
     * A spawn attempt.
     *
     * <p>A null {@code source} means the narrower question "does an entry for this entity exist
     * in this dimension at all", which is what the {@code EntityJoinLevel} fallback asks when no
     * spawn reason is available.
     */
    public record SpawnSubject(String entityId, @Nullable String source, String dimension) {}

    /** An interaction with an entity, including what the player is holding. */
    public record InteractionSubject(String entityId, String action, ItemStack held) {}
}
