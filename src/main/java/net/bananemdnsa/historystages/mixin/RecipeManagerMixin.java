package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.events.RecipeHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {
    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;",
            at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void historystages$filterRecipe(
            RecipeType<T> type, I input, Level level, CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        Optional<RecipeHolder<T>> result = cir.getReturnValue();
        if (result.isPresent() && isRecipeLocked(result.get(), level.isClientSide())) {
            cir.setReturnValue(Optional.empty());
        }
    }

    @Inject(method = "getRecipesFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void historystages$filterRecipes(
            RecipeType<T> type, I input, Level level, CallbackInfoReturnable<List<RecipeHolder<T>>> cir) {
        List<RecipeHolder<T>> recipes = cir.getReturnValue();
        List<RecipeHolder<T>> filtered = recipes.stream()
                .filter(recipe -> !isRecipeLocked(recipe, level.isClientSide()))
                .collect(Collectors.toList());
        if (filtered.size() != recipes.size()) {
            cir.setReturnValue(filtered);
        }
    }

    private static boolean isRecipeLocked(RecipeHolder<?> recipe, boolean isClientSide) {
        return RecipeHandler.isOutputLocked(recipe.value(), isClientSide) || RecipeHandler.isRecipeIdLocked(recipe.id(), isClientSide);
    }
}
