package net.bananemdnsa.historystages.data.tooltip;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The research scroll's tooltip layout: which lines exist, in which order, with which text and
 * colour. Parsed from the common config's {@code scrollTooltipLines} list, the same way
 * {@code ResearchBoosterRegistry} parses {@code researchBoosters}.
 *
 * <p>Deliberately free of Minecraft types so the whole codec is unit-testable. Turning a style
 * token into a {@code ChatFormatting} happens in the renderer.
 */
public final class ScrollTooltipLayout {

    private ScrollTooltipLayout() {}

    // --- known ids ---

    /** Always first, never movable, never disabled: the item name. */
    public static final String NAME_ID = "name";

    /** Reorderable sections, in their factory order. */
    public static final List<String> MOVABLE_IDS = List.of(
            "individual_badge", "owner", "info1", "info2", "tier", "dependencies");

    /** Templates inside the dependency block. Fixed order, only text and colour are editable. */
    public static final List<String> DEP_TEMPLATE_IDS = List.of(
            "dep.header", "dep.item", "dep.stage", "dep.individual", "dep.xp", "dep.separator");

    /**
     * Block-level values of the dependency block. They ride in the same list so the whole layout
     * stays one config key; their value lives in {@code text} and the other fields are unused.
     */
    public static final List<String> DEP_OPTION_IDS = List.of(
            "dep.icon_fulfilled", "dep.icon_open", "dep.icon_unknown",
            "dep.color_fulfilled", "dep.color_open");

    private static final List<ScrollTooltipLine> DEFAULTS = List.of(
            new ScrollTooltipLine(NAME_ID, true, false, "aqua", ""),
            new ScrollTooltipLine("individual_badge", true, false, "light_purple", ""),
            new ScrollTooltipLine("owner", true, false, "gray", ""),
            new ScrollTooltipLine("info1", true, false, "gray+italic", ""),
            new ScrollTooltipLine("info2", true, false, "gray+italic", ""),
            new ScrollTooltipLine("tier", true, false, "gray", ""),
            new ScrollTooltipLine("dependencies", true, true, "", ""),
            new ScrollTooltipLine("dep.header", true, false, "gold", ""),
            new ScrollTooltipLine("dep.item", true, false, "", ""),
            new ScrollTooltipLine("dep.stage", true, false, "", ""),
            new ScrollTooltipLine("dep.individual", true, false, "", ""),
            new ScrollTooltipLine("dep.xp", true, false, "", ""),
            new ScrollTooltipLine("dep.separator", true, false, "dark_gray", ""),
            new ScrollTooltipLine("dep.icon_fulfilled", true, false, "", "\u2714"),
            new ScrollTooltipLine("dep.icon_open", true, false, "", "\u2718"),
            new ScrollTooltipLine("dep.icon_unknown", true, false, "", "\u2022"),
            new ScrollTooltipLine("dep.color_fulfilled", true, false, "", "green"),
            new ScrollTooltipLine("dep.color_open", true, false, "", "gray"));

    public static List<ScrollTooltipLine> defaults() {
        return DEFAULTS;
    }

    /** The encoded form of {@link #defaults()}, for the config's default value. */
    public static List<String> defaultsEncoded() {
        return DEFAULTS.stream().map(ScrollTooltipLayout::encodeLine).toList();
    }

    // --- merge ---

    /**
     * Turns the raw config list into a complete layout: known ids keep the config's order,
     * missing ones are filled from the defaults, unknown ones are dropped. That is what lets a
     * later version add a section without anyone having to touch their config file.
     */
    public static List<ScrollTooltipLine> parse(List<? extends String> entries) {
        Map<String, ScrollTooltipLine> byId = new LinkedHashMap<>();
        List<String> movableOrder = new ArrayList<>();

        if (entries != null) {
            for (String raw : entries) {
                ScrollTooltipLine line = decodeLine(raw);
                if (line == null) continue;
                if (!isKnown(line.id()) || byId.containsKey(line.id())) continue;
                byId.put(line.id(), line);
                if (MOVABLE_IDS.contains(line.id())) movableOrder.add(line.id());
            }
        }

        for (String id : MOVABLE_IDS) {
            if (!movableOrder.contains(id)) movableOrder.add(id);
        }

        List<ScrollTooltipLine> result = new ArrayList<>(DEFAULTS.size());
        result.add(byId.getOrDefault(NAME_ID, defaultLine(NAME_ID)));
        for (String id : movableOrder) result.add(byId.getOrDefault(id, defaultLine(id)));
        for (String id : DEP_TEMPLATE_IDS) result.add(byId.getOrDefault(id, defaultLine(id)));
        for (String id : DEP_OPTION_IDS) result.add(byId.getOrDefault(id, defaultLine(id)));
        return List.copyOf(result);
    }

    private static boolean isKnown(String id) {
        return NAME_ID.equals(id) || MOVABLE_IDS.contains(id)
                || DEP_TEMPLATE_IDS.contains(id) || DEP_OPTION_IDS.contains(id);
    }

    private static ScrollTooltipLine defaultLine(String id) {
        return DEFAULTS.stream().filter(l -> l.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("No default for id " + id));
    }

    /** Splits a style field into lowercase formatting names. Empty for a blank field. */
    public static List<String> styleTokens(String style) {
        if (style == null || style.isBlank()) return List.of();
        List<String> tokens = new ArrayList<>();
        for (String part : style.split("\\+")) {
            String token = part.trim().toLowerCase(java.util.Locale.ROOT);
            if (!token.isEmpty()) tokens.add(token);
        }
        return List.copyOf(tokens);
    }

    // --- line codec ---

    /**
     * Escapes the two separators, the escape character itself, and the newline. Order matters:
     * the backslash has to go first or it would escape the escapes.
     *
     * <p>The newline is in here because a line's text may hold real line breaks — one config
     * line can render as several tooltip lines — and a raw newline inside a toml list entry
     * would not survive the round trip.
     */
    static String escape(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        return raw.replace("\\", "\\\\").replace("|", "\\p").replace(";", "\\s")
                .replace("\n", "\\n");
    }

    /** Inverse of {@link #escape}. An unknown escape is kept verbatim rather than swallowed. */
    static String unescape(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) {
                out.append(c);
                continue;
            }
            char next = raw.charAt(++i);
            switch (next) {
                case '\\' -> out.append('\\');
                case 'p' -> out.append('|');
                case 's' -> out.append(';');
                case 'n' -> out.append('\n');
                default -> out.append('\\').append(next);
            }
        }
        return out.toString();
    }

    public static String encodeLine(ScrollTooltipLine line) {
        return String.join("|",
                escape(line.id()),
                Boolean.toString(line.enabled()),
                Boolean.toString(line.spacerBefore()),
                escape(line.style()),
                escape(line.text()));
    }

    /** Returns null for anything that is not five fields; the caller falls back to the default. */
    public static ScrollTooltipLine decodeLine(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        // -1 keeps a trailing empty text field, which the default lines all have.
        String[] parts = trimmed.split("\\|", -1);
        if (parts.length != 5) return null;
        return new ScrollTooltipLine(
                unescape(parts[0]),
                Boolean.parseBoolean(parts[1]),
                Boolean.parseBoolean(parts[2]),
                unescape(parts[3]),
                unescape(parts[4]));
    }

    // --- live layout ---

    private static List<ScrollTooltipLine> ACTIVE = DEFAULTS;

    /** Replaces the live layout from the visual config's {@code scroll_tooltip.lines} list. */
    public static void rebuildFromConfig(List<? extends String> entries) {
        ACTIVE = parse(entries);
    }

    /** The whole live layout, name and templates included. */
    public static List<ScrollTooltipLine> active() {
        return ACTIVE;
    }

    /** Only the reorderable sections, in the order the tooltip should render them. */
    public static List<ScrollTooltipLine> movable() {
        return ACTIVE.stream().filter(l -> MOVABLE_IDS.contains(l.id())).toList();
    }

    /** The live line for an id, or null if the id is not one this version knows. */
    public static ScrollTooltipLine line(String id) {
        return ACTIVE.stream().filter(l -> l.id().equals(id)).findFirst().orElse(null);
    }

    /** Shorthand for the reserved option ids, whose value lives in {@code text}. */
    public static String option(String id, String fallback) {
        ScrollTooltipLine found = line(id);
        return found == null || found.text().isEmpty() ? fallback : found.text();
    }

    // --- placeholders ---

    /**
     * Replaces {@code %name%}-style placeholders. An unknown placeholder is left standing so a
     * typo in the config is visible in game rather than silently swallowed.
     */
    public static String fill(String template, Map<String, String> vars) {
        if (template == null || template.isEmpty()) return "";
        String out = template;
        for (Map.Entry<String, String> var : vars.entrySet()) {
            out = out.replace("%" + var.getKey() + "%", var.getValue());
        }
        return out;
    }
}
