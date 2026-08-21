package net.bananemdnsa.historystages.data.settings;

/**
 * The six shapes a stage setting can take.
 *
 * <p>Deliberately a closed set: HistoryStages renders, validates and stores every field itself,
 * so a kind it does not know is a kind it cannot draw. Addons that need something else are the
 * reason a "bring your own screen" tier exists as a possible later addition — not a reason to
 * open this enum.
 */
public enum SettingKind {
    BOOL,
    INTEGER,
    TEXT,
    CHOICE,
    ITEM,
    /**
     * Like {@link #TEXT} in storage — a plain {@code String} — but rendered as a button that opens
     * the mod's wrapping, previewing text dialog instead of a one-line {@code EditBox}. For values
     * that carry format codes or placeholders, which are otherwise edited blind.
     */
    LONG_TEXT
}
