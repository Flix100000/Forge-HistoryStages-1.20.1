package net.bananemdnsa.historystages.data.config;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired once so other mods can add their own config sections to the HistoryStages config screen.
 *
 * <p>Registration is legal only during dispatch: when it ends the registry freezes, and
 * everything that walks it — the config screen's card layout, the common-value publisher — may
 * then assume the list never changes. An always-open registry would let a server and a client
 * disagree about which sections exist.
 *
 * <pre>{@code
 * modEventBus.addListener(RegisterConfigSectionsEvent.class, event -> event.register(
 *         AddonConfigSection.builder("mymod:trades")
 *                 .titleLangKey("config.mymod.trades.title")
 *                 .side(ConfigSide.COMMON)
 *                 .field(HIDE_TRADES)
 *                 .build()));
 * }</pre>
 */
public class RegisterConfigSectionsEvent extends Event implements IModBusEvent {

    public void register(AddonConfigSection section) {
        AddonConfigSections.register(section);
    }
}
