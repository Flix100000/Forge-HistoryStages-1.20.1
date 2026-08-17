package net.bananemdnsa.historystages.util;

/**
 * The project's public URLs, in one place.
 *
 * <p>Deliberately free of Minecraft and loader imports: the Fabric and Forge ports show the
 * same links, and three copies of a Discord invite would drift apart the first time one is
 * rotated.
 */
public final class ModLinks {

    /** Full documentation — the link worth reaching from inside the editor. */
    public static final String WIKI = "https://github.com/Flix100000/History-Stages/wiki";

    /** Support and development chat. */
    public static final String DISCORD = "https://discord.gg/BeZzxyZ9c4";

    /**
     * Bug reports. Points at the template directly rather than at the issue list: blank
     * issues are disabled, so anyone landing anywhere else has to find the right form first.
     * Pairs with the version the credits dialog shows.
     */
    public static final String BUG_REPORT =
            "https://github.com/Flix100000/History-Stages/issues/new?template=bug_report.yml";

    /** Feature requests, same reasoning as {@link #BUG_REPORT}. */
    public static final String FEATURE_REQUEST =
            "https://github.com/Flix100000/History-Stages/issues/new?template=feature_request.yml";

    private ModLinks() {
    }
}
