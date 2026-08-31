package net.bananemdnsa.historystages.data.dependency;

import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * One individual stage a group demands, and of whom.
 *
 * <p>{@code all_online} and {@code all_ever} ask the whole server, which is what a global stage
 * wants: it unlocks once, for everybody, so "everybody is ready" is the honest question. An
 * individual stage unlocks per player, and asking the same question there builds a chain nobody
 * can start — the first player to reach stage two is by definition the only one who has stage
 * one. {@link #MODE_PLAYER} asks only whoever is doing the research, which is what an
 * individual-to-individual prerequisite actually means.
 */
public class IndividualStageDep {

    /** Everyone currently online must hold the stage. */
    public static final String MODE_ALL_ONLINE = "all_online";
    /** Everyone the server has ever seen must hold the stage. */
    public static final String MODE_ALL_EVER = "all_ever";
    /** Only the player doing the research must hold the stage. */
    public static final String MODE_PLAYER = "player";

    /**
     * Every mode, in the order the editor offers them.
     *
     * <p>One list, read by the picker, the loader's validation and the labels alike. Three copies
     * of the same three strings is how a fourth mode ends up accepted in one place and thrown
     * away in another.
     */
    public static final List<String> MODES = List.of(MODE_ALL_ONLINE, MODE_ALL_EVER, MODE_PLAYER);

    /**
     * The modes offered on a global stage, which is {@link #MODES} without {@link #MODE_PLAYER}.
     *
     * <p>Not because it could not be answered there — the checker would ask whoever is at the
     * pedestal, and that works. Because of what it would then mean: a global stage unlocks once
     * for everybody, so a personal gate on it lets the first qualifying player open the stage for
     * the whole server, including everyone who does not hold the prerequisite. That reads as
     * "everyone needs it" and does the opposite, and the two modes above are what "everyone needs
     * it" is actually spelled with.
     */
    public static final List<String> GLOBAL_MODES = List.of(MODE_ALL_ONLINE, MODE_ALL_EVER);

    /** The modes a stage in this scope may use. */
    public static List<String> modesFor(boolean individual) {
        return individual ? MODES : GLOBAL_MODES;
    }

    @SerializedName("stage_id")
    private String stageId;

    private String mode;

    public IndividualStageDep() {
        this.mode = MODE_ALL_ONLINE;
    }

    public IndividualStageDep(String stageId, String mode) {
        this.stageId = stageId;
        this.mode = mode != null ? mode : MODE_ALL_ONLINE;
    }

    /** Whether {@code mode} is one this mod knows how to check. */
    public static boolean isValidMode(String mode) {
        return MODES.contains(mode);
    }

    public String getStageId() { return stageId; }
    public String getMode() { return mode != null ? mode : MODE_ALL_ONLINE; }

    public void setStageId(String stageId) { this.stageId = stageId; }
    public void setMode(String mode) { this.mode = mode; }

    public boolean isAllEver() { return MODE_ALL_EVER.equals(mode); }

    public boolean isPlayer() { return MODE_PLAYER.equals(mode); }

    public IndividualStageDep copy() {
        return new IndividualStageDep(stageId, mode);
    }
}
