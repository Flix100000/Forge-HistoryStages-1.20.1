package net.bananemdnsa.historystages.mixin;

import java.util.List;
import java.util.UUID;

import net.bananemdnsa.historystages.util.lock.RecipeCraftContext;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the recipe filter who opened the stonecutter.
 *
 * <p>The resolution happens in {@code setupRecipeList}, not in {@code setupResultSlot} — the
 * latter only assembles from the list that was already resolved. Gating at the resolution means a
 * locked recipe never becomes a button in the first place, so preview and taking are both covered
 * without a second hook.
 *
 * <p>The menu carries no player field, so the crafter is remembered at construction. Both public
 * constructors run through the one injected here.
 *
 * <p>Runs on both sides — the menu exists on the client too and resolves its own button list. That
 * symmetry is the point: one side flipping alone would show a button whose result the other side
 * refuses.
 */
@Mixin(StonecutterMenu.class)
public class StonecutterMenuMixin {

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
            method = "setupRecipeList",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipesFor("
                            + "Lnet/minecraft/world/item/crafting/RecipeType;"
                            + "Lnet/minecraft/world/item/crafting/RecipeInput;"
                            + "Lnet/minecraft/world/level/Level;"
                            + ")Ljava/util/List;"))
    private List<RecipeHolder<StonecutterRecipe>> historystages$resolveForCrafter(
            RecipeManager manager, RecipeType<StonecutterRecipe> type, RecipeInput input,
            Level level) {
        return RecipeCraftContext.with(this.historystages$crafter,
                () -> manager.getRecipesFor(type, (SingleRecipeInput) input, level));
    }
}
