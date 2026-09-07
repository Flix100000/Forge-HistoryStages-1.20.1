package net.bananemdnsa.historystages.data.lock;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;

/**
 * Finds out which items merchants deal in, by running the recipes they are dealt from.
 *
 * <p>Minecraft keeps no list of trade goods. What it keeps is a set of small recipes for
 * <em>rolling</em> an offer — a handful per profession and level — and a recipe cannot be read,
 * only run. Mods add theirs in the same form. So the only honest way to answer "what can be
 * traded" is to run them all and look at what came out.
 *
 * <p><strong>The recipes are run directly, not through a merchant.</strong> Making a merchant and
 * asking for its offers is the obvious way and it is a dead end: that call refuses outright
 * anywhere but on a server, so a scan built on it comes back empty on a client and says nothing
 * about why. Running the recipes carries no such restriction — and it is the better answer in any
 * case, because a merchant is dealt only two of its level's recipes at random, so asking merchants
 * means making hundreds and hoping, while this covers every recipe exactly once.
 *
 * <p>Each recipe is still run several times, because some are random in themselves: the
 * enchanted-book trade picks an enchantment per offer, and a single run would name one book.
 *
 * <p><strong>The answer is good, not complete.</strong> A recipe that refuses to run outside a
 * real trade contributes nothing, and on a dedicated server a client's copy of the table holds
 * only the vanilla recipes — a mod's are added when a world loads its data, which a client never
 * does. That second gap is what the server's own answer is for; see {@code ClientTradeGoods}.
 * Both fail quietly on purpose: this list narrows a picker, and one missing item is a click's
 * worth of annoyance where a scan that breaks the editor is not.
 */
public final class TradeGoodsScanner {

    /**
     * How many times to run each recipe.
     *
     * <p>Once would do for the fixed ones, which are most of them. Eight is for the handful that
     * roll something themselves — a book's enchantment, an armourer's dye — where a single run
     * would put one arbitrary result in the list and leave its siblings out.
     */
    private static final int RUNS_PER_LISTING = 8;

    /** Novice through master. */
    private static final int MAX_LEVEL = 5;

    /**
     * The last scan, kept for the rest of the server's life.
     *
     * <p>Which recipes exist is settled when a world loads its data and does not change while it
     * runs. Cleared when the server stops, because the next one may load others.
     */
    private static List<TradePreview> cached = null;

    private TradeGoodsScanner() {
    }

    /** The scan, done once. Every call after the first is a field read. */
    public static List<TradePreview> cached(Level level) {
        if (cached == null) cached = scan(level);
        return cached;
    }

    /** Just the item ids from a set of offers, for the picker's "trade goods only" filter. */
    public static Set<String> itemIdsOf(List<TradePreview> offers) {
        Set<String> ids = new LinkedHashSet<>();
        for (TradePreview offer : offers) ids.addAll(offer.itemIds());
        return ids;
    }

    /** Forgets the scan. Called when the server stops. */
    public static void clearCache() {
        cached = null;
    }

    /**
     * Every distinct offer the merchant recipes were seen to produce.
     *
     * <p>Both halves of each are kept. A trade entry can gate either side, so throwing the prices
     * away would lose half the answers — and emeralds, which are the payment for nearly
     * everything.
     */
    public static List<TradePreview> scan(Level level) {
        // Keyed by what makes two rolls the same trade, so a recipe that rolls its own price does
        // not appear eight times at eight prices. First roll wins; see TradePreview#identity.
        Map<String, TradePreview> offers = new LinkedHashMap<>();
        if (level == null) return List.of();

        long startedAt = System.currentTimeMillis();
        // Someone for the recipes to be dealt to. Several of them ask what kind of villager they
        // are dealing with and hand back nothing for anything else. It never enters the world, and
        // it is never asked for its offers — that is the call that refuses.
        Villager trader = EntityType.VILLAGER.create(level);
        if (trader == null) return List.of();
        RandomSource random = level.getRandom();
        int recipes = 0;

        for (VillagerProfession profession : BuiltInRegistries.VILLAGER_PROFESSION) {
            Int2ObjectMap<VillagerTrades.ItemListing[]> byLevel =
                    VillagerTrades.TRADES.get(profession);
            if (byLevel == null) continue;
            for (int merchantLevel = 1; merchantLevel <= MAX_LEVEL; merchantLevel++) {
                VillagerTrades.ItemListing[] listings = byLevel.get(merchantLevel);
                if (listings == null) continue;
                // Set before running, because a recipe may read the trader's profession or level.
                trader.setVillagerData(
                        new VillagerData(VillagerType.PLAINS, profession, merchantLevel));
                ResourceLocation professionId =
                        BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
                recipes += run(listings, trader, random, offers,
                        professionId == null ? "" : professionId.toString(), merchantLevel);
            }
        }

        for (Int2ObjectMap.Entry<VillagerTrades.ItemListing[]> entry
                : VillagerTrades.WANDERING_TRADER_TRADES.int2ObjectEntrySet()) {
            // Its own two tiers are "generic" and "rare"; both are level 1 as far as a lock is
            // concerned, because a wandering trader never has another.
            recipes += run(entry.getValue(), trader, random, offers,
                    TradePreview.WANDERING_TRADER, 1);
        }

        trader.discard();
        // Timed because this runs while a screen opens, and "the game hiccuped once" is otherwise
        // a mystery nobody can attribute.
        DebugLogger.runtime("Trade Goods", "scan",
                "ran " + recipes + " merchant recipes in "
                        + (System.currentTimeMillis() - startedAt) + "ms, found "
                        + offers.size() + " distinct offers");
        return List.copyOf(offers.values());
    }

    /** @return how many recipes were run */
    private static int run(VillagerTrades.ItemListing[] listings, Villager trader,
                           RandomSource random, Map<String, TradePreview> offers,
                           String professionId, int level) {
        int ran = 0;
        for (VillagerTrades.ItemListing listing : listings) {
            if (listing == null) continue;
            for (int attempt = 0; attempt < RUNS_PER_LISTING; attempt++) {
                try {
                    MerchantOffer offer = listing.getOffer(trader, random);
                    if (offer == null) continue;
                    TradePreview preview = previewOf(offer, professionId, level);
                    if (preview != null) offers.putIfAbsent(preview.identity(), preview);
                } catch (Exception recipeRefused) {
                    // One mod's recipe will not run outside a real trade. Give up on that one and
                    // keep the rest — see the note on the class about why this is quiet.
                    break;
                }
            }
            ran++;
        }
        return ran;
    }

    /** Null when the offer hands over nothing recognisable, which no lock could name anyway. */
    private static TradePreview previewOf(MerchantOffer offer, String professionId, int level) {
        String resultId = idOf(offer.getResult());
        if (resultId == null) return null;
        String costAId = idOf(offer.getCostA());
        String costBId = idOf(offer.getCostB());
        return new TradePreview(professionId, level,
                resultId, offer.getResult().getCount(),
                costAId, costAId == null ? 0 : offer.getCostA().getCount(),
                costBId, costBId == null ? 0 : offer.getCostB().getCount());
    }

    private static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? null : key.toString();
    }
}
