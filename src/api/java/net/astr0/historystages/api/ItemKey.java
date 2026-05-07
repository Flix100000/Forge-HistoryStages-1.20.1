package net.astr0.historystages.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * An immutable key representing an Item and its optional NBT data.
 */
public record ItemKey(Item item, @Nullable CompoundTag nbt) {

    // NBT locking is somewhat of a fools' errand. It can be done, but trying to do it in any kind of
    // performance friendly way will have us chasing our tails. Using this method is the best I could
    // come up with. It does apply some GC pressure as checking requires the creation of instances of this
    // small record class, but there is nothing we can really do. The only way I think this could be made a lot
    // simpler is if we didn't need to be able to find out what stage locked the NBT, but that would kind of
    // break the whole point of this feature. For now, we will accept the GC hit and hope its not too bad.
    public ItemKey(Item item, @Nullable CompoundTag nbt) {
        this.item = item;
        // Normalize: Treat empty tags as null for faster equality checks
        this.nbt = (nbt == null || nbt.isEmpty()) ? null : nbt.copy();
    }

    public static ItemKey of(ItemStack stack, boolean ignoreNBT) {

        if (ignoreNBT) return ItemKey.of(stack.getItem());

        return new ItemKey(stack.getItem(), stack.getTag());
    }

    public static ItemKey of(ItemStack stack) {
        return new ItemKey(stack.getItem(), stack.getTag());
    }

    public static ItemKey of(Item item) {
        return new ItemKey(item, null);
    }

    public boolean hasNbt() {
        return nbt != null;
    }

    public void clearNbt() {
        nbt = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemKey that)) return false;
        // Item check first (pointer equality), then NBT
        return this.item == that.item && Objects.equals(this.nbt, that.nbt);
    }

    @Override
    public int hashCode() {
        // CompoundTag.hashCode() is recursive; this is why the Fast-Path is needed
        int result = item.hashCode();
        result = 31 * result + (nbt != null ? nbt.hashCode() : 0);
        return result;
    }
}
