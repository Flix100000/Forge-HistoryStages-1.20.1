package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.events.LootLockHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

@Mixin(Player.class)
public abstract class PlayerOpenMenuMixin {
    @Inject(method = "openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;",
            at = @At("TAIL"))
    private void historystages$filterLootrContents(MenuProvider menuProvider,
                                                   CallbackInfoReturnable<OptionalInt> cir) {
        Player self = (Player) (Object) this;
        if (!(self instanceof ServerPlayer serverPlayer)) return;
        AbstractContainerMenu menu = serverPlayer.containerMenu;
        if (menu == null) return;
        LootLockHandler.onMenuOpened(serverPlayer, menu);
    }
}
