package net.bananemdnsa.historystages.mixin;

import java.util.List;

import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.TradeLockHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The second trade seam: no result is produced for an offer this player may not have.
 *
 * <p>This is the one that counts. The server resolves a trade through the merchant's own full
 * list, not through the shortened one the client was sent, so a modified client could otherwise
 * pay for an offer it never received. A player with an ordinary client never reaches this code —
 * they cannot select what they were not shown.
 *
 * <p>Here rather than on {@code MerchantResultSlot.onTake} on purpose. By the time the result is
 * being taken it has already been drawn into the slot, and a click that does nothing is exactly
 * the broken-looking behaviour this category was built to replace. Refusing to fill the slot in
 * the first place leaves nothing to click.
 */
@Mixin(MerchantContainer.class)
public abstract class MerchantContainerMixin {

    @Shadow @Final private Merchant merchant;

    @Shadow private MerchantOffer activeOffer;

    @Shadow private int futureXp;

    @Inject(method = "updateSellItem", at = @At("RETURN"))
    private void historystages$refuseGatedOffer(CallbackInfo ci) {
        if (activeOffer == null) return;
        if (merchant.isClientSide()) return;

        Player player = merchant.getTradingPlayer();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        List<String> gating = TradeLockHelper.gatingStagesForOffer(
                activeOffer, merchant, TradeLockHelper.levelOf(merchant), serverPlayer);
        if (gating.isEmpty()) return;

        MerchantContainer container = (MerchantContainer) (Object) this;
        container.setItem(2, ItemStack.EMPTY);
        activeOffer = null;
        futureXp = 0;

        DebugLogger.runtimeThrottled("Trade Lock",
                "trade_payment_" + serverPlayer.getUUID(),
                "<" + serverPlayer.getName().getString() + "> payment for a gated offer refused — "
                        + "missing stages: " + gating);
    }
}
