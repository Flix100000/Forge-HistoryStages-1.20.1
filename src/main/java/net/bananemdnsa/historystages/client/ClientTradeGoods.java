package net.bananemdnsa.historystages.client;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.data.lock.TradeGoodsScanner;
import net.bananemdnsa.historystages.data.lock.TradePreview;
import net.minecraft.world.level.Level;

/**
 * The items merchants deal in, as the editor's trades picker sees them.
 *
 * <p>Filled from two places, and it takes whatever either of them knows.
 *
 * <p>The client works it out itself, straight away, by running the merchant recipes it has. In a
 * single-player world that is the whole answer: the game and its world share one copy of the
 * recipe table, so everything a mod added is already in it.
 *
 * <p>On a dedicated server it is not. A mod's recipes are added to the table when a world loads
 * its data, and a client never does that — its copy holds the vanilla recipes and nothing else.
 * So the server is asked as well, and its answer is added to what is already here. Adding rather
 * than replacing, because neither side ever knows something the other should overrule: the
 * server's list is the larger one, the client's arrives first, and the union is right either way.
 *
 * <p>Empty is a real answer and has to leave the picker unfiltered. A filter that hides everything
 * looks like a broken screen rather than a narrow one.
 */
public final class ClientTradeGoods {

    /** Keyed by what makes two rolls the same trade, so the two sources cannot double up. */
    private static final Map<String, TradePreview> offers = new LinkedHashMap<>();
    private static Set<String> goods = Set.of();
    private static boolean scannedLocally;

    private ClientTradeGoods() {
    }

    /**
     * Works the local list out if that has not happened yet.
     *
     * <p>Called when the trades picker is about to open, so there is something to narrow to even
     * if the server never answers.
     */
    public static void scanLocally(Level level) {
        if (scannedLocally || level == null) return;
        scannedLocally = true;
        addAll(TradeGoodsScanner.scan(level));
    }

    /** Adds what the server knows. Its list is a superset on a dedicated server. */
    public static void addFromServer(List<TradePreview> serverOffers) {
        addAll(serverOffers);
    }

    /** Every known offer, in the order the recipes were read. */
    public static List<TradePreview> offers() {
        return List.copyOf(offers.values());
    }

    public static boolean contains(String itemId) {
        return goods.contains(itemId);
    }

    /** Whether there is a list at all. An empty one must not be allowed to hide every item. */
    public static boolean isEmpty() {
        return goods.isEmpty();
    }

    public static int size() {
        return goods.size();
    }

    /** Forgets everything. The next server may load a different set of mods. */
    public static void clear() {
        offers.clear();
        goods = Set.of();
        scannedLocally = false;
    }

    private static void addAll(List<TradePreview> incoming) {
        if (incoming == null || incoming.isEmpty()) return;
        for (TradePreview offer : incoming) offers.putIfAbsent(offer.identity(), offer);
        Set<String> merged = new LinkedHashSet<>();
        for (TradePreview offer : offers.values()) merged.addAll(offer.itemIds());
        goods = Set.copyOf(merged);
    }
}
