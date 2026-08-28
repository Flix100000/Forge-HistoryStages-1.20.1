package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.config.ConfigSpecCodec;
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

    // --- list values, against COMMON_SPEC ---
    //
    // The graph spec cannot cover these: graph.toml has no list anywhere in it, which is exactly
    // why the codec shipped without list support and every list setting in the common config
    // would have stopped syncing. COMMON_SPEC is the spec that actually has them.
    //
    // "research.researchBoosters" is the subject throughout because it is defineListAllowEmpty,
    // so clearing it is a legal value the spec accepts rather than something validate=true turns
    // away for reasons that have nothing to do with the codec.

    private static final String BOOSTERS = "research.researchBoosters";

    @GameTest(template = "empty")
    public static void listsRoundTripThroughTheWireString(GameTestHelper helper) {
        List<? extends String> original = Config.COMMON.researchBoosters.get();
        try {
            Map<String, String> wire = new HashMap<>();
            wire.put(BOOSTERS, "minecraft:gold_block, 20, 0, 1, min;minecraft:diamond_block, 40, 10, 2, exact");

            if (ConfigSpecCodec.apply(Config.COMMON_SPEC, wire, true, ConfigSpecCodec.NO_EXTRA_CHECK) != 1) {
                helper.fail("the booster list was not applied at all");
                return;
            }

            List<? extends String> applied = Config.COMMON.researchBoosters.get();
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

            String collected = ConfigSpecCodec.collect(Config.COMMON_SPEC).get(BOOSTERS);
            if (!collected.equals(wire.get(BOOSTERS))) {
                helper.fail("collect gave '" + collected + "', expected '" + wire.get(BOOSTERS) + "'");
                return;
            }
            helper.succeed();
        } finally {
            Config.COMMON.researchBoosters.set(original);
        }
    }

    @GameTest(template = "empty")
    public static void emptyStringIsAnEmptyList(GameTestHelper helper) {
        List<? extends String> original = Config.COMMON.researchBoosters.get();
        try {
            // Seeded first, and not left to the default. researchBoosters defaults to empty, so a
            // codec that applied nothing at all would leave the list empty and pass this test
            // without ever having cleared anything.
            Config.COMMON.researchBoosters.set(List.of("minecraft:gold_block, 20, 0, 1, min"));

            Map<String, String> wire = new HashMap<>();
            wire.put(BOOSTERS, "");
            ConfigSpecCodec.apply(Config.COMMON_SPEC, wire, true, ConfigSpecCodec.NO_EXTRA_CHECK);

            List<? extends String> applied = Config.COMMON.researchBoosters.get();
            // The failure being guarded against is a list holding one empty string, which reads as
            // "one booster" everywhere downstream and parses into a warning rather than nothing.
            if (!applied.isEmpty()) {
                helper.fail("clearing the list gave " + applied.size() + " entries: " + applied);
                return;
            }
            if (!ConfigSpecCodec.collect(Config.COMMON_SPEC).get(BOOSTERS).isEmpty()) {
                helper.fail("an empty list did not collect back to an empty string");
                return;
            }
            helper.succeed();
        } finally {
            Config.COMMON.researchBoosters.set(original);
        }
    }

    @GameTest(template = "empty")
    public static void everyCommonListCollectsWithoutBrackets(GameTestHelper helper) {
        // String.valueOf on a List yields "[a, b]". That parses back as a single entry named
        // "[a" and would have gone out to every client on login.
        Map<String, String> all = ConfigSpecCodec.collect(Config.COMMON_SPEC);
        for (Map.Entry<String, String> entry : all.entrySet()) {
            String value = entry.getValue();
            if (value.startsWith("[") && value.endsWith("]")) {
                helper.fail("'" + entry.getKey() + "' collected as a Java list literal: " + value);
                return;
            }
        }
        helper.succeed();
    }
}
