package net.bananemdnsa.historystages.util.lock;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.TradeLockKind;
import net.bananemdnsa.historystages.data.lock.TradeOfferFilter;
import net.bananemdnsa.historystages.data.lock.engine.LockResolution;
import net.bananemdnsa.historystages.data.lock.engine.StageLocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jetbrains.annotations.Nullable;

/**
 * Joins the two scopes for a trade question, and turns a live merchant into something
 * {@link TradeOfferFilter} can reason about.
 *
 * <p>Everything Minecraft-shaped about the trade category is here: reading an offer's ids,
 * finding a villager's profession, and answering the item action {@code trade} through the lock
 * engine. The decisions themselves are in the filter, which is why they can be unit-tested.
 *
 * <p>An offer survives only if <em>both</em> scopes keep it. That is the same "global or
 * individual locks it" rule every other check in this mod follows, expressed over a list.
 */
public final class TradeLockHelper {

    private TradeLockHelper() {}

    /** What one viewer may see of a merchant's offers, and who is holding the rest back. */
    public record Filtered(List<Integer> keptIndices, List<String> gatingStages, int offeredCount) {

        /**
         * Whether anything was taken away — the difference between "you may not see any of this"
         * and "this merchant genuinely has nothing", which is the only case worth a message.
         */
        public boolean removedAnything() {
            return keptIndices.size() < offeredCount;
        }

        public boolean removedEverything() {
            return keptIndices.isEmpty() && offeredCount > 0;
        }
    }

    /** The offers this player may see, as indices into {@code offers}. */
    public static Filtered filterForPlayer(MerchantOffers offers, Merchant merchant, int level,
                                           Player player) {
        List<TradeOfferFilter.Offer> view = new ArrayList<>(offers.size());
        for (MerchantOffer offer : offers) {
            view.add(asFilterOffer(offer));
        }
        TradeOfferFilter.MerchantView merchantView = new TradeOfferFilter.MerchantView(
                professionIdOf(merchant), level, merchantKeyOf(merchant));

        UUID uuid = player.getUUID();
        TradeOfferFilter.Result global = TradeOfferFilter.filter(view, merchantView,
                StageManager.getStages(), StageLocks.serverGlobal(),
                itemActionGate(StageScope.GLOBAL, StageLocks.serverGlobal()));
        TradeOfferFilter.Result individual = TradeOfferFilter.filter(view, merchantView,
                StageManager.getIndividualStages(), StageLocks.serverIndividual(uuid),
                itemActionGate(StageScope.INDIVIDUAL, StageLocks.serverIndividual(uuid)));

        List<Integer> kept = new ArrayList<>(global.keptIndices());
        kept.retainAll(individual.keptIndices());

        Set<String> gating = new LinkedHashSet<>(global.gatingStages());
        gating.addAll(individual.gatingStages());

        return new Filtered(List.copyOf(kept), List.copyOf(gating), view.size());
    }

    /**
     * The stages holding one offer back from this player, or an empty list.
     *
     * <p>Used by the seam that guards payment, which is asked about a single resolved offer
     * rather than a whole list.
     */
    public static List<String> gatingStagesForOffer(MerchantOffer offer, Merchant merchant,
                                                    int level, Player player) {
        TradeOfferFilter.Offer view = asFilterOffer(offer);
        TradeOfferFilter.MerchantView merchantView = new TradeOfferFilter.MerchantView(
                professionIdOf(merchant), level, merchantKeyOf(merchant));
        UUID uuid = player.getUUID();

        Set<String> gating = new LinkedHashSet<>(TradeOfferFilter.gatingStagesFor(
                view, merchantView, StageManager.getStages(), StageLocks.serverGlobal(),
                itemActionGate(StageScope.GLOBAL, StageLocks.serverGlobal())));
        gating.addAll(TradeOfferFilter.gatingStagesFor(
                view, merchantView, StageManager.getIndividualStages(),
                StageLocks.serverIndividual(uuid),
                itemActionGate(StageScope.INDIVIDUAL, StageLocks.serverIndividual(uuid))));

        return List.copyOf(gating);
    }

    /**
     * The merchant's own level, for the seam that has no level parameter to work from.
     *
     * <p>1 for anything that is not a villager, which is exactly what {@code WanderingTrader}
     * passes when it opens its own screen — so a stage gating level 1 hides every wandering
     * trader. That is stated in the tab's tooltip rather than left to be discovered.
     */
    public static int levelOf(Merchant merchant) {
        return merchant instanceof Villager villager
                ? villager.getVillagerData().getLevel()
                : 1;
    }

    /**
     * Turns stage ids into the names a player would recognise.
     *
     * <p>The filter answers in ids, because that is what a lock is written against. What goes on
     * screen is what the pack author called the stage — and when a stage has since been deleted
     * the id is still better than nothing, so it falls back to it rather than dropping the entry.
     */
    public static List<String> displayNamesOf(List<String> stageIds) {
        List<String> names = new ArrayList<>(stageIds.size());
        for (String stageId : stageIds) {
            StageEntry stage = StageManager.getStages().get(stageId);
            if (stage == null) stage = StageManager.getIndividualStages().get(stageId);
            names.add(stage != null ? stage.getDisplayName() : stageId);
        }
        return names;
    }

    /**
     * Which kind of stage is holding these offers back.
     *
     * <p>Read from the same two tables {@link #displayNamesOf} walks: a stage the global table
     * does not know and the individual one does is an individual stage, and one set of ids can
     * contain both kinds. A stage neither table knows is counted as neither — it cannot be shown
     * and cannot be classified, and guessing would be worse than leaving it out.
     */
    public static TradeLockKind kindOf(List<String> stageIds) {
        boolean anyGlobal = false;
        boolean anyIndividual = false;
        for (String stageId : stageIds) {
            if (StageManager.getStages().containsKey(stageId)) {
                anyGlobal = true;
            } else if (StageManager.getIndividualStages().containsKey(stageId)) {
                anyIndividual = true;
            }
        }
        return TradeLockKind.of(anyGlobal, anyIndividual);
    }

    private static TradeOfferFilter.Offer asFilterOffer(MerchantOffer offer) {
        ItemStack costB = offer.getCostB();
        return new TradeOfferFilter.Offer(
                asFilterItem(offer.getResult()),
                asFilterItem(offer.getCostA()),
                costB.isEmpty() ? null : asFilterItem(costB));
    }

    private static TradeOfferFilter.OfferItem asFilterItem(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new TradeOfferFilter.OfferItem(key == null ? null : key.toString(), stack);
    }

    /**
     * The merchant's profession, or null when it has none.
     *
     * <p>Null is the honest answer for the wandering trader and for every merchant another mod
     * writes. A profession entry can never catch those; they are gated by item or not at all,
     * and the tab's tooltip says so.
     */
    /**
     * Who this merchant is, for naming one of its offers.
     *
     * <p>The profession for a villager. For anything without one, its entity type — which is what
     * lets a single wandering-trader offer, or a single offer from a merchant another mod wrote, be
     * named at all. A profession <em>lock</em> still ignores those, because gating "the profession
     * of a thing that has none" would mean nothing; naming one of its trades is a different
     * question and has a good answer.
     *
     * <p>Never null, so an offer entry always has something to compare against. A profession id
     * and an entity id could in principle collide, and the consequence would be that two
     * merchants' identically-shaped offers gate together — small, explainable, and far cheaper
     * than a second field in every stage file.
     */
    public static String merchantKeyOf(Merchant merchant) {
        String professionId = professionIdOf(merchant);
        if (professionId != null) return professionId;
        if (merchant instanceof net.minecraft.world.entity.Entity entity) {
            ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (type != null) return type.toString();
        }
        return "";
    }

    @Nullable
    private static String professionIdOf(Merchant merchant) {
        if (!(merchant instanceof Villager villager)) return null;
        VillagerProfession profession = villager.getVillagerData().getProfession();
        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return key == null ? null : key.toString();
    }

    /**
     * The item action {@code trade}, asked through the ordinary item path so that item, tag and
     * mod entries all answer for it.
     *
     * <p>Carries no direction: an item entry narrowed to {@code trade} means nobody trades with
     * it either way. Narrowing to one side is what the trade tab's own entries are for.
     */
    private static TradeOfferFilter.ItemActionGate itemActionGate(StageScope scope,
                                                                  StageStateView state) {
        return item -> {
            if (!(item.stack() instanceof ItemStack stack) || stack.isEmpty()) return List.of();
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key == null) return List.of();
            return LockResolution.missingStages(
                    StageLocks.engine().gatingStagesForItemAction(
                            key.toString(), key.getNamespace(), stack, "trade", scope),
                    state);
        };
    }
}
