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

import net.bananemdnsa.historystages.util.DerivedCache;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds {@link StructureCluster}s by growing a zone outward from a seed piece.
 *
 * <p>Algorithm per structure start:
 * <ol>
 *   <li>Filter out "umbrella" pieces (any piece whose BB strictly contains another piece's BB).
 *       These appear in modded villages / jigsaw plot markers and would otherwise drag a giant
 *       envelope into the zone.</li>
 *   <li>Pick an unassigned piece as the seed; the zone starts as that piece inflated by
 *       {@code padding} (the "orange box").</li>
 *   <li><b>Overlap merge</b>: absorb every remaining piece whose raw BB intersects any shape in
 *       the zone. Repeat until no piece overlaps. The zone is allowed to take any non-convex
 *       shape (L, T, hollow, etc.) — it's stored as a union of axis-aligned boxes.</li>
 *   <li><b>Bridge phase</b>: search outward by {@code clusterDistance} (default 6 blocks, "wenn
 *       wir in diesem Radius eine weitere PieceBB finden"). For the first remaining piece within
 *       reach, build a connector bridge BB (single-axis) or an L-shaped pair (two-axis diagonal),
 *       absorb the bridge and the piece. <b>Restart from step 3 after every bridge added</b> —
 *       the new piece might overlap others and the 6-block search must be re-evaluated against
 *       the grown zone.</li>
 *   <li>When no more pieces overlap or are within reach, emit the cluster and start over with
 *       a new seed.</li>
 * </ol>
 *
 * <p><b>"3-Block-Erweiterung" (3-block height expansion):</b>
 * After a zone is finalized, the union is scanned per (x,z) column. For each contiguous
 * occupied Y-interval whose height is &lt; 3 blocks, a cap box is added on top so the union
 * is 3 blocks thick there. Cap boxes are then greedily merged into the minimum number of
 * rectangles (strip merge along Z then X) so the renderer doesn't get spammed with tiny
 * 1×1×N boxes. Only the topmost thin interval per column gets capped — capping a lower
 * thin interval would intrude into shapes above it.
 */
public final class ClusterBuilder {

    /** Minimum Y-thickness of a lock shape after the height-expansion pass. */
    private static final int MIN_LOCK_HEIGHT = 5;

    /**
     * Extra padding added to every piece BEFORE overlap-merge and bridging — a safety wall
     * between the actual structure blocks and the player. Stacks on top of the configured
     * {@code structureLockPadding} so even with config = 0 there's always a buffer. Because
     * this widens every seed shape, the overlap step naturally absorbs pieces that were close
     * but not quite touching, and the bridge step bridges across the (now-larger) zone.
     */
    private static final int SAFETY_PADDING = 2;

    private ClusterBuilder() {}

    /**
     * One structure start's clusters, kept for as long as the start itself is loaded.
     *
     * <p>The zone a start produces is a pure function of its pieces and the two settings below,
     * and its pieces never change while it is loaded. Recomputing it was the expensive half of
     * every scan: {@link #ensureMinHeight} allocates an {@code int[width * depth]} pair over the
     * cluster's envelope, so one large jigsaw village costs a few hundred kilobytes and a pass
     * over every column — and both callers used to pay that again per player and per chunk
     * crossing, for zones that had not moved.
     *
     * <p>Nothing invalidates this by hand. Unloading the chunk drops the start and the entry with
     * it; a settings change is caught by the stamp. See {@link DerivedCache}.
     */
    private static final DerivedCache<StructureStart, List<StructureCluster>> ZONES =
            new DerivedCache<>();

    /**
     * How often a scan reused a remembered zone, and how often it had to build one.
     *
     * <p>For the runtime log. Whether this cache is working is not visible from the outside —
     * a broken one is only slow — so the numbers have to be readable somewhere.
     */
    public static String cacheStats() {
        return ZONES.hits() + " reused / " + ZONES.derivations() + " built, "
                + ZONES.size() + " held";
    }

    public static List<StructureCluster> collectClustersNear(
            ServerLevel level,
            BlockPos center,
            int chunkRadius,
            int padding,
            int clusterDistance
    ) {
        ChunkPos centerChunk = new ChunkPos(center);

        Set<StructureStart> starts = new LinkedHashSet<>();
        for (int cx = centerChunk.x - chunkRadius; cx <= centerChunk.x + chunkRadius; cx++) {
            for (int cz = centerChunk.z - chunkRadius; cz <= centerChunk.z + chunkRadius; cz++) {
                ChunkAccess chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (StructureStart start : chunk.getAllStarts().values()) {
                    if (start == null || !start.isValid()) continue;
                    starts.add(start);
                }
            }
        }

        if (starts.isEmpty()) return List.of();

        Registry<Structure> registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        long settings = DerivedCache.stamp(padding, clusterDistance);

        List<StructureCluster> result = new ArrayList<>();
        for (StructureStart start : starts) {
            result.addAll(ZONES.get(start, settings, () -> zonesOf(registry, start, padding, clusterDistance)));
        }
        return result;
    }

    /** The uncached build for one start, and the only thing {@link #ZONES} ever runs. */
    private static List<StructureCluster> zonesOf(Registry<Structure> registry, StructureStart start,
                                                  int padding, int clusterDistance) {
        Holder.Reference<Structure> holder = resolveHolder(registry, start.getStructure());
        if (holder == null) return List.of();

        List<StructureCluster> zones = new ArrayList<>();
        buildFor(start, holder, padding, clusterDistance, zones);
        // Immutable, because every caller from here on shares this exact list.
        return List.copyOf(zones);
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
        // Safety-wall buffer applied to every piece before overlap-merge and bridging so
        // there's always a gap between the actual structure blocks and where the lock kicks in.
        int effectivePadding = padding + SAFETY_PADDING;

        List<BoundingBox> pieces = filterUmbrellaPieces(start);
        if (pieces.isEmpty()) return;

        Set<Integer> remaining = new LinkedHashSet<>();
        for (int i = 0; i < pieces.size(); i++) remaining.add(i);

        while (!remaining.isEmpty()) {
            int seed = remaining.iterator().next();
            remaining.remove(seed);

            List<BoundingBox> zonePieces = new ArrayList<>();
            List<BoundingBox> zoneShapes = new ArrayList<>();
            absorbPiece(pieces.get(seed), effectivePadding, zonePieces, zoneShapes);

            // Alternate overlap-merge and bridge until neither can grow the zone further.
            while (true) {
                drainOverlapping(pieces, remaining, zonePieces, zoneShapes, effectivePadding);

                BridgeCandidate bc = findNearestBridge(pieces, remaining, zoneShapes, clusterDistance, effectivePadding);
                if (bc == null) break;

                remaining.remove(bc.pieceIndex);
                zoneShapes.addAll(bc.bridgeShapes);
                absorbPiece(pieces.get(bc.pieceIndex), effectivePadding, zonePieces, zoneShapes);
                // Loop restarts: new piece may overlap others; 6-block search re-runs from the
                // now-larger zone.
            }

            List<BoundingBox> finalShapes = ensureMinHeight(zoneShapes, MIN_LOCK_HEIGHT);
            out.add(new StructureCluster(
                    envelopeOf(finalShapes),
                    holder,
                    List.copyOf(zonePieces),
                    List.copyOf(finalShapes)
            ));
        }
    }

    /** Drops pieces whose BB strictly contains another piece's BB (modded jigsaw "claim" pieces). */
    private static List<BoundingBox> filterUmbrellaPieces(StructureStart start) {
        List<BoundingBox> raw = new ArrayList<>(start.getPieces().size());
        for (var piece : start.getPieces()) raw.add(piece.getBoundingBox());
        if (raw.isEmpty()) return raw;

        List<BoundingBox> filtered = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            BoundingBox candidate = raw.get(i);
            boolean isUmbrella = false;
            for (int j = 0; j < raw.size(); j++) {
                if (i == j) continue;
                if (strictlyContains(candidate, raw.get(j))) {
                    isUmbrella = true;
                    break;
                }
            }
            if (!isUmbrella) filtered.add(candidate);
        }
        return filtered.isEmpty() ? raw : filtered;
    }

    private static void absorbPiece(BoundingBox piece, int padding,
                                    List<BoundingBox> zonePieces, List<BoundingBox> zoneShapes) {
        zonePieces.add(piece);
        zoneShapes.add(padding > 0 ? piece.inflatedBy(padding) : piece);
    }

    /** Absorbs every remaining piece whose raw BB intersects any shape in the current zone. */
    private static void drainOverlapping(List<BoundingBox> pieces, Set<Integer> remaining,
                                         List<BoundingBox> zonePieces, List<BoundingBox> zoneShapes,
                                         int padding) {
        boolean grew = true;
        while (grew) {
            grew = false;
            Integer hit = null;
            for (int idx : remaining) {
                BoundingBox piece = pieces.get(idx);
                for (BoundingBox shape : zoneShapes) {
                    if (intersects(piece, shape)) {
                        hit = idx;
                        break;
                    }
                }
                if (hit != null) break;
            }
            if (hit != null) {
                remaining.remove(hit);
                absorbPiece(pieces.get(hit), padding, zonePieces, zoneShapes);
                grew = true;
            }
        }
    }

    private record BridgeCandidate(int pieceIndex, List<BoundingBox> bridgeShapes) {}

    /**
     * Finds the first remaining piece within {@code maxDistance} of any zone shape and builds
     * the bridge BB(s) to connect them. Returns {@code null} if nothing is within reach.
     */
    private static BridgeCandidate findNearestBridge(List<BoundingBox> pieces, Set<Integer> remaining,
                                                     List<BoundingBox> zoneShapes, int maxDistance,
                                                     int padding) {
        for (int idx : remaining) {
            BoundingBox piece = pieces.get(idx);
            BoundingBox bestShape = null;
            int bestDist = Integer.MAX_VALUE;
            for (BoundingBox shape : zoneShapes) {
                int d = pieceDistance(shape, piece);
                if (d <= maxDistance && d < bestDist) {
                    bestDist = d;
                    bestShape = shape;
                }
            }
            if (bestShape == null) continue;

            List<BoundingBox> bridges = buildBridge(bestShape, piece, padding);
            // Diagonal in all 3 axes is skipped (rare); such pieces typically get reached via
            // an intermediate piece in a later iteration.
            if (bridges.isEmpty()) continue;

            return new BridgeCandidate(idx, bridges);
        }
        return null;
    }

    /**
     * Builds connector BBs between two boxes:
     * <ul>
     *   <li>0 axes separated → already overlapping, no bridge needed (caller shouldn't reach here)</li>
     *   <li>1 axis separated → single connector spanning the gap, using the perpendicular overlap</li>
     *   <li>2 axes separated → L-shape made of two perpendicular connectors meeting at a corner</li>
     *   <li>3 axes separated → skipped (returns empty); rare and visually confusing</li>
     * </ul>
     */
    private static List<BoundingBox> buildBridge(BoundingBox a, BoundingBox b, int padding) {
        int xGap = axisGap(a.minX(), a.maxX(), b.minX(), b.maxX());
        int yGap = axisGap(a.minY(), a.maxY(), b.minY(), b.maxY());
        int zGap = axisGap(a.minZ(), a.maxZ(), b.minZ(), b.maxZ());
        int axes = (xGap > 0 ? 1 : 0) + (yGap > 0 ? 1 : 0) + (zGap > 0 ? 1 : 0);

        if (axes == 0) return List.of();
        if (axes >= 3) return List.of();

        if (axes == 1) {
            return List.of(singleAxisBridge(a, b, padding));
        }
        return lBridge(a, b, padding, xGap > 0, yGap > 0, zGap > 0);
    }

    private static BoundingBox singleAxisBridge(BoundingBox a, BoundingBox b, int padding) {
        int[] x = axisSpan(a.minX(), a.maxX(), b.minX(), b.maxX());
        int[] y = axisSpan(a.minY(), a.maxY(), b.minY(), b.maxY());
        int[] z = axisSpan(a.minZ(), a.maxZ(), b.minZ(), b.maxZ());
        BoundingBox bridge = new BoundingBox(x[0], y[0], z[0], x[1], y[1], z[1]);
        return padding > 0 ? bridge.inflatedBy(padding) : bridge;
    }

    /**
     * Builds two perpendicular connectors forming an L between {@code a} and {@code b}.
     * The corner sits at {@code a}'s edge in the "first" gapped axis and {@code b}'s edge in
     * the "second" gapped axis — i.e. the bridge leaves {@code a} along one axis, then turns
     * and enters {@code b} along the other.
     */
    private static List<BoundingBox> lBridge(BoundingBox a, BoundingBox b, int padding,
                                              boolean gapX, boolean gapY, boolean gapZ) {
        // Determine which two axes are gapped. The third axis must overlap (axes == 2 case).
        int[] xSpan = axisSpan(a.minX(), a.maxX(), b.minX(), b.maxX());
        int[] ySpan = axisSpan(a.minY(), a.maxY(), b.minY(), b.maxY());
        int[] zSpan = axisSpan(a.minZ(), a.maxZ(), b.minZ(), b.maxZ());

        // First segment uses a's range in the second-gapped axis; second segment uses b's
        // range in the first-gapped axis. Pick axis order so the order is deterministic
        // (X before Y before Z).
        BoundingBox seg1, seg2;
        if (gapX && gapZ) {
            // First leg along X at a's Z; second leg along Z at b's X.
            seg1 = new BoundingBox(xSpan[0], ySpan[0], a.minZ(), xSpan[1], ySpan[1], a.maxZ());
            seg2 = new BoundingBox(b.minX(), ySpan[0], zSpan[0], b.maxX(), ySpan[1], zSpan[1]);
        } else if (gapX && gapY) {
            // First leg along X at a's Y; second leg along Y at b's X.
            seg1 = new BoundingBox(xSpan[0], a.minY(), zSpan[0], xSpan[1], a.maxY(), zSpan[1]);
            seg2 = new BoundingBox(b.minX(), ySpan[0], zSpan[0], b.maxX(), ySpan[1], zSpan[1]);
        } else {
            // gapY && gapZ — first leg along Y at a's Z; second leg along Z at b's Y.
            seg1 = new BoundingBox(xSpan[0], ySpan[0], a.minZ(), xSpan[1], ySpan[1], a.maxZ());
            seg2 = new BoundingBox(xSpan[0], b.minY(), zSpan[0], xSpan[1], b.maxY(), zSpan[1]);
        }
        if (padding > 0) {
            seg1 = seg1.inflatedBy(padding);
            seg2 = seg2.inflatedBy(padding);
        }
        return List.of(seg1, seg2);
    }

    /**
     * "3-Block-Erweiterung" — see class javadoc. Scans the union per (x,z) column, finds
     * each contiguous occupied Y-interval, and for any interval thinner than {@code minHeight}
     * records a cap requirement on top of it. Cap requirements are then merged into rectangles
     * via greedy strip merging (extend along Z first, then along X while the full Z-strip
     * matches) and returned appended to the original shapes.
     */
    private static List<BoundingBox> ensureMinHeight(List<BoundingBox> shapes, int minHeight) {
        if (shapes.isEmpty() || minHeight <= 1) return shapes;

        int envMinX = Integer.MAX_VALUE, envMaxX = Integer.MIN_VALUE;
        int envMinZ = Integer.MAX_VALUE, envMaxZ = Integer.MIN_VALUE;
        for (BoundingBox s : shapes) {
            if (s.minX() < envMinX) envMinX = s.minX();
            if (s.maxX() > envMaxX) envMaxX = s.maxX();
            if (s.minZ() < envMinZ) envMinZ = s.minZ();
            if (s.maxZ() > envMaxZ) envMaxZ = s.maxZ();
        }
        int width = envMaxX - envMinX + 1;
        int depth = envMaxZ - envMinZ + 1;

        // Per-column cap requirement: [capMinY, capMaxY]. Sentinel capMinY = Integer.MIN_VALUE.
        int[] capMinY = new int[width * depth];
        int[] capMaxY = new int[width * depth];
        Arrays.fill(capMinY, Integer.MIN_VALUE);

        // Reusable interval buffer: pairs (minY, maxY) for shapes covering one column.
        int[] ivBuf = new int[shapes.size() * 2];

        for (int xi = 0; xi < width; xi++) {
            int x = envMinX + xi;
            for (int zi = 0; zi < depth; zi++) {
                int z = envMinZ + zi;

                int n = 0;
                for (BoundingBox s : shapes) {
                    if (x >= s.minX() && x <= s.maxX() && z >= s.minZ() && z <= s.maxZ()) {
                        ivBuf[2 * n] = s.minY();
                        ivBuf[2 * n + 1] = s.maxY();
                        n++;
                    }
                }
                if (n == 0) continue;

                // Insertion sort by minY (n is small in practice).
                for (int i = 1; i < n; i++) {
                    int a = ivBuf[2 * i], b = ivBuf[2 * i + 1];
                    int j = i - 1;
                    while (j >= 0 && ivBuf[2 * j] > a) {
                        ivBuf[2 * (j + 1)] = ivBuf[2 * j];
                        ivBuf[2 * (j + 1) + 1] = ivBuf[2 * j + 1];
                        j--;
                    }
                    ivBuf[2 * (j + 1)] = a;
                    ivBuf[2 * (j + 1) + 1] = b;
                }

                // Walk merged intervals; track the topmost thin one (its top determines the
                // cap base, its bottom determines how high the cap must reach).
                int curMin = ivBuf[0], curMax = ivBuf[1];
                int chosenTopY = Integer.MIN_VALUE;
                int chosenCapTop = Integer.MIN_VALUE;
                for (int i = 1; i < n; i++) {
                    int a = ivBuf[2 * i], b = ivBuf[2 * i + 1];
                    if (a <= curMax + 1) {
                        if (b > curMax) curMax = b;
                    } else {
                        if (curMax - curMin + 1 < minHeight && curMax > chosenTopY) {
                            chosenTopY = curMax;
                            chosenCapTop = curMin + minHeight - 1;
                        }
                        curMin = a;
                        curMax = b;
                    }
                }
                if (curMax - curMin + 1 < minHeight && curMax > chosenTopY) {
                    chosenTopY = curMax;
                    chosenCapTop = curMin + minHeight - 1;
                }

                if (chosenTopY != Integer.MIN_VALUE) {
                    int idx = xi * depth + zi;
                    capMinY[idx] = chosenTopY + 1;
                    capMaxY[idx] = chosenCapTop;
                }
            }
        }

        // Greedy strip merge: collapse equal-cap columns into rectangles.
        List<BoundingBox> caps = new ArrayList<>();
        boolean[] used = new boolean[width * depth];
        for (int xi = 0; xi < width; xi++) {
            for (int zi = 0; zi < depth; zi++) {
                int idx = xi * depth + zi;
                if (used[idx] || capMinY[idx] == Integer.MIN_VALUE) continue;
                int cMin = capMinY[idx], cMax = capMaxY[idx];

                int z1 = zi;
                while (z1 + 1 < depth) {
                    int nIdx = xi * depth + (z1 + 1);
                    if (used[nIdx] || capMinY[nIdx] != cMin || capMaxY[nIdx] != cMax) break;
                    z1++;
                }
                int x1 = xi;
                while (x1 + 1 < width) {
                    boolean ok = true;
                    for (int z = zi; z <= z1; z++) {
                        int nIdx = (x1 + 1) * depth + z;
                        if (used[nIdx] || capMinY[nIdx] != cMin || capMaxY[nIdx] != cMax) {
                            ok = false;
                            break;
                        }
                    }
                    if (!ok) break;
                    x1++;
                }
                for (int x = xi; x <= x1; x++) {
                    for (int z = zi; z <= z1; z++) used[x * depth + z] = true;
                }
                caps.add(new BoundingBox(envMinX + xi, cMin, envMinZ + zi,
                                          envMinX + x1, cMax, envMinZ + z1));
            }
        }

        if (caps.isEmpty()) return shapes;
        List<BoundingBox> result = new ArrayList<>(shapes.size() + caps.size());
        result.addAll(shapes);
        result.addAll(caps);
        return result;
    }

    private static BoundingBox envelopeOf(List<BoundingBox> shapes) {
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
        return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean intersects(BoundingBox a, BoundingBox b) {
        return a.maxX() >= b.minX() && a.minX() <= b.maxX()
            && a.maxY() >= b.minY() && a.minY() <= b.maxY()
            && a.maxZ() >= b.minZ() && a.minZ() <= b.maxZ();
    }

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
}
