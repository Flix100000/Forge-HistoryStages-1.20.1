package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
    @Inject(method = "createResult", at = @At("RETURN"))
    private void historystages$createResult(CallbackInfo ci) {
        if (!Config.COMMON.lockEnchanting && !Config.COMMON.individualLockEnchanting) {
            return;
        }

        ItemCombinerMenuAccessor accessor = (ItemCombinerMenuAccessor) this;
        Player player = accessor.historystages$getPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        ItemStack right = accessor.historystages$getInputSlots().getItem(1);
        if (!right.isEmpty() && StageLockHelper.isItemLockedForPlayer(right, serverPlayer)) {
            accessor.historystages$getResultSlots().setItem(0, ItemStack.EMPTY);
        }
    }
}
