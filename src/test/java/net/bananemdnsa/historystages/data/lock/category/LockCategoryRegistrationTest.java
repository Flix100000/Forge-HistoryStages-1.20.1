package net.bananemdnsa.historystages.data.lock.category;

import net.bananemdnsa.historystages.api.lock.CategoryStorage;

import net.bananemdnsa.historystages.api.lock.AddonLockCategory;

import net.bananemdnsa.historystages.api.lock.LockCategory;

import java.util.List;

import net.bananemdnsa.historystages.data.StageEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockCategoryRegistrationTest {

    record Trade(String id) {}

    private static AddonLockCategory<Trade> tradeCategory(String id) {
        return AddonLockCategory.<Trade>builder(id)
                .tabLangKey("editor.mymod.tab.things")
                .tooltipLangKey("editor.mymod.tooltip.things")
                .storage(CategoryStorage.gson(Trade.class))
                .build();
    }

    @AfterEach
    void resetRegistry() {
        LockCategories.resetForTesting();
    }

    @Test
    void theFifteenBuiltInsAreThereBeforeAnyoneRegisters() {
        assertEquals(15, LockCategories.all().size());
        assertNotNull(LockCategories.byId("historystages:items"));
    }

    @Test
    void aCategoryRegisteredInTheWindowShowsUp() {
        LockCategories.register(tradeCategory("mymod:villagertrades"));

        assertNotNull(LockCategories.byId("mymod:villagertrades"));
        assertEquals(16, LockCategories.all().size());
    }

    @Test
    void addonCategoriesComeAfterTheBuiltIns() {
        LockCategories.register(tradeCategory("mymod:villagertrades"));

        List<String> ids = LockCategories.ids();
        assertEquals("historystages:items", ids.get(0));
        assertEquals("mymod:villagertrades", ids.get(ids.size() - 1));
    }

    @Test
    void registeringAfterTheFreezeIsRejected() {
        LockCategories.freeze();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> LockCategories.register(tradeCategory("mymod:toolate")));
        assertTrue(thrown.getMessage().contains("mymod:toolate"),
                "the error should name the category that tried to register: " + thrown.getMessage());
    }

    @Test
    void aDuplicateIdIsRejected() {
        LockCategories.register(tradeCategory("mymod:villagertrades"));

        assertThrows(IllegalArgumentException.class,
                () -> LockCategories.register(tradeCategory("mymod:villagertrades")));
    }

    @Test
    void anAddonMayNotShadowABuiltIn() {
        // AddonLockCategory already refuses the historystages namespace, so this uses a bare
        // LockCategory to prove the registry itself also guards the built-in ids.
        LockCategory<String> impostor = new LockCategory<>() {
            @Override public String id() { return "historystages:items"; }
            @Override public String tabLangKey() { return "editor.mymod.tab.things"; }
            @Override public String tooltipLangKey() { return "editor.mymod.tooltip.things"; }
            @Override public List<String> read(StageEntry stage) { return List.of(); }
            @Override public void write(StageEntry stage, List<String> entries) {}
        };

        assertThrows(IllegalArgumentException.class, () -> LockCategories.register(impostor));
    }

    @Test
    void freezingTwiceIsHarmless() {
        LockCategories.freeze();
        LockCategories.freeze();
        assertTrue(LockCategories.isFrozen());
    }

    @Test
    void resetForTestingUnfreezesAndRestoresTheBuiltIns() {
        LockCategories.register(tradeCategory("mymod:villagertrades"));
        LockCategories.freeze();

        LockCategories.resetForTesting();

        assertTrue(!LockCategories.isFrozen());
        assertEquals(15, LockCategories.all().size());
    }

    @Test
    void aRegisteredCategoryStoresThroughTheAddonsBlock() {
        AddonLockCategory<Trade> category = tradeCategory("mymod:villagertrades");
        LockCategories.register(category);
        LockCategories.freeze();

        StageEntry stage = new StageEntry();
        category.write(stage, List.of(new Trade("minecraft:emerald")));

        assertTrue(stage.toJson().contains("mymod:villagertrades"));
    }
}
