package net.astr0.historystages.api.conditions;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record ItemLockCondition(ResourceLocation stageId, CompoundTag requiredNbt) {
    public boolean matches(ItemStack stack) {
        if (requiredNbt == null) return true; // No NBT required, direct match
        if (!stack.hasTag()) return false;    // NBT required, but item has none
        return NbtUtils.compareNbt(requiredNbt, stack.getTag(), true); // Strict/Fuzzy match
    }
}