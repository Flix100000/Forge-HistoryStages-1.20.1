package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityPickupMixin {
    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void historystages$blockPickup(Player player, CallbackInfo ci) {
        if (!Config.COMMON.individualLockItemPickup) return;
        if (!(player instanceof ServerPlayer sp)) return;
        ItemEntity self = (ItemEntity) (Object) this;
        ItemStack stack = self.getItem();
        if (stack.isEmpty()) return;
        if (StageLockHelper.isActionLockedByIndividualStage(stack, sp.getUUID(), "pickup")) {
            ResourceLocation itemRL = BuiltInRegistries.ITEM.getKey(stack.getItem());
            DebugLogger.runtimeThrottled("Inventory", "pickup_blocked_" + sp.getUUID() + "_" + itemRL,
                    "<" + sp.getName().getString() + "> Pickup of '" + itemRL + "' blocked [action: pickup]");
            ci.cancel();
        }
    }
}
