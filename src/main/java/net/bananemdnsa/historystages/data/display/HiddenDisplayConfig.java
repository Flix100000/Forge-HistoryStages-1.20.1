package net.bananemdnsa.historystages.data.display;

import com.google.gson.annotations.SerializedName;

/**
 * Per-stage configuration for hiding or replacing a locked item's name and tooltip.
 * Serialized under the {@code hidden_display} key of a {@link StageEntry}.
 *
 * <p>All effects apply only while the item is locked for the viewing player. With every
 * field at its default the stage behaves exactly as before (no display changes).</p>
 */
public class HiddenDisplayConfig {

    @SerializedName("name_mode")
    private String nameMode;     // off | hidden | replace; null → off

    @SerializedName("name_text")
    private String nameText;     // stage default name when nameMode == replace

    @SerializedName("tooltip_mode")
    private String tooltipMode;  // off | hidden | replace; null → off

    @SerializedName("tooltip_text")
    private String tooltipText;  // stage default tooltip when tooltipMode == replace ("\n" = multi-line)

    @SerializedName("show_lock_hints")
    private Boolean showLockHints; // null → true

    public HiddenDisplayConfig() {
    }

    public DisplayMode getNameMode() {
        return DisplayMode.parse(nameMode, DisplayMode.OFF);
    }

    public DisplayMode getTooltipMode() {
        return DisplayMode.parse(tooltipMode, DisplayMode.OFF);
    }

    public String getNameText() {
        return nameText != null ? nameText : "";
    }

    public String getTooltipText() {
        return tooltipText != null ? tooltipText : "";
    }

    public boolean isShowLockHints() {
        return showLockHints == null || showLockHints;
    }

    /** True when this config changes nothing — name and tooltip both OFF and lock hints shown. */
    public boolean isNoop() {
        return getNameMode() == DisplayMode.OFF && getTooltipMode() == DisplayMode.OFF && isShowLockHints();
    }

    public void setNameMode(DisplayMode mode) {
        this.nameMode = (mode != null ? mode : DisplayMode.OFF).serialize();
    }

    public void setTooltipMode(DisplayMode mode) {
        this.tooltipMode = (mode != null ? mode : DisplayMode.OFF).serialize();
    }

    public void setNameText(String text) {
        this.nameText = (text != null && !text.isEmpty()) ? text : null;
    }

    public void setTooltipText(String text) {
        this.tooltipText = (text != null && !text.isEmpty()) ? text : null;
    }

    public void setShowLockHints(boolean show) {
        this.showLockHints = show;
    }

    public HiddenDisplayConfig copy() {
        HiddenDisplayConfig c = new HiddenDisplayConfig();
        c.nameMode = this.nameMode;
        c.nameText = this.nameText;
        c.tooltipMode = this.tooltipMode;
        c.tooltipText = this.tooltipText;
        c.showLockHints = this.showLockHints;
        return c;
    }
}
