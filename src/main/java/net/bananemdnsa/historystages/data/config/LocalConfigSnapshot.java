package net.bananemdnsa.historystages.data.config;

import net.bananemdnsa.historystages.Config;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Map;

/**
 * The client's own config values, as they stood before a server pushed its own over them.
 *
 * <p>A sync writes the server's values straight into the spec objects in memory; the player's file
 * on disk is never touched. Nothing used to put those values back, so a client kept a visited
 * server's settings until the game was restarted. That was barely noticeable while only gameplay
 * values travelled. Now that the visual ones do too, leaving a server would carry its tooltips,
 * lock icons and screen overlays straight into the next singleplayer world.
 *
 * <p>The snapshot is taken by the sync handler itself, immediately before it applies anything —
 * the one moment the spec is certain to hold the local file's contents. An earlier design hooked
 * {@code ModConfigEvent.Loading} instead, which fails twice over: that event fires on a dedicated
 * server as well, and {@code LoadedConfig.save()} fires {@code Reloading}, so an admin's own save
 * would have been recorded as if it were the local baseline.
 *
 * <p>Deliberately free of {@code net.minecraft.client} imports so a dedicated server can load it
 * and a GameTest can exercise it. Whether a snapshot should be taken at all is a question only a
 * client can answer, so that decision stays at the call sites — see
 * {@code SyncVisualConfigPacket.apply}.
 */
public final class LocalConfigSnapshot {

    /** Null means "no server has overwritten this spec during this session". */
    private static Map<String, String> visual;
    private static Map<String, String> gameplay;

    private LocalConfigSnapshot() {}

    /**
     * Records the spec's current values, unless they were already recorded this session.
     *
     * <p>Must be called before the incoming values are written. The "already recorded" guard is
     * what makes a second sync harmless: an admin saving mid-session pushes another round of
     * server values, and overwriting the snapshot with those would leave the player with the
     * server's settings after they disconnect — the exact bug this class exists to prevent.
     */
    public static void rememberBeforeSync(ModConfigSpec spec) {
        if (spec == Config.VISUAL_SPEC) {
            if (visual == null) visual = ConfigSpecCodec.collect(spec);
        } else if (spec == Config.GAMEPLAY_SPEC) {
            if (gameplay == null) gameplay = ConfigSpecCodec.collect(spec);
        }
    }

    /**
     * Puts the local values back and forgets them, so the next server visit starts clean.
     *
     * <p>A no-op when no sync ever arrived, which is the singleplayer case: there the client and
     * the integrated server share one spec object, so nothing was ever overwritten.
     *
     * @return how many values were restored, for the caller's log line
     */
    public static int restore() {
        int restored = 0;
        if (visual != null) {
            restored += ConfigSpecCodec.apply(
                    Config.VISUAL_SPEC, visual, true, ConfigSpecCodec.NO_EXTRA_CHECK);
            visual = null;
        }
        if (gameplay != null) {
            restored += ConfigSpecCodec.apply(
                    Config.GAMEPLAY_SPEC, gameplay, true, ConfigSpecCodec.NO_EXTRA_CHECK);
            gameplay = null;
        }
        return restored;
    }

    /** True while a server's values are sitting in at least one spec. */
    public static boolean holdsServerValues() {
        return visual != null || gameplay != null;
    }
}
