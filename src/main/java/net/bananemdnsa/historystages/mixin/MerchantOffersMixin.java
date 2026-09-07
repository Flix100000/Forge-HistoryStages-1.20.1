package net.bananemdnsa.historystages.mixin;

import java.util.OptionalInt;

import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.TradeLockedPacket;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.TradeLockHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The first trade seam: the player is shown only the offers they may see.
 *
 * <p>On the interface rather than on {@code Villager}, and that is the whole point. Neither
 * {@code AbstractVillager}, {@code Villager} nor {@code WanderingTrader} overrides
 * {@link Merchant#openTradingScreen} — all three inherit it — and so does every merchant any
 * other mod writes. One seam therefore covers a modded trader nobody here has ever heard of,
 * without naming its class.
 *
 * <p><strong>The merchant keeps its real list.</strong> Only the copy sent to this player is
 * short. {@code overrideOffers} would change the merchant permanently, and a stage that is later
 * unlocked could not give the offer back — the one-way behaviour this whole category was designed
 * to avoid.
 *
 * <p>Nothing is drawn as locked, because there is no locked trade to draw: it is simply absent.
 * A merchant with one offer instead of two is unremarkable. A merchant with <em>none</em> is not,
 * so that case — and only that case — says why, and it says it inside the window the player is
 * looking at rather than in the actionbar under it. The player is staring at an empty list; the
 * explanation belongs where they are looking.
 *
 * <p>This seam is about what the player sees. It is not the security boundary: a modified client
 * could ask to pay for an offer it was never sent, which is what {@link MerchantContainerMixin}
 * is for.
 */
@Mixin(Merchant.class)
public interface MerchantOffersMixin {

    @Inject(method = "openTradingScreen", at = @At("HEAD"), cancellable = true)
    private void historystages$filterOffers(Player player, Component displayName, int level,
                                            CallbackInfo ci) {
        Merchant merchant = (Merchant) this;
        if (merchant.isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        MerchantOffers offers = merchant.getOffers();
        if (offers.isEmpty()) return;

        TradeLockHelper.Filtered filtered =
                TradeLockHelper.filterForPlayer(offers, merchant, level, serverPlayer);
        if (!filtered.removedAnything()) return;

        MerchantOffers shown = new MerchantOffers();
        for (int index : filtered.keptIndices()) {
            shown.add(offers.get(index));
        }

        // Vanilla's own body, with the shortened list in place of the merchant's. Opening the
        // menu against the real merchant is deliberate: payment still resolves through its full
        // list, and the second seam is what refuses the offers this player never received.
        OptionalInt containerId = player.openMenu(new SimpleMenuProvider(
                (id, inventory, viewer) -> new MerchantMenu(id, inventory, merchant), displayName));
        if (containerId.isPresent() && !shown.isEmpty()) {
            player.sendMerchantOffers(containerId.getAsInt(), shown, level,
                    merchant.getVillagerXp(), merchant.showProgressBar(), merchant.canRestock());
        }

        DebugLogger.runtimeThrottled("Trade Lock",
                "trade_" + serverPlayer.getUUID() + "_" + filtered.gatingStages(),
                "<" + serverPlayer.getName().getString() + "> " + filtered.keptIndices().size()
                        + " of " + filtered.offeredCount() + " offers shown — held back by: "
                        + filtered.gatingStages());

        // Only when nothing at all survived, and only after the window is confirmed open — there
        // is no point explaining an empty list to somebody who has no list in front of them.
        if (filtered.removedEverything() && containerId.isPresent()) {
            PacketHandler.sendTradeLockedToPlayer(
                    new TradeLockedPacket(containerId.getAsInt(),
                            TradeLockHelper.displayNamesOf(filtered.gatingStages()),
                            TradeLockHelper.kindOf(filtered.gatingStages())),
                    serverPlayer);
        }

        ci.cancel();
    }
}
