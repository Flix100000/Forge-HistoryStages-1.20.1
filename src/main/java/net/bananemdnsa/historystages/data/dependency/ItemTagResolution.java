package net.bananemdnsa.historystages.data.dependency;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Turns a {@code "#c:ingots"} entry into something to compare against or to draw.
 *
 * <p>The single place that knows what the {@code #} means. Three screens, a tooltip and the
 * pedestal's deposit slot all ask the same two questions — is this stack in the tag, and what
 * should the player see — and they have to agree, or an item goes in that the card said would
 * not count.
 *
 * <p><strong>An open entry cycles, a settled one does not.</strong> Until the first item is
 * booked the entry could still become any member of the tag, and showing one of them frozen
 * would read as a promise. So the display walks the members on the wall clock: one member per
 * second, the same member everywhere at the same moment, without a single animation timer. Once
 * the scroll records a choice, that choice is all there is to show.
 *
 * <p>Nothing here is cached. Tags rebind on every reload, and a cache of tag members with no
 * invalidation is how this repo has twice produced a bug that reads as "only works after a
 * restart". {@code BuiltInRegistries.ITEM.getTag} is a map lookup.
 */
public final class ItemTagResolution {

    /** How long one member of an unsettled tag is shown before the next takes over. */
    private static final long CYCLE_MS = 1000L;

    private ItemTagResolution() {}

    /** Whether {@code id} names a tag rather than an item. */
    public static boolean isTag(@Nullable String id) {
        return id != null && id.startsWith("#");
    }

    /** The tag key behind a {@code "#..."} entry, or null when the id is not a usable one. */
    @Nullable
    public static TagKey<Item> tagKey(@Nullable String tagId) {
        if (!isTag(tagId)) return null;
        ResourceLocation rl = ResourceLocation.tryParse(tagId.substring(1));
        return rl == null ? null : TagKey.create(Registries.ITEM, rl);
    }

    /**
     * Whether the stack belongs to the tag.
     *
     * <p>Only membership. An entry's NBT criterion is checked by the caller through
     * {@code NbtMatcher}, exactly as it is for a plain item entry — keeping it out here is what
     * lets the two entry kinds share the deposit code.
     *
     * <p>An unknown tag matches nothing rather than throwing: a stage may name a tag from a mod
     * that is not installed, and that must leave the requirement unfulfillable, not the world
     * unloadable.
     */
    public static boolean matches(@Nullable String tagId, ItemStack stack) {
        TagKey<Item> key = tagKey(tagId);
        return key != null && !stack.isEmpty() && stack.is(key);
    }

    /** Every item in the tag, in registry order. Empty for an unknown or empty tag. */
    public static List<Item> members(@Nullable String tagId) {
        TagKey<Item> key = tagKey(tagId);
        if (key == null) return List.of();
        var tag = BuiltInRegistries.ITEM.getTag(key);
        if (tag.isEmpty()) return List.of();
        List<Item> items = new ArrayList<>();
        for (Holder<Item> holder : tag.get()) items.add(holder.value());
        return items;
    }

    /**
     * What to draw for a tag entry.
     *
     * @param lockedId the item the scroll recorded, or null/empty while the entry is still open
     * @param timeMs   wall clock; the caller passes {@code System.currentTimeMillis()} and gets
     *                 the same member every other caller gets in that second
     * @return the settled item, a cycling member, or {@link ItemStack#EMPTY} when the tag
     *         resolves to nothing
     */
    public static ItemStack displayStack(@Nullable String tagId, @Nullable String lockedId, long timeMs) {
        Item settled = itemById(lockedId);
        if (settled != null) return new ItemStack(settled);

        List<Item> members = members(tagId);
        if (members.isEmpty()) return ItemStack.EMPTY;
        return new ItemStack(members.get(cycleIndex(members.size(), timeMs)));
    }

    /**
     * What to write for a tag entry. Falls back to the tag id itself, which is the only honest
     * thing left to say about a tag nothing in this instance provides.
     */
    public static String displayName(@Nullable String tagId, @Nullable String lockedId, long timeMs) {
        ItemStack stack = displayStack(tagId, lockedId, timeMs);
        if (!stack.isEmpty()) return stack.getHoverName().getString();
        return tagId == null ? "" : tagId;
    }

    private static int cycleIndex(int size, long timeMs) {
        return (int) (Math.floorMod(timeMs / CYCLE_MS, (long) size));
    }

    @Nullable
    private static Item itemById(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) return null;
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return null;
        return BuiltInRegistries.ITEM.get(rl);
    }
}
