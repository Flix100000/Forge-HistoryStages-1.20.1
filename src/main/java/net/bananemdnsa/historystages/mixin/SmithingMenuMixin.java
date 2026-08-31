package net.bananemdnsa.historystages.mixin;

import java.util.List;
import java.util.UUID;

import net.bananemdnsa.historystages.util.lock.RecipeCraftContext;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the recipe filter who opened the smithing table.
 *
 * <p>The crafter is remembered at construction rather than shadowed off
 * {@code ItemCombinerMenu.player}, so this reads exactly like the stonecutter next to it — same
 * decision, same shape.
 *
 * <p>{@code createResult} is the one place the result comes from, and it writes into the result
 * container. A locked recipe leaves that container empty, so {@code onTake} and
 * {@code quickMoveStack} need no hook of their own: they find nothing.
 */
@Mixin(SmithingMenu.class)
public class SmithingMenuMixin {

    @Unique
    @Nullable
    private UUID historystages$crafter;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;"
            + "Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
    private void historystages$rememberCrafter(int containerId, Inventory playerInventory,
                                               ContainerLevelAccess access, CallbackInfo ci) {
        this.historystages$crafter = playerInventory.player.getUUID();
    }

    @Redirect(
            method = "createResult",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipesFor("
                            + "Lnet/minecraft/world/item/crafting/RecipeType;"
                            + "Lnet/minecraft/world/item/crafting/RecipeInput;"
                            + "Lnet/minecraft/world/level/Level;"
                            + ")Ljava/util/List;"))
    private List<RecipeHolder<SmithingRecipe>> historystages$resolveForCrafter(
            RecipeManager manager, RecipeType<SmithingRecipe> type, RecipeInput input,
            Level level) {
        return RecipeCraftContext.with(this.historystages$crafter,
                () -> manager.getRecipesFor(type, (SmithingRecipeInput) input, level));
    }
}
