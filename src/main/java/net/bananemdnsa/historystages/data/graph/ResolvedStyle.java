package net.bananemdnsa.historystages.data.graph;

/** A fully resolved node style — no nulls, ready to draw. */
public record ResolvedStyle(
        String shape, double size, int cornerRadius,
        int border, int borderWidth, int fill, double fillOpacity,
        String label, int labelColor, boolean checkmark) {

    /**
     * Layers an override on top of a base. Any null field in {@code override} keeps the base
     * value, which is what lets a pack author change one colour without restating a block.
     */
    public static ResolvedStyle merge(ResolvedStyle base, StageStyle override) {
        if (override == null || override.isEmpty()) return base;
        return new ResolvedStyle(
                override.shape != null ? override.shape.toUpperCase(java.util.Locale.ROOT) : base.shape(),
                override.size != null ? override.size : base.size(),
                override.cornerRadius != null ? override.cornerRadius : base.cornerRadius(),
                override.border != null ? parseColor(override.border, base.border()) : base.border(),
                override.borderWidth != null ? override.borderWidth : base.borderWidth(),
                override.fill != null ? parseColor(override.fill, base.fill()) : base.fill(),
                override.fillOpacity != null ? override.fillOpacity : base.fillOpacity(),
                override.label != null ? override.label.toUpperCase(java.util.Locale.ROOT) : base.label(),
                override.labelColor != null ? parseColor(override.labelColor, base.labelColor()) : base.labelColor(),
                override.checkmark != null ? override.checkmark : base.checkmark());
    }

    /** Parses {@code #RRGGBB} (with or without the hash) into 0xRRGGBB; falls back on garbage. */
    public static int parseColor(String text, int fallback) {
        if (text == null) return fallback;
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
