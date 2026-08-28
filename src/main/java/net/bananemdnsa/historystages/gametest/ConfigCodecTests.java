package net.bananemdnsa.historystages.gametest;

import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.config.ConfigSpecCodec;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Round-trip and rejection behaviour of {@link ConfigSpecCodec}, exercised against the graph
 * spec since it is the one already wired to a real config file.
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
}
