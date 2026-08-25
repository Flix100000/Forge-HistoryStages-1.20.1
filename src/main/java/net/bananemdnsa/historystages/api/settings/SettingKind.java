package net.bananemdnsa.historystages.api.settings;

/**
 * The seven shapes a stage setting can take.
 *
 * <p>Deliberately a closed set: HistoryStages renders, validates and stores every field itself,
 * so a kind it does not know is a kind it cannot draw. {@link #CUSTOM_SCREEN} is the answer for
 * a value only the addon knows how to edit — it keeps the value a plain string and hands the
 * editing to a screen the addon supplies, which is why it is not a reason to open this enum.
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
    LONG_TEXT,

    /**
     * Like {@link #TEXT} in storage — a plain {@code String} — but rendered as a button that opens
     * a screen the addon supplies, through the client-side {@code CustomFieldScreens} registry.
     *
     * <p>The escape hatch from the fixed kinds: whatever an addon needs that none of the others
     * express, it draws itself. Deliberately no new storage shape, so the read, the write, the
     * sync and the scope handling are all paths that already existed — what the string means is
     * the addon own business.
     *
     * <p>A field of this kind whose screen was never registered renders as a disabled button. It
     * cannot be edited, which is the honest outcome, rather than opening nothing.
     */
    CUSTOM_SCREEN
}
