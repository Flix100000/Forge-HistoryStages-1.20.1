package net.bananemdnsa.historystages.data.settings;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired once so other mods can add their own per-stage settings groups.
 *
 * <p>Registration is legal only during dispatch: when it ends the registry freezes, and
 * everything that walks it — the settings screen's card layout, the lang parity check, sync —
 * may then assume the list never changes. An always-open registry would let a server and a
 * client disagree about which groups exist.
 *
 * <pre>{@code
 * modEventBus.addListener(RegisterStageSettingsGroupsEvent.class, event -> event.register(
 *         StageSettingsGroup.builder("mymod:trades")
 *                 .titleLangKey("settings.mymod.trades.title")
 *                 .field(HIDE_TRADES)
 *                 .build()));
 * }</pre>
 */
public class RegisterStageSettingsGroupsEvent extends Event implements IModBusEvent {

    public void register(StageSettingsGroup group) {
        StageSettingsGroups.register(group);
    }
}
