package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.events.RecipeHandler;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(targets = "net.minecraft.world.item.crafting.RecipeManager$1")
public class RecipeManagerCheckMixin {
    @Inject(method = "getRecipeFor", at = @At("RETURN"), cancellable = true, require = 0)
    private void historystages$filterCachedRecipe(RecipeInput input, Level level,
                                                  CallbackInfoReturnable<Optional<RecipeHolder<?>>> cir) {
        Optional<RecipeHolder<?>> result = cir.getReturnValue();
        if (!level.isClientSide() && result.isPresent()
                && RecipeHandler.isRecipeLocked(result.get(), level.registryAccess(), false)) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
