package net.bananemdnsa.historystages.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link StructureCluster}s for every structure start within a chunk radius
 * of a position. The resulting lock area is a union of axis-aligned boxes:
 * <ul>
 *   <li>each structure piece, inflated by {@code padding},</li>
 *   <li>plus a small connector box between every pair of pieces whose pair-distance
 *       (Chebyshev / per-axis max gap) is &le; {@code clusterDistance}.</li>
 * </ul>
 * The union of these shapes traces the structure's actual geometry instead of
 * collapsing into one giant rectangle, so e.g. a desert village turns into
 * house-shaped zones linked by thin paths/connectors.
 */
public final class ClusterBuilder {

    private ClusterBuilder() {}

    public static List<StructureCluster> collectClustersNear(
            ServerLevel level,
            BlockPos center,
            int chunkRadius,
            int padding,
            int clusterDistance
    ) {
        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ChunkPos centerChunk = new ChunkPos(center);

        Map<StructureStart, Structure> starts = new LinkedHashMap<>();
        for (int cx = centerChunk.x - chunkRadius; cx <= centerChunk.x + chunkRadius; cx++) {
            for (int cz = centerChunk.z - chunkRadius; cz <= centerChunk.z + chunkRadius; cz++) {
                ChunkAccess chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
                    StructureStart start = e.getValue();
                    if (start == null || !start.isValid()) continue;
                    starts.putIfAbsent(start, e.getKey());
                }
            }
        }

        if (starts.isEmpty()) return List.of();

        List<StructureCluster> result = new ArrayList<>();
        for (Map.Entry<StructureStart, Structure> entry : starts.entrySet()) {
            Holder.Reference<Structure> holder = resolveHolder(registry, entry.getValue());
            if (holder == null) continue;
            buildFor(entry.getKey(), holder, padding, clusterDistance, result);
        }
        return result;
    }

    private static Holder.Reference<Structure> resolveHolder(Registry<Structure> registry, Structure structure) {
        var key = registry.getKey(structure);
        if (key == null) return null;
        return registry.getHolder(ResourceKey.create(Registries.STRUCTURE, key)).orElse(null);
    }

    private static void buildFor(
            StructureStart start,
            Holder.Reference<Structure> holder,
            int padding,
            int clusterDistance,
            List<StructureCluster> out
    ) {
        List<BoundingBox> rawPieces = new ArrayList<>(start.getPieces().size());
        for (var piece : start.getPieces()) {
            rawPieces.add(piece.getBoundingBox());
        }
        if (rawPieces.isEmpty()) return;

        // Drop "umbrella" pieces — a piece whose BB strictly contains another piece's BB.
        // Some structures (modded villages, plot-marker structures, jigsaw "claim" pieces)
        // include a single giant piece that encompasses the whole structure for layout
        // purposes. Without filtering, that piece would be drawn as a huge orange box
        // outside the actual rooms and would trigger the lock when entered.
        List<BoundingBox> pieceBoxes = new ArrayList<>(rawPieces.size());
        for (int i = 0; i < rawPieces.size(); i++) {
            BoundingBox candidate = rawPieces.get(i);
            boolean isUmbrella = false;
            for (int j = 0; j < rawPieces.size(); j++) {
                if (i == j) continue;
                if (strictlyContains(candidate, rawPieces.get(j))) {
                    isUmbrella = true;
                    break;
                }
            }
            if (!isUmbrella) pieceBoxes.add(candidate);
        }
        if (pieceBoxes.isEmpty()) pieceBoxes = rawPieces; // safety fallback

        // 1. Group pieces into clusters via union-find on pair-distance ≤ clusterDistance.
        int n = pieceBoxes.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (find(parent, i) == find(parent, j)) continue;
                if (pieceDistance(pieceBoxes.get(i), pieceBoxes.get(j)) <= clusterDistance) {
                    union(parent, i, j);
                }
            }
        }

        Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            groups.computeIfAbsent(find(parent, i), k -> new ArrayList<>()).add(i);
        }

        // 2. For each cluster, build lockShapes = inflated pieces + connectors between
        //    nearby pieces within the cluster.
        for (List<Integer> indices : groups.values()) {
            List<BoundingBox> originals = new ArrayList<>(indices.size());
            List<BoundingBox> shapes = new ArrayList<>();

            for (int idx : indices) {
                BoundingBox piece = pieceBoxes.get(idx);
                originals.add(piece);
                shapes.add(padding > 0 ? piece.inflatedBy(padding) : piece);
            }

            // Connectors between every nearby pair in this cluster.
            for (int a = 0; a < indices.size(); a++) {
                for (int b = a + 1; b < indices.size(); b++) {
                    BoundingBox pa = pieceBoxes.get(indices.get(a));
                    BoundingBox pb = pieceBoxes.get(indices.get(b));
                    if (pieceDistance(pa, pb) > clusterDistance) continue;
                    BoundingBox connector = buildConnector(pa, pb, padding);
                    if (connector != null) shapes.add(connector);
                }
            }

            // NOTE: BoundingBox#encapsulate mutates `this` in-place in vanilla 1.21, so
            // chaining it on shapes.get(0) would silently rewrite the first shape into
            // the cluster envelope. Compute the envelope manually instead.
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BoundingBox s : shapes) {
                if (s.minX() < minX) minX = s.minX();
                if (s.minY() < minY) minY = s.minY();
                if (s.minZ() < minZ) minZ = s.minZ();
                if (s.maxX() > maxX) maxX = s.maxX();
                if (s.maxY() > maxY) maxY = s.maxY();
                if (s.maxZ() > maxZ) maxZ = s.maxZ();
            }
            BoundingBox bounds = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);

            out.add(new StructureCluster(bounds, holder, List.copyOf(originals), List.copyOf(shapes)));
        }
    }

    /**
     * Builds a connector box bridging two pieces along a single axis.
     * <ul>
     *   <li>If the pieces overlap in every axis: no connector needed, returns {@code null}.</li>
     *   <li>If they are separated in exactly one axis: connector spans the gap in that
     *       axis and the natural piece overlap in the perpendicular axes — the bridge is
     *       as wide as the two pieces have in common, never wider.</li>
     *   <li>If they are separated in two or more axes (diagonal): no connector. Otherwise
     *       grid-arranged pieces would fill in their diagonals and recreate one big box.</li>
     * </ul>
     */
    private static BoundingBox buildConnector(BoundingBox a, BoundingBox b, int padding) {
        int xGap = axisGap(a.minX(), a.maxX(), b.minX(), b.maxX());
        int yGap = axisGap(a.minY(), a.maxY(), b.minY(), b.maxY());
        int zGap = axisGap(a.minZ(), a.maxZ(), b.minZ(), b.maxZ());
        int separatedAxes = (xGap > 0 ? 1 : 0) + (yGap > 0 ? 1 : 0) + (zGap > 0 ? 1 : 0);
        if (separatedAxes == 0) return null;
        if (separatedAxes >= 2) return null;

        int[] x = axisSpan(a.minX(), a.maxX(), b.minX(), b.maxX());
        int[] y = axisSpan(a.minY(), a.maxY(), b.minY(), b.maxY());
        int[] z = axisSpan(a.minZ(), a.maxZ(), b.minZ(), b.maxZ());

        BoundingBox connector = new BoundingBox(x[0], y[0], z[0], x[1], y[1], z[1]);
        return padding > 0 ? connector.inflatedBy(padding) : connector;
    }

    /** True when {@code outer}'s BB fully contains {@code inner}'s BB and the two are not equal. */
    private static boolean strictlyContains(BoundingBox outer, BoundingBox inner) {
        boolean contains =
                outer.minX() <= inner.minX() && outer.maxX() >= inner.maxX() &&
                outer.minY() <= inner.minY() && outer.maxY() >= inner.maxY() &&
                outer.minZ() <= inner.minZ() && outer.maxZ() >= inner.maxZ();
        if (!contains) return false;
        boolean equal =
                outer.minX() == inner.minX() && outer.maxX() == inner.maxX() &&
                outer.minY() == inner.minY() && outer.maxY() == inner.maxY() &&
                outer.minZ() == inner.minZ() && outer.maxZ() == inner.maxZ();
        return !equal;
    }

    private static int[] axisSpan(int aMin, int aMax, int bMin, int bMax) {
        if (aMax < bMin) return new int[]{aMax, bMin};
        if (bMax < aMin) return new int[]{bMax, aMin};
        return new int[]{Math.max(aMin, bMin), Math.min(aMax, bMax)};
    }

    /** Chebyshev distance between two axis-aligned BBs (0 if they overlap). */
    private static int pieceDistance(BoundingBox a, BoundingBox b) {
        int dx = axisGap(a.minX(), a.maxX(), b.minX(), b.maxX());
        int dy = axisGap(a.minY(), a.maxY(), b.minY(), b.maxY());
        int dz = axisGap(a.minZ(), a.maxZ(), b.minZ(), b.maxZ());
        return Math.max(Math.max(dx, dy), dz);
    }

    private static int axisGap(int aMin, int aMax, int bMin, int bMax) {
        if (aMax < bMin) return bMin - aMax;
        if (bMax < aMin) return aMin - bMax;
        return 0;
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int ra = find(parent, a), rb = find(parent, b);
        if (ra != rb) parent[ra] = rb;
    }
}
