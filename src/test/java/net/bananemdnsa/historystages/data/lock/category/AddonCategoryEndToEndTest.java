package net.bananemdnsa.historystages.data.lock.category;

import net.bananemdnsa.historystages.api.lock.CategoryStorage;

import net.bananemdnsa.historystages.api.lock.AddonLockCategory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole path a real addon walks, in order, in one test.
 *
 * <p>This is the acceptance criterion for addon-facing lock categories, and it is deliberately
 * written to read like the example an addon author would copy: declare what you store, declare
 * how to recognise it, register, and ask.
 *
 * <p>It drives {@code CategoryLockResolver} rather than {@code CategoryLocks} because the latter
 * reaches into {@code StageManager}, and the test runtime classpath carries no Minecraft. The
 * stage maps are therefore supplied by hand — which is all {@code CategoryLocks} does anyway.
 */
class AddonCategoryEndToEndTest {

    /** What the addon writes into a stage file. */
    record Trade(String soldItem) {}

    /** What the addon actually has in hand when the game asks. */
    record Offer(String soldItem, int emeralds) {}

    private static final String CATEGORY_ID = "mymod:villagertrades";

    private static AddonLockCategory<Trade> declareCategory() {
        return AddonLockCategory.<Trade>builder(CATEGORY_ID)
                .tabLangKey("editor.mymod.tab.villagertrades")
                .tooltipLangKey("editor.mymod.tooltip.villagertrades")
                .storage(CategoryStorage.gson(Trade.class))
                .matcher(Offer.class, (Trade entry, Offer offer) -> entry.soldItem().equals(offer.soldItem()))
                .build();
    }

    @AfterEach
    void resetRegistry() {
        LockCategories.resetForTesting();
    }

    @Test
    void anAddonCanRegisterAStoreAndGateItsOwnObjects() {
        // 1. declare and register, inside the window
        AddonLockCategory<Trade> trades = declareCategory();
        LockCategories.register(trades);
        LockCategories.freeze();

        assertNotNull(LockCategories.byId(CATEGORY_ID), "the category should be findable by id");
        assertEquals(List.of(CATEGORY_ID), LockCategories.addonIds());

        // 2. a stage gates one of the addon's things
        StageEntry bronzeAge = new StageEntry();
        bronzeAge.setDisplayName("Bronze Age");
        trades.write(bronzeAge, List.of(new Trade("minecraft:emerald")));

        // 3. that survives being written to a file and read back
        StageEntry reloaded = new Gson().fromJson(bronzeAge.toJson(), StageEntry.class);
        assertEquals(List.of(new Trade("minecraft:emerald")), trades.read(reloaded),
                "the addon's entries must survive a save/load round trip");

        Map<String, StageEntry> stages = new LinkedHashMap<>();
        stages.put("bronze", reloaded);

        // 4. a player without the stage is gated, and is told which stage they need
        Offer gatedOffer = new Offer("minecraft:emerald", 3);
        assertTrue(CategoryLockResolver.isLocked(trades, gatedOffer, stages, StageStateView.NONE_UNLOCKED));
        assertEquals(List.of("bronze"),
                CategoryLockResolver.missingStages(trades, gatedOffer, stages, StageStateView.NONE_UNLOCKED));

        // 5. the same player, once they have the stage, is not
        StageStateView hasBronze = StageStateView.of(Set.of("bronze"));
        assertFalse(CategoryLockResolver.isLocked(trades, gatedOffer, stages, hasBronze));
        assertTrue(CategoryLockResolver.missingStages(trades, gatedOffer, stages, hasBronze).isEmpty());

        // 6. something the addon never gated is never locked
        Offer untouchedOffer = new Offer("minecraft:diamond", 3);
        assertFalse(CategoryLockResolver.isLocked(trades, untouchedOffer, stages, StageStateView.NONE_UNLOCKED));
    }

    /**
     * The scenario the raw addons block exists for: the server has the stage data, but the mod
     * that owns it is gone. Nothing may crash, nothing may gate, and the data must still be
     * sitting in the file afterwards.
     */
    @Test
    void aStageKeepsAddonDataWhileTheOwningModIsUninstalled() {
        StageEntry bronzeAge = new StageEntry();
        declareCategory().write(bronzeAge, List.of(new Trade("minecraft:emerald")));
        String onDisk = bronzeAge.toJson();

        // the addon never registers this run
        LockCategories.freeze();
        assertTrue(LockCategories.addonIds().isEmpty());

        StageEntry loadedWithoutTheAddon = new Gson().fromJson(onDisk, StageEntry.class);
        assertTrue(loadedWithoutTheAddon.addonCategoryIds().contains(CATEGORY_ID),
                "the data must still be there even though nothing understands it");
        assertTrue(loadedWithoutTheAddon.toJson().contains(CATEGORY_ID),
                "and it must survive being written back out again");
    }
}
