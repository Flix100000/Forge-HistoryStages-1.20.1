package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.config.ConfigSpecCodec;
import net.bananemdnsa.historystages.data.config.LocalConfigSnapshot;
import net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLayout;
import net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLine;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Round-trip and rejection behaviour of {@link ConfigSpecCodec}.
 *
 * <p>Split across two specs on purpose. The graph spec covers the scalar paths and the rejection
 * rules; the common spec covers lists, because graph.toml has none and the list handling could
 * therefore break without a single test noticing.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConfigCodecTests {

    private ConfigCodecTests() {}

    @GameTest(template = "empty")
    public static void collectThenApplyIsIdentity(GameTestHelper helper) {
        Map<String, String> before = ConfigSpecCodec.collect(GraphConfig.GRAPH_SPEC);
        ConfigSpecCodec.apply(GraphConfig.GRAPH_SPEC, before, true, ConfigSpecCodec.NO_EXTRA_CHECK);
        Map<String, String> after = ConfigSpecCodec.collect(GraphConfig.GRAPH_SPEC);

        if (!before.equals(after)) {
            helper.fail("collect -> apply -> collect changed the spec");
            return;
        }
        if (before.isEmpty()) {
            helper.fail("collect returned nothing — the walk found no values at all");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownPathsAndJunkAreSkipped(GameTestHelper helper) {
        // A boolean key cannot stand in for "malformed value" here: Boolean.parseBoolean never
        // throws, so any junk text for a boolean path just becomes false and sails through both
        // the parse step and the spec's own (unconstrained) test. A ranged value is what actually
        // exercises validate=true rejecting something the spec disagrees with — "canvas.gridSize"
        // is defineInRange(48, 16, 160), so a syntactically valid but out-of-range int is parsed
        // fine and then turned away by spec.test(...).
        Map<String, String> before = ConfigSpecCodec.collect(GraphConfig.GRAPH_SPEC);

        Map<String, String> junk = new HashMap<>();
        junk.put("this.path.does.not.exist", "true");
        junk.put("canvas.gridSize", "99999");
        int applied = ConfigSpecCodec.apply(
                GraphConfig.GRAPH_SPEC, junk, true, ConfigSpecCodec.NO_EXTRA_CHECK);

        if (applied != 0) {
            helper.fail("applied " + applied + " junk values, expected 0");
            return;
        }
        if (!before.equals(ConfigSpecCodec.collect(GraphConfig.GRAPH_SPEC))) {
            helper.fail("junk input changed the spec");
            return;
        }
        helper.succeed();
    }

    // --- list values, against GAMEPLAY_SPEC ---
    //
    // The graph spec cannot cover these: graph.toml has no list anywhere in it, which is exactly
    // why the codec shipped without list support and every list setting in the gameplay config
    // would have stopped syncing. GAMEPLAY_SPEC is the spec that actually has them.
    //
    // "research.researchBoosters" is the subject throughout because it is defineListAllowEmpty,
    // so clearing it is a legal value the spec accepts rather than something validate=true turns
    // away for reasons that have nothing to do with the codec.

    private static final String BOOSTERS = "research.researchBoosters";

    @GameTest(template = "empty")
    public static void listsRoundTripThroughTheWireString(GameTestHelper helper) {
        List<? extends String> original = Config.GAMEPLAY.researchBoosters.get();
        try {
            Map<String, String> wire = new HashMap<>();
            wire.put(BOOSTERS, "minecraft:gold_block, 20, 0, 1, min;minecraft:diamond_block, 40, 10, 2, exact");

            if (ConfigSpecCodec.apply(Config.GAMEPLAY_SPEC, wire, true, ConfigSpecCodec.NO_EXTRA_CHECK) != 1) {
                helper.fail("the booster list was not applied at all");
                return;
            }

            List<? extends String> applied = Config.GAMEPLAY.researchBoosters.get();
            if (applied.size() != 2) {
                helper.fail("expected 2 boosters, got " + applied.size() + ": " + applied);
                return;
            }
            // The commas inside an entry must survive: they separate a booster's own fields, and
            // splitting on them instead of on ';' would shred every entry into five.
            if (!applied.get(0).equals("minecraft:gold_block, 20, 0, 1, min")) {
                helper.fail("first booster came back as '" + applied.get(0) + "'");
                return;
            }

            String collected = ConfigSpecCodec.collect(Config.GAMEPLAY_SPEC).get(BOOSTERS);
            if (!collected.equals(wire.get(BOOSTERS))) {
                helper.fail("collect gave '" + collected + "', expected '" + wire.get(BOOSTERS) + "'");
                return;
            }
            helper.succeed();
        } finally {
            Config.GAMEPLAY.researchBoosters.set(original);
        }
    }

    @GameTest(template = "empty")
    public static void emptyStringIsAnEmptyList(GameTestHelper helper) {
        List<? extends String> original = Config.GAMEPLAY.researchBoosters.get();
        try {
            // Seeded first, and not left to the default. researchBoosters defaults to empty, so a
            // codec that applied nothing at all would leave the list empty and pass this test
            // without ever having cleared anything.
            Config.GAMEPLAY.researchBoosters.set(List.of("minecraft:gold_block, 20, 0, 1, min"));

            Map<String, String> wire = new HashMap<>();
            wire.put(BOOSTERS, "");
            ConfigSpecCodec.apply(Config.GAMEPLAY_SPEC, wire, true, ConfigSpecCodec.NO_EXTRA_CHECK);

            List<? extends String> applied = Config.GAMEPLAY.researchBoosters.get();
            // The failure being guarded against is a list holding one empty string, which reads as
            // "one booster" everywhere downstream and parses into a warning rather than nothing.
            if (!applied.isEmpty()) {
                helper.fail("clearing the list gave " + applied.size() + " entries: " + applied);
                return;
            }
            if (!ConfigSpecCodec.collect(Config.GAMEPLAY_SPEC).get(BOOSTERS).isEmpty()) {
                helper.fail("an empty list did not collect back to an empty string");
                return;
            }
            helper.succeed();
        } finally {
            Config.GAMEPLAY.researchBoosters.set(original);
        }
    }

    @GameTest(template = "empty")
    public static void everyCommonListCollectsWithoutBrackets(GameTestHelper helper) {
        // String.valueOf on a List yields "[a, b]". That parses back as a single entry named
        // "[a" and would have gone out to every client on login.
        Map<String, String> all = ConfigSpecCodec.collect(Config.GAMEPLAY_SPEC);
        for (Map.Entry<String, String> entry : all.entrySet()) {
            String value = entry.getValue();
            if (value.startsWith("[") && value.endsWith("]")) {
                helper.fail("'" + entry.getKey() + "' collected as a Java list literal: " + value);
                return;
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void leavingAServerBringsBackTheOwnValues(GameTestHelper helper) {
        boolean originalIcons = Config.VISUAL.showLockIcons.get();
        int originalInterval = Config.GAMEPLAY.structureCheckInterval.get();

        // Anything left over from an earlier test would make this one lie.
        LocalConfigSnapshot.restore();

        try {
            // What the player has in their own files. Deliberately not the defaults: a snapshot
            // that silently did nothing would still look right if these matched.
            Config.VISUAL.showLockIcons.set(false);
            Config.GAMEPLAY.structureCheckInterval.set(77);

            LocalConfigSnapshot.rememberBeforeSync(Config.VISUAL_SPEC);
            LocalConfigSnapshot.rememberBeforeSync(Config.GAMEPLAY_SPEC);

            // What the server pushes over them on login.
            Config.VISUAL.showLockIcons.set(true);
            Config.GAMEPLAY.structureCheckInterval.set(5);

            // An admin saves mid-session, so a second round of server values arrives. This must
            // NOT become the new baseline, or the player would keep the server's settings.
            LocalConfigSnapshot.rememberBeforeSync(Config.VISUAL_SPEC);
            LocalConfigSnapshot.rememberBeforeSync(Config.GAMEPLAY_SPEC);

            int restored = LocalConfigSnapshot.restore();
            if (restored < 2) {
                helper.fail("restore() only wrote " + restored + " values, expected at least 2");
                return;
            }
            if (Config.VISUAL.showLockIcons.get()) {
                helper.fail("showLockIcons stayed on the server's value instead of coming back false");
                return;
            }
            if (Config.GAMEPLAY.structureCheckInterval.get() != 77) {
                helper.fail("structureCheckInterval came back as "
                        + Config.GAMEPLAY.structureCheckInterval.get() + " instead of 77");
                return;
            }
            if (LocalConfigSnapshot.holdsServerValues()) {
                helper.fail("the snapshot survived its own restore — the next server visit would "
                        + "restore stale values");
                return;
            }
            helper.succeed();
        } finally {
            LocalConfigSnapshot.restore();
            Config.VISUAL.showLockIcons.set(originalIcons);
            Config.GAMEPLAY.structureCheckInterval.set(originalInterval);
        }
    }

    @GameTest(template = "empty")
    public static void restoringAlsoRebuildsWhatTheValuesFeed(GameTestHelper helper) {
        // Three settings are lists that get parsed into an in-memory structure. Writing the values
        // back is not enough on its own — without a rebuild the player carries the server's parsed
        // copy into their next singleplayer world while their own file says something else.
        //
        // The observable is the name line's enabled flag, not the line count: parse() fills every
        // missing id in from the defaults, so the layout is always the same length no matter what
        // goes in. A first version of this test compared lengths and could not tell the two apart.
        List<? extends String> originalLines = Config.VISUAL.scrollTooltipLines.get();
        LocalConfigSnapshot.restore();

        try {
            ScrollTooltipLine defaultName = ScrollTooltipLayout.defaults().stream()
                    .filter(l -> l.id().equals(ScrollTooltipLayout.NAME_ID))
                    .findFirst().orElseThrow();
            boolean localEnabled = !defaultName.enabled();

            List<String> localLayout = List.of(ScrollTooltipLayout.encodeLine(
                    new ScrollTooltipLine(ScrollTooltipLayout.NAME_ID, localEnabled,
                            defaultName.spacerBefore(), defaultName.style(), defaultName.text())));
            Config.VISUAL.scrollTooltipLines.set(localLayout);
            ScrollTooltipLayout.rebuildFromConfig(localLayout);

            if (nameLineEnabled() != localEnabled) {
                helper.fail("the local layout did not survive its own parse, so this test could "
                        + "not prove anything");
                return;
            }

            LocalConfigSnapshot.rememberBeforeSync(Config.VISUAL_SPEC);

            List<String> serverLayout = ScrollTooltipLayout.defaultsEncoded();
            Config.VISUAL.scrollTooltipLines.set(serverLayout);
            ScrollTooltipLayout.rebuildFromConfig(serverLayout);

            if (nameLineEnabled() == localEnabled) {
                helper.fail("the server layout parses the same as the player's, so this test "
                        + "could not tell them apart");
                return;
            }

            LocalConfigSnapshot.restore();

            if (nameLineEnabled() != localEnabled) {
                helper.fail("the live layout still shows the server's name line — the values came "
                        + "back but nothing reparsed them");
                return;
            }
            helper.succeed();
        } finally {
            LocalConfigSnapshot.restore();
            Config.VISUAL.scrollTooltipLines.set(originalLines);
            ScrollTooltipLayout.rebuildFromConfig(originalLines);
        }
    }

    private static boolean nameLineEnabled() {
        return ScrollTooltipLayout.active().stream()
                .filter(l -> l.id().equals(ScrollTooltipLayout.NAME_ID))
                .findFirst().orElseThrow().enabled();
    }
}
