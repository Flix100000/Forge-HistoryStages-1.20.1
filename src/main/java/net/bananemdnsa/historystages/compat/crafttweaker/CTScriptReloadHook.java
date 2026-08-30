package net.bananemdnsa.historystages.compat.crafttweaker;

import net.bananemdnsa.historystages.api.stage.StageEvent;
import net.bananemdnsa.historystages.compat.script.ScriptCallResolution;
import net.bananemdnsa.historystages.compat.script.ScriptStageListeners;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * The NeoForge-side wiring CraftTweaker needs: forward {@link StageEvent} to whatever the scripts
 * subscribed, and drop those subscriptions before the scripts run again.
 *
 * <p><strong>Why not CraftTweaker's own event bus:</strong> 21.0 keeps everything about script
 * runs under {@code impl.script.scriptrun} — there is no public {@code ScriptRunEvent} to listen
 * to, and no documented way for a third-party mod to add an event type to CraftTweaker's bus.
 * Our own listener list works either way, and the surface scripts see is identical.
 *
 * <p><strong>Why {@link AddReloadListenerEvent}:</strong> CraftTweaker loads its scripts as a
 * reload listener, and this event fires while those listeners are being assembled — before they
 * run, on world load and on every {@code /reload}. Clearing there is what stops a script from
 * being subscribed twice after the first reload and n+1 times after n of them.
 */
public final class CTScriptReloadHook {

    private CTScriptReloadHook() {}

    private static final Logger LOGGER = LogUtils.getLogger();

    public static void register(IEventBus bus) {
        // Listener failures belong in the game log next to the other script errors, not on
        // stderr. The registry keeps the sink settable so it stays loadable in a unit test,
        // where nothing Minecraft-side is on the classpath.
        ScriptStageListeners.setErrorSink(LOGGER::warn);

        bus.addListener(StageEvent.Unlocked.class, event ->
                ScriptStageListeners.fireUnlocked(event.getStageId(), event.getDisplayName()));
        bus.addListener(StageEvent.Locked.class, event ->
                ScriptStageListeners.fireLocked(event.getStageId(), event.getDisplayName()));
        bus.addListener(StageEvent.IndividualUnlocked.class, event ->
                ScriptStageListeners.fireIndividualUnlocked(
                        event.getStageId(), event.getDisplayName(), event.getPlayerUUID()));
        bus.addListener(StageEvent.IndividualLocked.class, event ->
                ScriptStageListeners.fireIndividualLocked(
                        event.getStageId(), event.getDisplayName(), event.getPlayerUUID()));

        bus.addListener(AddReloadListenerEvent.class, event -> {
            ScriptStageListeners.clear();
            // A corrected script gets to complain again about ids it still has wrong.
            ScriptCallResolution.resetWarnings();
        });
    }

    /**
     * Resolves the UUID an individual {@link StageEvent} carries into the player ZenScript wants.
     * Null when that player is offline.
     */
    @Nullable
    static Player playerOf(UUID uuid) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getPlayerList().getPlayer(uuid);
    }
}
