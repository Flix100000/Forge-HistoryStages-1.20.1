package net.bananemdnsa.historystages.jei;

import net.minecraft.world.item.ItemStack;

/** One booster entry rendered as a JEI recipe. */
public record BoosterRecipe(ItemStack blockStack, int speedPercent, int costPercent) {}
