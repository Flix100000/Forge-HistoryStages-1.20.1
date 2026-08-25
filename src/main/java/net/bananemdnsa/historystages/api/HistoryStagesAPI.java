package net.bananemdnsa.historystages.api;

/**
 * A version marker for the HistoryStages addon API.
 *
 * <p>Deliberately holds no registration method. Everything is registered through the NeoForge
 * mod-bus events named {@code Register…Event}, one per extension point, because that is the shape
 * a Minecraft mod author already knows — and a static facade beside it would be a second way to
 * do the same thing.
 *
 * <p><strong>Documentation lives in the wiki:</strong>
 * <a href="https://github.com/Flix100000/History-Stages/wiki/Addon-Development">Addon
 * Development</a>. The working example is the demo addon under
 * {@code net.bananemdnsa.historystages.demo}, which exercises all five extension points and —
 * enforced by a test — reaches for nothing outside this package.
 */
public final class HistoryStagesAPI {

    /**
     * The generation of this API surface, equal to the mod's major version.
     *
     * <p>6.x is generation 6; a breaking change to anything under {@code api} waits for 7.0. That
     * is what makes the loader the one place compatibility is checked: an addon writes
     * {@code versionRange="[6.0,7.0)"} against {@code historystages} in its {@code mods.toml} and
     * is refused at load time rather than at first call. This constant exists to be read in a log
     * line or a crash report, not as a second gate.
     */
    public static final int API_VERSION = 6;

    private HistoryStagesAPI() {}
}