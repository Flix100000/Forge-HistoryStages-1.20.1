package net.bananemdnsa.historystages.data.display;

/**
 * A lock entry (item, tag or mod) that may carry per-entry REPLACE text overrides for the
 * stage's hidden-display config. {@code null} means "use the stage default".
 */
public interface TextOverrideHolder {
    String getNameTextOverride();
    String getTooltipTextOverride();
}
