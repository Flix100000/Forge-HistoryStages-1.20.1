package net.bananemdnsa.historystages.mixin;

import java.util.Optional;

import net.bananemdnsa.historystages.util.lock.RecipeCraftContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Tells the recipe filter who is standing at the crafting table.
 *
 * <p>{@code slotChangedCraftingGrid} is static and takes the player, which is why this one hook
 * covers both the crafting table and the 2x2 inventory grid: {@code InventoryMenu.slotsChanged}
 * calls straight into it.
 *
 * <p>The redirect wraps the resolution call and nothing else. Wrapping the method instead would
 * leave the window open for {@code assemble}, {@code setRecipeUsed} and the slot packet, none of
 * which should be judged as a recipe lookup.
 *
 * <p>Server-side only, and that is vanilla's doing — the method returns immediately on the client
 * and the result slot arrives by packet. Client and server therefore cannot disagree here.
 */
@Mixin(CraftingMenu.class)
public class CraftingMenuMixin {

    @Redirect(
            method = "slotChangedCraftingGrid",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor("
                            + "Lnet/minecraft/world/item/crafting/RecipeType;"
                            + "Lnet/minecraft/world/item/crafting/RecipeInput;"
                            + "Lnet/minecraft/world/level/Level;"
                            + "Lnet/minecraft/world/item/crafting/RecipeHolder;"
                            + ")Ljava/util/Optional;"))
    private static Optional<RecipeHolder<CraftingRecipe>> historystages$resolveForCrafter(
            RecipeManager manager, RecipeType<CraftingRecipe> type, RecipeInput input, Level level,
            RecipeHolder<CraftingRecipe> lastRecipe,
            AbstractContainerMenu menu, Level menuLevel, Player player,
            CraftingContainer craftSlots, ResultContainer resultSlots,
            RecipeHolder<CraftingRecipe> recipe) {
        return RecipeCraftContext.with(player.getUUID(),
                () -> manager.getRecipeFor(type, (CraftingInput) input, level, lastRecipe));
    }
}
