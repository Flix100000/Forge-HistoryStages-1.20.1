package net.bananemdnsa.historystages.gametest;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.TradeOfferEntry;
import net.bananemdnsa.historystages.data.TradeProfessionEntry;
import net.bananemdnsa.historystages.data.lock.TradeGoodsScanner;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.util.lock.TradeLockHelper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The trade seam, answered against a live registry.
 *
 * <p>The filter's decision table is pinned by {@code TradeOfferFilterTest}, which needs no game.
 * What can only be shown here is the wiring: that a real {@code MerchantOffer} is read correctly,
 * that a villager's profession and level arrive as ids, that an entry's criterion is settled
 * against a real stack, and that the item action {@code trade} reaches an offer through ordinary
 * item entries.
 *
 * <p><strong>Nothing here opens a trade screen.</strong> The test player has no connection, and
 * {@code openTradingScreen} sends it a packet — the failure would say nothing about trade locks.
 * The filter is asked directly instead, which is the same call the seam makes.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TradeLockTests {

    private TradeLockTests() {}

    // ---------------------------------------------------------------------------------------
    // Helpers. Deliberately above the tests: GameTestCleanupGuardTest slices the file from one
    // test to the next and gives the last test everything to the end, so a helper sitting after
    // it gets blamed on that test.
    // ---------------------------------------------------------------------------------------

    /** 20 paper → 1 emerald, the shape of nearly every villager trade. */
    private static MerchantOffers paperForEmerald() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(
                new ItemCost(Items.PAPER, 20), new ItemStack(Items.EMERALD), 16, 2, 0.05f));
        return offers;
    }

    /**
     * The lock entry naming {@link #paperForEmerald}, as the picker would produce it.
     *
     * <p>Spelled out rather than derived, so a change to how a trade is identified breaks these
     * tests instead of quietly making them agree with themselves.
     */
    private static TradeOfferEntry paperForEmeraldEntry() {
        return new TradeOfferEntry("minecraft:librarian", 1,
                "minecraft:emerald", "minecraft:paper", null);
    }

    /** A librarian at the given level, not added to the world — nothing here needs it ticking. */
    private static Villager librarian(GameTestHelper helper, int level) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        if (villager != null) {
            villager.setVillagerData(
                    new VillagerData(VillagerType.PLAINS, VillagerProfession.LIBRARIAN, level));
        }
        return villager;
    }

    /** A stack carrying {@code {tier: <value>}} in its custom data. */
    private static ItemStack tiered(String value) {
        ItemStack stack = new ItemStack(Items.EMERALD);
        CompoundTag tag = new CompoundTag();
        tag.putString("tier", value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static JsonObject tierCriterion(String value) {
        JsonObject criterion = new JsonObject();
        criterion.addProperty("tier", value);
        return criterion;
    }

    private static MerchantOffers offerSelling(ItemStack result) {
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.PAPER, 20), result, 16, 2, 0.05f));
        return offers;
    }

    /** How many of these offers the player may see. */
    private static int shownTo(MerchantOffers offers, Villager villager, ServerPlayer player) {
        return TradeLockHelper
                .filterForPlayer(offers, villager, TradeLockHelper.levelOf(villager), player)
                .keptIndices().size();
    }

    // ---------------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------------

    /** The claim: a stage names one trade, and that trade is gone. */
    @GameTest(template = "empty")
    public static void aGatedOfferIsNotShown(GameTestHelper helper) {
        try {
            GameTestStages.global("trade_result", stage -> stage.setTradeOffers(
                    new ArrayList<>(List.of(paperForEmeraldEntry()))));

            ServerPlayer player = GameTestPlayers.create(helper);
            Villager villager = librarian(helper, 1);

            if (shownTo(paperForEmerald(), villager, player) != 0) {
                helper.fail("a stage names this exact trade and is not unlocked, but it is "
                        + "still shown");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * The control, and the whole reason for naming offers instead of items: the same goods from
     * another merchant are a different trade and must survive.
     */
    @GameTest(template = "empty")
    public static void theSameGoodsFromAnotherMerchantAreLeftAlone(GameTestHelper helper) {
        try {
            GameTestStages.global("trade_unrelated", stage -> stage.setTradeOffers(
                    new ArrayList<>(List.of(new TradeOfferEntry("minecraft:cartographer", 1,
                            "minecraft:emerald", "minecraft:paper", null)))));

            ServerPlayer player = GameTestPlayers.create(helper);
            Villager villager = librarian(helper, 1);

            if (shownTo(paperForEmerald(), villager, player) != 1) {
                helper.fail("the gated trade belongs to a cartographer, but a librarian making "
                        + "the same trade was filtered - the merchant is not part of the match");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** A level is part of what names a trade, so the same recipe at another level survives. */
    @GameTest(template = "empty")
    public static void theSameTradeAtAnotherLevelIsLeftAlone(GameTestHelper helper) {
        try {
            GameTestStages.global("trade_level_scoped", stage -> stage.setTradeOffers(
                    new ArrayList<>(List.of(new TradeOfferEntry("minecraft:librarian", 3,
                            "minecraft:emerald", "minecraft:paper", null)))));

            ServerPlayer player = GameTestPlayers.create(helper);

            if (shownTo(paperForEmerald(), librarian(helper, 1), player) != 1) {
                helper.fail("only the level 3 trade is gated, but a level 1 librarian was "
                        + "filtered - the level is not part of the match");
                return;
            }
            if (shownTo(paperForEmerald(), librarian(helper, 3), player) != 0) {
                helper.fail("the level 3 trade is gated, but a level 3 librarian still shows it");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * A trade a mod added is found by the picker and can be gated like any other.
     *
     * <p>Mods do not write their own list — they add listings to the same table vanilla fills,
     * through {@code VillagerTradesEvent}, and NeoForge writes the result back into
     * {@code VillagerTrades.TRADES} when a world loads its data. So this puts a listing there the
     * same way and checks both halves of the claim: that the scan sees it, and that an entry
     * naming it removes it from what a player is shown.
     *
     * <p>Done here rather than by installing a mod because no mod in this workspace adds a
     * villager trade — the table is pure vanilla, so nothing else in these tests would notice if
     * added trades were being skipped.
     */
    @GameTest(template = "empty")
    public static void aTradeAddedTheWayAModAddsItIsFoundAndGateable(GameTestHelper helper) {
        Int2ObjectMap<VillagerTrades.ItemListing[]> byLevel =
                VillagerTrades.TRADES.get(VillagerProfession.LIBRARIAN);
        if (byLevel == null) {
            helper.fail("the librarian has no trade table to add to");
            return;
        }
        VillagerTrades.ItemListing[] original = byLevel.get(1);
        try {
            // What a mod's listing looks like from here: something that hands over an item no
            // vanilla librarian ever offers.
            VillagerTrades.ItemListing added = (trader, random) -> new MerchantOffer(
                    new ItemCost(Items.EMERALD, 3), new ItemStack(Items.DRAGON_EGG), 5, 1, 0.05f);
            VillagerTrades.ItemListing[] extended =
                    java.util.Arrays.copyOf(original, original.length + 1);
            extended[original.length] = added;
            byLevel.put(1, extended);

            net.bananemdnsa.historystages.data.lock.TradePreview match = null;
            for (net.bananemdnsa.historystages.data.lock.TradePreview offer
                    : TradeGoodsScanner.scan(helper.getLevel())) {
                if ("minecraft:dragon_egg".equals(offer.resultId())) match = offer;
            }
            final net.bananemdnsa.historystages.data.lock.TradePreview found = match;
            if (found == null) {
                helper.fail("a trade added to the table the way a mod adds one was not found by "
                        + "the scan, so it would never appear in the picker");
                return;
            }
            if (!"minecraft:librarian".equals(found.professionId()) || found.level() != 1) {
                helper.fail("the added trade was found but attributed to " + found.professionId()
                        + " level " + found.level());
                return;
            }

            GameTestStages.global("trade_modded", stage -> stage.setTradeOffers(
                    new ArrayList<>(List.of(found.asLockEntry()))));

            ServerPlayer player = GameTestPlayers.create(helper);
            MerchantOffers offers = new MerchantOffers();
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 3),
                    new ItemStack(Items.DRAGON_EGG), 5, 1, 0.05f));

            if (shownTo(offers, librarian(helper, 1), player) != 0) {
                helper.fail("the added trade is named by a stage that is not unlocked, but it is "
                        + "still shown - a modded trade cannot be gated");
                return;
            }
            helper.succeed();
        } finally {
            byLevel.put(1, original);
            GameTestStages.removeAll();
        }
    }

    /** A profession entry has to resolve the villager's profession to an id. */
    @GameTest(template = "empty")
    public static void aGatedProfessionHidesEverythingTheVillagerHas(GameTestHelper helper) {
        try {
            GameTestStages.global("trade_profession", stage -> stage.setTradeProfessions(
                    new ArrayList<>(List.of("minecraft:librarian"))));

            ServerPlayer player = GameTestPlayers.create(helper);
            Villager villager = librarian(helper, 1);

            if (shownTo(paperForEmerald(), villager, player) != 0) {
                helper.fail("librarians are gated, but a librarian's offer is still shown - the "
                        + "profession is not being resolved to its registry id");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * A profession narrowed to some levels reaches those and no others.
     *
     * <p>The whole reason the narrowing exists: gating level 4 outright would take every other
     * profession's experts with it, and this is the only way to say "librarians, from expert up".
     */
    @GameTest(template = "empty")
    public static void aProfessionNarrowedToLevelsSparesTheOthers(GameTestHelper helper) {
        try {
            GameTestStages.global("trade_profession_levels", stage ->
                    stage.setTradeProfessionEntries(new ArrayList<>(List.of(
                            new TradeProfessionEntry("minecraft:librarian",
                                    new ArrayList<>(List.of("4", "5")))))));

            ServerPlayer player = GameTestPlayers.create(helper);

            if (shownTo(paperForEmerald(), librarian(helper, 3), player) != 1) {
                helper.fail("only levels 4 and 5 are gated, but a level 3 librarian was filtered "
                        + "- the merchant's level is not reaching the profession entry");
                return;
            }
            if (shownTo(paperForEmerald(), librarian(helper, 4), player) != 0) {
                helper.fail("level 4 is gated for librarians, but a level 4 librarian still "
                        + "shows its offers");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** And must not catch a villager of another trade. */
    @GameTest(template = "empty")
    public static void anotherProfessionIsLeftAlone(GameTestHelper helper) {
        try {
            GameTestStages.global("trade_other_profession", stage -> stage.setTradeProfessions(
                    new ArrayList<>(List.of("minecraft:weaponsmith"))));

            ServerPlayer player = GameTestPlayers.create(helper);
            Villager villager = librarian(helper, 1);

            if (shownTo(paperForEmerald(), villager, player) != 1) {
                helper.fail("weaponsmiths are gated but a librarian was filtered");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** The level is the merchant's own, read off the villager rather than off the offer. */
    @GameTest(template = "empty")
    public static void aGatedLevelHidesEverythingTheVillagerHas(GameTestHelper helper) {
        try {
            GameTestStages.global("trade_level", stage ->
                    stage.setTradeLevels(new ArrayList<>(List.of("4"))));

            ServerPlayer player = GameTestPlayers.create(helper);

            if (shownTo(paperForEmerald(), librarian(helper, 4), player) != 0) {
                helper.fail("level 4 is gated and this villager is level 4, but its offer is "
                        + "still shown");
                return;
            }
            if (shownTo(paperForEmerald(), librarian(helper, 3), player) != 1) {
                helper.fail("only level 4 is gated, but a level 3 villager was filtered too");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * The eleventh item action, reaching an offer through an ordinary item entry — the half the
     * unit tests can only fake, because deciding it needs the item registry.
     */
    @GameTest(template = "empty")
    public static void theItemActionTradeReachesAnOffer(GameTestHelper helper) {
        try {
            GameTestStages.global("trade_item_action", stage -> stage.setItemEntries(
                    new ArrayList<>(List.of(new ItemEntry(
                            "minecraft:emerald", null, List.of("trade"))))));

            ServerPlayer player = GameTestPlayers.create(helper);
            Villager villager = librarian(helper, 1);

            if (shownTo(paperForEmerald(), villager, player) != 0) {
                helper.fail("an item entry narrowed to the action \"trade\" does not reach the "
                        + "offer that hands that item over");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** An item entry narrowed to something else must leave trading alone. */
    @GameTest(template = "empty")
    public static void anItemNarrowedToAnotherActionDoesNotBlockTrading(GameTestHelper helper) {
        try {
            GameTestStages.global("trade_item_action_other", stage -> stage.setItemEntries(
                    new ArrayList<>(List.of(new ItemEntry(
                            "minecraft:emerald", null, List.of("equip"))))));

            ServerPlayer player = GameTestPlayers.create(helper);
            Villager villager = librarian(helper, 1);

            if (shownTo(paperForEmerald(), villager, player) != 1) {
                helper.fail("an item entry narrowed to \"equip\" blocked a trade it never named");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /** The criterion, which is the one part of an entry that needs a real stack to settle. */
    @GameTest(template = "empty")
    public static void aCriterionOnlyCatchesTheStackThatSatisfiesIt(GameTestHelper helper) {
        try {
            GameTestStages.global("trade_criterion", stage -> stage.setTradeOffers(
                    new ArrayList<>(List.of(new TradeOfferEntry("minecraft:librarian", 1,
                            "minecraft:emerald", "minecraft:paper", null,
                            tierCriterion("gold"))))));

            ServerPlayer player = GameTestPlayers.create(helper);
            Villager villager = librarian(helper, 1);

            if (shownTo(offerSelling(tiered("gold")), villager, player) != 0) {
                helper.fail("the entry's criterion matches this offer's result, but the offer "
                        + "is still shown");
                return;
            }
            if (shownTo(offerSelling(tiered("iron")), villager, player) != 1) {
                helper.fail("the entry's criterion does not match this offer's result, but the "
                        + "offer was filtered anyway");
                return;
            }
            if (shownTo(paperForEmerald(), villager, player) != 1) {
                helper.fail("a plain emerald satisfies no criterion, but the offer handing one "
                        + "over was filtered");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
        }
    }

    /**
     * The reason the seam sits where the window opens rather than where the merchant draws its
     * offers: a player is standing there, so an individual stage has someone to answer for.
     */
    @GameTest(template = "empty")
    public static void anIndividualStageAnswersPerPlayer(GameTestHelper helper) {
        IndividualStageData data = IndividualStageData.get(helper.getLevel());
        String stageId = GameTestStages.PREFIX + "trade_individual";
        ServerPlayer withoutIt = GameTestPlayers.create(helper);
        ServerPlayer withIt = GameTestPlayers.create(helper);
        try {
            GameTestStages.individual("trade_individual", stage -> stage.setTradeOffers(
                    new ArrayList<>(List.of(paperForEmeraldEntry()))));
            data.addStage(withIt.getUUID(), stageId);

            Villager villager = librarian(helper, 1);

            if (shownTo(paperForEmerald(), villager, withoutIt) != 0) {
                helper.fail("the player has not unlocked the individual stage gating emeralds, "
                        + "but the offer is shown to them anyway");
                return;
            }
            if (shownTo(paperForEmerald(), villager, withIt) != 1) {
                helper.fail("the player has unlocked the individual stage, but the same merchant "
                        + "still hides the offer - the filter is answering globally");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            data.removeStage(withIt.getUUID(), stageId);
        }
    }

    /**
     * The half that filtering at draw time could never do: unlocking gives the offer back to a
     * merchant that has long since chosen what it sells.
     */
    @GameTest(template = "empty")
    public static void unlockingGivesTheOfferBack(GameTestHelper helper) {
        StageData data = StageData.get(helper.getLevel());
        String stageId = GameTestStages.PREFIX + "trade_unlock";
        try {
            GameTestStages.global("trade_unlock", stage -> stage.setTradeOffers(
                    new ArrayList<>(List.of(paperForEmeraldEntry()))));

            ServerPlayer player = GameTestPlayers.create(helper);
            Villager villager = librarian(helper, 1);

            if (shownTo(paperForEmerald(), villager, player) != 0) {
                helper.fail("the gating stage is not unlocked, but the offer is already shown");
                return;
            }

            data.addStage(stageId);

            if (shownTo(paperForEmerald(), villager, player) != 1) {
                helper.fail("the stage was unlocked, but the same merchant still hides the offer "
                        + "- an unlock has to reach a merchant that already exists");
                return;
            }
            helper.succeed();
        } finally {
            GameTestStages.removeAll();
            // Unlocked state lives in SavedData and outlives both the test and the stage entry.
            data.removeStage(stageId);
        }
    }
}
