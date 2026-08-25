package net.bananemdnsa.historystages.data.lock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.api.stage.StageStateView;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.category.CategoryLockResolver;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;
import net.bananemdnsa.historystages.data.lock.engine.LockResolution;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * An instrument, not a test — it measures and prints, and it does not fail on a number.
 *
 * <p>Phase 10 of the API rework is a performance phase, and the project had no measurement of
 * any kind. Optimising without one means guessing, and an invisible optimisation that nobody
 * measured cannot be shown to have worked. This produces the before-and-after figure that
 * decides how far the BitSet engine is worth taking.
 *
 * <p>Deliberately no timing assertion. A wall-clock threshold in a test suite is flaky on a busy
 * machine and would eventually be muted, which is worse than having no number at all. The one
 * assertion here guards the setup, so the class cannot silently measure nothing.
 *
 * <p>Covers only the Minecraft-free categories — dimensions stand in for the whole id-matching
 * family, which is what a bitmask bake could precompute. The item path needs real stacks, real
 * tags and NBT matching; measuring that belongs in a GameTest, and NBT is precisely the part no
 * bake can cover.
 *
 * <p>Off unless asked for: {@code ./gradlew test -Pbench}. Half a million iterations per
 * measurement turn a three-second suite into thirty-five, and the suite runs after every step of
 * a refactor — a cost nobody would keep paying, so it would eventually be deleted instead.
 */
@EnabledIfSystemProperty(named = "historystages.bench", matches = "true")
class LockCheckBenchmarkTest {

    private static final int WARMUP = 100_000;
    private static final int RUNS = 500_000;

    /**
     * Stage counts spanning what packs actually ship. Felix, 2026-08-24: a real pack runs around
     * three hundred stages, each locking roughly five items. Five hundred is the headroom check.
     */
    private static final int[] SCALES = {10, 50, 300, 500};

    private static final String GATED = "historystages:bench_dim_0";
    private static final String UNGATED = "minecraft:the_end";

    /**
     * A world of {@code stages} stages, each gating four dimensions of its own. Nothing gates
     * {@link #UNGATED}, which is the case that matters: almost everything a player touches is in
     * no stage at all, and that is the path a per-frame caller hits over and over.
     */
    private static Map<String, StageEntry> world(int stages) {
        Map<String, StageEntry> map = new LinkedHashMap<>();
        for (int i = 0; i < stages; i++) {
            StageEntry stage = new StageEntry();
            List<String> dims = new ArrayList<>();
            for (int d = 0; d < 4; d++) dims.add("historystages:bench_dim_" + (i * 4 + d));
            stage.setDimensions(dims);
            map.put("bench_stage_" + i, stage);
        }
        return map;
    }

    private static long nanosPerOp(Runnable op) {
        for (int i = 0; i < WARMUP; i++) op.run();
        long start = System.nanoTime();
        for (int i = 0; i < RUNS; i++) op.run();
        return (System.nanoTime() - start) / RUNS;
    }

    @Test
    void measureTheLockCheck() {
        LockCategory<?> dimensions = LockCategories.byId("historystages:dimensions");
        assertNotNull(dimensions, "no dimensions category — the benchmark would measure nothing");

        StringBuilder report = new StringBuilder("\n=== lock check, ns per call ===\n");
        report.append(String.format("%-8s %14s %14s %14s%n",
                "stages", "gating(miss)", "gating(hit)", "isLocked(miss)"));

        for (int scale : SCALES) {
            Map<String, StageEntry> stages = world(scale);
            StageStateView nothingUnlocked = StageStateView.NONE_UNLOCKED;

            long miss = nanosPerOp(() ->
                    CategoryLockResolver.gatingStages(dimensions, UNGATED, stages));
            long hit = nanosPerOp(() ->
                    CategoryLockResolver.gatingStages(dimensions, GATED, stages));

            List<String> gatingForMiss = CategoryLockResolver.gatingStages(dimensions, UNGATED, stages);
            long locked = nanosPerOp(() -> LockResolution.isLocked(gatingForMiss, nothingUnlocked));

            report.append(String.format("%-8d %14d %14d %14d%n", scale, miss, hit, locked));
        }

        report.append("\nThe miss column is the one that matters: nearly everything a player\n")
              .append("touches is gated by nothing, and that is the call a per-frame caller\n")
              .append("makes over and over.\n");
        System.out.println(report);
    }

    /**
     * Full scan against index lookup — the before and after of the Phase 10 index.
     *
     * <p>The engine reaches its index through {@code CategoryLockIndexes}, which asks
     * {@code StageManager} and therefore cannot be touched from a unit test. What is rebuilt here
     * is the same shape: one map lookup for the candidates, then the exact check over those and
     * only those. The scan it replaces is the column beside it.
     */
    @Test
    void measureTheIndexAgainstTheScan() {
        LockCategory<?> dimensions = LockCategories.byId("historystages:dimensions");
        assertNotNull(dimensions, "no dimensions category — the benchmark would measure nothing");

        StringBuilder report = new StringBuilder("\n=== full scan vs index lookup, ns per call ===\n");
        report.append(String.format("%-8s %14s %14s %10s%n", "stages", "scan", "index", "factor"));

        for (int scale : SCALES) {
            Map<String, StageEntry> stages = world(scale);

            // The same table CategoryLockIndexes builds: key -> stages that could match it.
            Map<String, List<String>> index = new java.util.HashMap<>();
            for (Map.Entry<String, StageEntry> stage : stages.entrySet()) {
                for (String key : dimensions.indexKeys(stage.getValue())) {
                    index.computeIfAbsent(key, k -> new ArrayList<>(1)).add(stage.getKey());
                }
            }

            long scan = nanosPerOp(() ->
                    CategoryLockResolver.gatingStages(dimensions, UNGATED, stages));
            long lookup = nanosPerOp(() -> {
                List<String> candidates = index.get(UNGATED);
                if (candidates == null) return;
                CategoryLockResolver.gatingStages(List.of(dimensions), UNGATED, candidates, stages);
            });

            report.append(String.format("%-8d %14d %14d %9dx%n",
                    scale, scan, lookup, lookup == 0 ? scan : scan / Math.max(lookup, 1)));
        }

        report.append("\nThe index answers 'no stage can match' from one hash lookup, and that is\n")
              .append("the answer almost every call gets. The scan had to visit every stage to\n")
              .append("reach the same conclusion.\n");
        System.out.println(report);
    }

    /**
     * The shape of a per-tick fast-out: "does any stage anywhere carry an entry of this kind?"
     *
     * <p>{@code anyStructureLocks} and {@code anyBiomeLocks} answer exactly this, on every tick,
     * by walking both stage maps until they find one. A pack that uses neither never finds one,
     * so the common case is the full walk — and the answer can only change when the stages do.
     */
    @Test
    void measureThePerTickFastOut() {
        LockCategory<?> structures = LockCategories.byId("historystages:structures");
        assertNotNull(structures, "no structures category — the benchmark would measure nothing");

        StringBuilder report = new StringBuilder("\n=== per-tick fast-out, ns per call ===\n");
        report.append(String.format("%-8s %18s%n", "stages", "no stage matches"));

        for (int scale : SCALES) {
            Map<String, StageEntry> stages = world(scale);
            long ns = nanosPerOp(() -> {
                for (StageEntry stage : stages.values()) {
                    if (!structures.read(stage).isEmpty()) return;
                }
            });
            report.append(String.format("%-8d %18d%n", scale, ns));
        }

        report.append("\nTwenty times a second, and both scopes are walked, so double it. The\n")
              .append("answer changes only when the stages change — which is a signal we have.\n");
        System.out.println(report);
    }

    /**
     * Where the bitmask would actually pay, measured instead of argued.
     *
     * <p>A mask replaces the resolution step: instead of asking the player's set about each
     * gating stage in turn, one AND answers all of them. That only matters when there are many
     * gating stages per subject — and the individual-scope view costs a map lookup per question
     * on top, because it resolves the player's set every time it is asked.
     *
     * <p>Compared against the cheapest thing a mask could be: a single long AND.
     */
    @Test
    void measureWhenAMaskWouldStartToPay() {
        StringBuilder report = new StringBuilder("\n=== resolution vs a mask, ns per call ===\n");
        report.append(String.format("%-14s %14s %16s %10s%n",
                "gating stages", "global view", "per-player view", "one AND"));

        java.util.UUID player = new java.util.UUID(1L, 2L);
        Map<java.util.UUID, Set<String>> perPlayer = new java.util.concurrent.ConcurrentHashMap<>();
        perPlayer.put(player, java.util.concurrent.ConcurrentHashMap.newKeySet());

        for (int count : new int[] {1, 5, 20, 50, 200}) {
            List<String> gating = new ArrayList<>();
            for (int i = 0; i < count; i++) gating.add("bench_stage_" + i);

            // Everything unlocked, deliberately. isLocked returns on the first stage the viewer
            // is still missing, so a locked player measures one iteration no matter how many
            // stages gate the subject. The player who has it all is the one who pays the full
            // walk — on every check, for the rest of the save.
            Set<String> unlocked = java.util.concurrent.ConcurrentHashMap.newKeySet();
            unlocked.addAll(gating);
            perPlayer.get(player).addAll(gating);
            StageStateView global = StageStateView.of(unlocked);
            // The shape StageLocks.serverIndividual builds: the player's set is looked up again
            // for every single question.
            StageStateView individual = id ->
                    perPlayer.getOrDefault(player, Set.<String>of()).contains(id);

            long g = nanosPerOp(() -> LockResolution.isLocked(gating, global));
            long p = nanosPerOp(() -> LockResolution.isLocked(gating, individual));

            long[] mask = {0L};
            long m = nanosPerOp(() -> { if ((0xFFFFL & ~mask[0]) != 0) mask[0] += 0; });

            report.append(String.format("%-14d %14d %16d %10d%n", count, g, p, m));
        }

        report.append("\nA subject gated by one stage is the normal case and costs nothing either\n")
              .append("way. The mask only starts to matter where a pack gates the same thing from\n")
              .append("many stages at once — a mod lock repeated across progression tiers.\n");
        System.out.println(report);
    }

    @Test
    void measureTheMultiCategoryPass() {
        // What the item path runs: items, mods and tags asked together over the candidate stages.
        List<LockCategory<?>> categories = List.of(
                LockCategories.byId("historystages:items"),
                LockCategories.byId("historystages:mods"),
                LockCategories.byId("historystages:tags"));
        assertNotNull(categories.get(0), "no items category — the benchmark would measure nothing");

        StringBuilder report = new StringBuilder("\n=== multi-category pass, ns per call ===\n");
        report.append(String.format("%-10s %16s%n", "candidates", "gatingStages"));

        Map<String, StageEntry> stages = world(300);
        for (int candidates : new int[] {0, 1, 5, 20}) {
            Set<String> ids = new java.util.LinkedHashSet<>();
            for (int i = 0; i < candidates; i++) ids.add("bench_stage_" + i);
            long ns = nanosPerOp(() ->
                    CategoryLockResolver.gatingStages(categories, "minecraft:stone", ids, stages));
            report.append(String.format("%-10d %16d%n", candidates, ns));
        }

        report.append("\nZero candidates is the relevance index doing its job: the item is in no\n")
              .append("stage at all and the scan is skipped. Anything a bitmask could save has to\n")
              .append("be saved in the other rows.\n");
        System.out.println(report);
    }
}
