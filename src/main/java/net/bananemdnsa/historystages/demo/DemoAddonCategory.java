package net.bananemdnsa.historystages.demo;

import java.util.ArrayList;
import java.util.List;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.api.trigger.RegisterTriggerTypesEvent;
import net.bananemdnsa.historystages.api.lock.AddonLockCategory;
import net.bananemdnsa.historystages.api.lock.CategoryStorage;
import net.bananemdnsa.historystages.api.lock.RegisterLockCategoriesEvent;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * A stand-in addon, so the addon path can be exercised before a real addon exists — and so there
 * is a worked example to point an addon author at.
 *
 * <p>Off unless the game is started with {@code -Dhistorystages.demoCategory=true}, which means it
 * never exists for a player. It is written the way a real addon would write it, using only the
 * public path: register a category, say how its entries serialise, say how to recognise one of its
 * objects at runtime.
 *
 * <p>What it deliberately does not do is hook the game. A real addon owns the moment its thing is
 * about to happen and asks {@code CategoryLocks} then; there is nothing here to hook.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class DemoAddonCategory {

    /** Namespaced like any addon must be — {@code historystages} is reserved for the built-ins. */
    public static final String CATEGORY_ID = "hsdemo:relics";

    /** The stand-in addon's own auto-trigger type. Namespaced like any addon's must be. */
    public static final String TRIGGER_TYPE = "hsdemo:relic_found";

    private static final String ENABLED_PROPERTY = "historystages.demoCategory";

    /**
     * The category this addon built, kept so its editor can use it.
     *
     * <p>Worth copying: the builder already returns a fully typed {@code LockCategory<String>}.
     * Looking it back up out of the registry hands back a {@code LockCategory<?>} and forces an
     * unchecked cast — an addon should not have to cast to reach a thing it registered itself.
     */
    private static AddonLockCategory<String> category;

    private DemoAddonCategory() {}

    public static boolean enabled() {
        return Boolean.getBoolean(ENABLED_PROPERTY);
    }

    /** The registered category. Null until the registration event has run. */
    static AddonLockCategory<String> category() {
        return category;
    }

    @SubscribeEvent
    public static void onRegisterCategories(RegisterLockCategoriesEvent event) {
        if (!enabled()) return;

        category = AddonLockCategory.<String>builder(CATEGORY_ID)
                .tabLangKey("editor.historystages.demo.tab.relics")
                .tooltipLangKey("editor.historystages.demo.tooltip.relics")
                .storage(CategoryStorage.gson(String.class))
                .matcher(String.class, String::equals)
                .build();
        event.register(category);
    }

    @SubscribeEvent
    public static void onRegisterTriggerTypes(RegisterTriggerTypesEvent event) {
        if (!enabled()) return;
        event.register(TRIGGER_TYPE, RelicFoundTrigger.class, StageScope.GLOBAL);
        // Carries a number and no id, so it cannot be authored by picking from a list.
        event.register(RelicHoardTrigger.TYPE, RelicHoardTrigger.class);
    }

    /**
     * What a maintainer may pick from. A real addon would ask its own registry; this makes up
     * enough rows that searching and scrolling are worth trying.
     */
    public static List<String> candidateRelics() {
        List<String> relics = new ArrayList<>(List.of(
                "hsdemo:amber_pendant", "hsdemo:bone_flute", "hsdemo:cracked_seal",
                "hsdemo:dusty_ledger", "hsdemo:ember_shard"));
        for (int i = 1; i <= 60; i++) {
            relics.add(String.format("hsdemo:relic_%02d", i));
        }
        return relics;
    }
}
