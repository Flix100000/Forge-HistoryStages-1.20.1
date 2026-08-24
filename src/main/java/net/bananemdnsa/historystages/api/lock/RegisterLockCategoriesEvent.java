package net.bananemdnsa.historystages.api.lock;

import net.bananemdnsa.historystages.data.lock.category.LockCategories;

import net.bananemdnsa.historystages.api.lock.LockCategory;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired once so other mods can add their own lock categories.
 *
 * <p>This is the only moment registration is legal: when dispatch ends the registry freezes, and
 * everything that walks it — editor tabs, dual-phase detection, sync — may then assume the list
 * never changes again. An always-open registry would need invalidation everywhere and would let
 * a server and a client disagree about which categories exist.
 *
 * <pre>{@code
 * modEventBus.addListener(RegisterLockCategoriesEvent.class, event -> event.register(
 *         AddonLockCategory.<Trade>builder("mymod:villagertrades")
 *                 .tabLangKey("editor.mymod.tab.villagertrades")
 *                 .tooltipLangKey("editor.mymod.tooltip.villagertrades")
 *                 .storage(CategoryStorage.gson(Trade.class))
 *                 .build()));
 * }</pre>
 */
public class RegisterLockCategoriesEvent extends Event implements IModBusEvent {

    public void register(LockCategory<?> category) {
        LockCategories.register(category);
    }
}
