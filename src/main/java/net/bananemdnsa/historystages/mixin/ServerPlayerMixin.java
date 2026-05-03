package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.events.GameplayEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {
    @Inject(method = "changeDimension", at = @At("HEAD"), cancellable = true)
    private void historystages$blockLockedDimension(DimensionTransition transition,
            CallbackInfoReturnable<Entity> cir) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        String targetDimensionId = transition.newLevel().dimension().location().toString();
        if (!GameplayEvents.shouldBlockDimensionChange(player, targetDimensionId)) {
            return;
        }

        cir.setReturnValue(player);
    }
}
