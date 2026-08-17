package net.bananemdnsa.historystages.data;

/**
 * Size limit for a stage travelling to the server in the save packet.
 *
 * <p>The packet carries the stage as a single string, and Minecraft caps string writes at a fixed
 * character count. Going over that throws while the packet is encoded, which kills the connection
 * without any usable error. Checking against {@link #MAX_STAGE_JSON} before sending lets the editor
 * report the problem instead.
 */
public final class StageJsonLimits {

    /** Character limit the save packet writes the stage JSON with. */
    public static final int MAX_STAGE_JSON = 65536;

    private StageJsonLimits() {}

    /** Returns true when the stage JSON can be sent without breaking the connection. */
    public static boolean fitsSavePacket(String stageJson) {
        return stageJson != null && stageJson.length() <= MAX_STAGE_JSON;
    }
}
