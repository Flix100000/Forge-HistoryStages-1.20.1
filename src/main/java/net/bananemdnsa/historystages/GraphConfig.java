package net.bananemdnsa.historystages;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * {@code graph.toml} — everything the Stage Graph looks like and reveals. Server-owned and
 * synced, like the common config.
 *
 * <p>Its own spec rather than another block in {@link Config.Common} because it is 94 keys;
 * folded into the common file they would drown the settings around them, and the
 * generic sync in {@code SyncGraphConfigPacket} only works on a spec that contains nothing else.
 */
public class GraphConfig {

    public enum NodeShape { RECT, ROUNDED, CIRCLE, DIAMOND, HEXAGON }

    public enum LabelMode { NONE, ID, DISPLAY_NAME }

    public enum EdgeRouting { STRAIGHT, ORTHOGONAL, CURVED }

    public enum EdgeStyle { SOLID, DASHED }

    public enum CanvasBackground { GRID, SOLID, TEXTURE }

    /** What the player view reveals. Carried over unchanged from the old {@code [graph]} block. */
    public enum GraphVisibility { ALL, PROGRESSIVE, PROGRESSIVE_STRICT, UNLOCKED_ONLY }

    /** One node appearance block; six of these exist (3 states x 2 stage collections). */
    public static class StyleBlock {
        public final ModConfigSpec.EnumValue<NodeShape> shape;
        public final ModConfigSpec.DoubleValue size;
        public final ModConfigSpec.IntValue cornerRadius;
        public final ModConfigSpec.ConfigValue<String> border;
        public final ModConfigSpec.IntValue borderWidth;
        public final ModConfigSpec.ConfigValue<String> fill;
        public final ModConfigSpec.DoubleValue fillOpacity;
        public final ModConfigSpec.EnumValue<LabelMode> label;
        public final ModConfigSpec.ConfigValue<String> labelColor;
        public final ModConfigSpec.BooleanValue checkmark;

        StyleBlock(ModConfigSpec.Builder builder, NodeShape defShape, String defBorder,
                   String defFill, boolean defCheckmark) {
            shape = builder.comment("Node outline: RECT, ROUNDED, CIRCLE, DIAMOND or HEXAGON.")
                    .defineEnum("shape", defShape);
            size = builder.comment("Scale relative to the base node size.")
                    .defineInRange("size", 1.0, 0.25, 4.0);
            cornerRadius = builder.comment("Corner radius in pixels.",
                            "Currently has NO effect: the ROUNDED shape is drawn from a texture",
                            "generated once at a fixed relative radius, so the corners cannot",
                            "change per node. Kept so existing files keep loading.")
                    .defineInRange("cornerRadius", 4, 0, 16);
            border = builder.comment("Border colour, #RRGGBB.")
                    .define("border", defBorder);
            borderWidth = builder.comment("Border thickness in pixels.")
                    .defineInRange("borderWidth", 2, 0, 6);
            fill = builder.comment("Fill colour, #RRGGBB.")
                    .define("fill", defFill);
            fillOpacity = builder.comment("Fill opacity, 0.0 = transparent, 1.0 = solid.")
                    .defineInRange("fillOpacity", 0.35, 0.0, 1.0);
            label = builder.comment("Text under the node: NONE, ID or DISPLAY_NAME.")
                    .defineEnum("label", LabelMode.DISPLAY_NAME);
            labelColor = builder.comment("Label colour, #RRGGBB.")
                    .define("labelColor", "#DDDDDD");
            checkmark = builder.comment("Draw a status tick on the node?")
                    .define("checkmark", defCheckmark);
        }
    }

    public static class Graph {

        // [general]
        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.ConfigValue<String> title;
        public final ModConfigSpec.BooleanValue showSidebar;
        public final ModConfigSpec.BooleanValue sidebarOpen;
        public final ModConfigSpec.BooleanValue showLegend;
        public final ModConfigSpec.BooleanValue legendOpen;

        // [canvas]
        public final ModConfigSpec.EnumValue<CanvasBackground> background;
        public final ModConfigSpec.ConfigValue<String> backgroundTexture;
        public final ModConfigSpec.IntValue gridSize;
        public final ModConfigSpec.DoubleValue startZoom;
        public final ModConfigSpec.DoubleValue minZoom;
        public final ModConfigSpec.DoubleValue maxZoom;
        public final ModConfigSpec.BooleanValue fitOnOpen;
        public final ModConfigSpec.BooleanValue animations;

        // [visibility]
        public final ModConfigSpec.EnumValue<GraphVisibility> visibilityMode;
        public final ModConfigSpec.BooleanValue respectHiddenDisplay;
        public final ModConfigSpec.BooleanValue showIndividualStages;

        // [panel]
        public final ModConfigSpec.BooleanValue showStageDeps;
        public final ModConfigSpec.BooleanValue showItems;
        public final ModConfigSpec.BooleanValue showXp;
        public final ModConfigSpec.BooleanValue showAdvancements;
        public final ModConfigSpec.BooleanValue showKills;
        public final ModConfigSpec.BooleanValue showStats;
        public final ModConfigSpec.BooleanValue showScoreboard;
        public final ModConfigSpec.BooleanValue showTriggers;
        public final ModConfigSpec.BooleanValue showUnlocks;
        public final ModConfigSpec.BooleanValue showDescription;

        // [edges]
        public final ModConfigSpec.ConfigValue<String> edgeColorMet;
        public final ModConfigSpec.ConfigValue<String> edgeColorOpen;
        public final ModConfigSpec.IntValue edgeWidth;
        public final ModConfigSpec.EnumValue<EdgeStyle> edgeStyleMet;
        public final ModConfigSpec.EnumValue<EdgeStyle> edgeStyleOpen;
        public final ModConfigSpec.EnumValue<EdgeRouting> edgeRouting;
        public final ModConfigSpec.BooleanValue edgeArrowheads;
        public final ModConfigSpec.EnumValue<EdgeStyle> orGroupStyle;

        // [style.<collection>.<state>]
        public final StyleBlock globalUnlocked;
        public final StyleBlock globalReachable;
        public final StyleBlock globalLocked;
        public final StyleBlock individualUnlocked;
        public final StyleBlock individualReachable;
        public final StyleBlock individualLocked;

        Graph(ModConfigSpec.Builder builder) {
            builder.comment(
                    "Stage Graph — the progression map players open from the pause screen and",
                    "admins open from the in-game editor.",
                    "The [visibility] section affects ONLY the player view; the editor view always",
                    "shows everything. Everything else applies to both."
            ).push("general");

            enabled = builder
                    .comment("Show the 'Stage Graph' button in the pause screen for players?",
                            "Off by default: the graph reveals how the pack is structured, so a pack",
                            "author should switch it on deliberately rather than find it already there.",
                            "Turning it off only removes the player button — admins can always reach the",
                            "graph through the in-game editor. [Default: false]")
                    .define("enabled", false);
            title = builder
                    .comment("Screen title. Either literal text or a translation key.")
                    .define("title", "graph.historystages.title");
            showSidebar = builder
                    .comment("Show the searchable stage list on the left? [Default: true]")
                    .define("showSidebar", true);
            sidebarOpen = builder
                    .comment("Start with the sidebar expanded rather than collapsed to its rail? [Default: true]")
                    .define("sidebarOpen", true);
            showLegend = builder.comment("Show the legend? [Default: true]").define("showLegend", true);
            legendOpen = builder.comment("Start with the legend expanded? [Default: false]")
                    .define("legendOpen", false);
            builder.pop();

            builder.comment("Canvas behaviour and background.").push("canvas");
            background = builder.comment("GRID, SOLID or TEXTURE.")
                    .defineEnum("background", CanvasBackground.GRID);
            backgroundTexture = builder
                    .comment("Texture tiled behind the graph when background = TEXTURE.",
                            "A full texture path, e.g. minecraft:textures/block/polished_andesite.png.",
                            "Blank, unparseable or missing falls back to the plain colour, so a typo",
                            "leaves a readable canvas rather than a missing-texture chequerboard.")
                    .define("backgroundTexture", "minecraft:textures/block/polished_andesite.png");
            gridSize = builder.comment("Pixels per grid cell at zoom 1.0.")
                    .defineInRange("gridSize", 48, 16, 160);
            startZoom = builder.defineInRange("startZoom", 1.0, 0.1, 4.0);
            minZoom = builder.defineInRange("minZoom", 0.3, 0.05, 1.0);
            maxZoom = builder.defineInRange("maxZoom", 2.5, 1.0, 8.0);
            fitOnOpen = builder
                    .comment("Player view only: fit the viewport to the visible nodes on open.",
                            "Keep this on — filtered-out stages leave gaps in the map, and without",
                            "fitting a fresh world opens on empty space. [Default: true]")
                    .define("fitOnOpen", true);
            animations = builder.comment("Play hover, open and drop animations? [Default: true]")
                    .define("animations", true);
            builder.pop();

            builder.comment("How much of the map the PLAYER view reveals. The editor ignores this.")
                    .push("visibility");
            visibilityMode = builder
                    .comment("ALL                = every stage, including standalone ones.",
                            "PROGRESSIVE        = unlocked stages plus their direct neighbours, and everything currently researchable.",
                            "PROGRESSIVE_STRICT = the same, minus the researchable stages that hang off nothing the player owns",
                            "                     (free-standing roots, and stages that only ask for items or XP).",
                            "UNLOCKED_ONLY      = only stages the player has already unlocked.",
                            "A stage removed by this filter takes its edges with it — no placeholder node.")
                    .defineEnum("mode", GraphVisibility.PROGRESSIVE);
            respectHiddenDisplay = builder
                    .comment("Mask the NAME of stages that carry a 'hidden_display' config?",
                            "This is not the same as the filter above: such a stage is still drawn and",
                            "still connected, it just shows as '???'. [Default: true]")
                    .define("respectHiddenDisplay", true);
            showIndividualStages = builder
                    .comment("Show individual (per-player) stages in the graph? [Default: true]")
                    .define("showIndividualStages", true);
            builder.pop();

            builder.comment("Which sections the detail panel on the right shows.").push("panel");
            showStageDeps = builder.define("showStageDeps", true);
            showItems = builder.define("showItems", true);
            showXp = builder.define("showXp", true);
            showAdvancements = builder.define("showAdvancements", true);
            showKills = builder.define("showKills", true);
            showStats = builder.define("showStats", true);
            showScoreboard = builder.define("showScoreboard", true);
            showTriggers = builder
                    .comment("Show auto-unlock triggers for AUTO/TEMPORARY stages? These reveal the",
                            "condition that unlocks a stage. [Default: true]")
                    .define("showTriggers", true);
            showUnlocks = builder.comment("Show the 'unlocks' list — what this stage leads to?")
                    .define("showUnlocks", true);
            showDescription = builder.comment("Show the per-stage info text from graph_stages.json?")
                    .define("showDescription", true);
            builder.pop();

            builder.comment("Dependency lines.").push("edges");
            edgeColorMet = builder.comment("Colour of a satisfied dependency, #RRGGBB.")
                    .define("colorMet", "#88BB88");
            edgeColorOpen = builder.comment("Colour of an open dependency, #RRGGBB.")
                    .define("colorOpen", "#999999");
            edgeWidth = builder.defineInRange("width", 2, 1, 6);
            edgeStyleMet = builder
                    .comment("Line style for a satisfied AND dependency.")
                    .defineEnum("styleMet", EdgeStyle.SOLID);
            edgeStyleOpen = builder
                    .comment("Line style for an unsatisfied AND dependency.",
                            "Solid by default so the two visual axes stay separate: the LINE STYLE",
                            "says AND vs OR, the COLOUR says satisfied vs open. Setting this to",
                            "DASHED makes an open AND dependency look like an OR one — the graph",
                            "compensates by drawing OR groups with a finer dotted pattern, but the",
                            "distinction is much weaker.")
                    .defineEnum("styleOpen", EdgeStyle.SOLID);
            edgeRouting = builder.comment("STRAIGHT, ORTHOGONAL or CURVED.")
                    .defineEnum("routing", EdgeRouting.CURVED);
            edgeArrowheads = builder.define("arrowheads", true);
            orGroupStyle = builder
                    .comment("Line style for dependencies inside an OR group.",
                            "Dashed by default, against solid AND dependencies — that is the whole",
                            "signal for 'one of these is enough'.")
                    .defineEnum("orGroupStyle", EdgeStyle.DASHED);
            builder.pop();

            builder.comment("Node appearance, per lock state and per stage collection.",
                            "A stage may override any of these individually in",
                            "settings/graph_stages.json.",
                            "",
                            "The status tick is on for UNLOCKED only. A tick means 'you have this';",
                            "putting one on a reachable stage claims something is done when it is",
                            "merely available next. Reachable is already distinguished by its colour.")
                    .push("style");

            builder.push("global");
            globalUnlocked = styleBlock(builder, "unlocked", NodeShape.ROUNDED, "#44CC99", "#2E8B62", true);
            globalReachable = styleBlock(builder, "reachable", NodeShape.ROUNDED, "#DDBB44", "#8A7220", false);
            globalLocked = styleBlock(builder, "locked", NodeShape.ROUNDED, "#555555", "#787878", false);
            builder.pop();

            builder.push("individual");
            individualUnlocked = styleBlock(builder, "unlocked", NodeShape.DIAMOND, "#44CC99", "#2E8B62", true);
            individualReachable = styleBlock(builder, "reachable", NodeShape.DIAMOND, "#DDBB44", "#8A7220", false);
            individualLocked = styleBlock(builder, "locked", NodeShape.DIAMOND, "#555555", "#787878", false);
            builder.pop();

            builder.pop(); // style
        }

        private static StyleBlock styleBlock(ModConfigSpec.Builder builder, String state,
                                             NodeShape shape, String border, String fill,
                                             boolean checkmark) {
            builder.push(state);
            StyleBlock block = new StyleBlock(builder, shape, border, fill, checkmark);
            builder.pop();
            return block;
        }
    }

    public static final ModConfigSpec GRAPH_SPEC;
    public static final Graph GRAPH;

    static {
        final Pair<Graph, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Graph::new);
        GRAPH = pair.getLeft();
        GRAPH_SPEC = pair.getRight();
    }
}
