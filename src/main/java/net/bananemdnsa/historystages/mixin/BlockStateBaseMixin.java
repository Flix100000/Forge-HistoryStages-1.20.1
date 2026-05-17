package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.events.GameplayEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateBaseMixin {

    @Inject(method = "getDestroyProgress", at = @At("RETURN"), cancellable = true)
    private void historystages$slowLockedBlockBreak(Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        float multiplier = GameplayEvents.getBreakSpeedMultiplier((BlockState) (Object) this, player);
        if (multiplier < 1.0F) {
            cir.setReturnValue(cir.getReturnValue() * multiplier);
        }
    }
}
