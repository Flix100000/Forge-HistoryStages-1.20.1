package net.bananemdnsa.historystages.structure;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Temporary debug visualization for {@link StructureCluster} bounds. Opt-in per player via
 * {@code /history debug viz}. Renders piece outlines (white) and lock-shape outlines
 * (orange / red) using particles sent only to the opted-in player.
 *
 * <p>Throughput is the main performance concern: each particle is one network packet.
 * To keep cost bounded we apply three filters before drawing:
 * <ul>
 *   <li>broad-phase distance test on the cluster envelope ({@link #MAX_SHAPE_DISTANCE_SQ}),</li>
 *   <li>fine-phase distance test on each individual shape,</li>
 *   <li>piece outlines (the densest layer) are drawn only at close range
 *       ({@link #PIECE_DRAW_DISTANCE_SQ}).</li>
 * </ul>
 */
public final class ClusterDebugRenderer {

    private static final Set<UUID> ENABLED = new HashSet<>();

    /** Render every N ticks. Lower = smoother but more network traffic. */
    private static final int RENDER_INTERVAL = 20;
    /** Distance between particles along a BB edge, in blocks. Larger = fewer particles. */
    private static final double PARTICLE_STEP = 2.0;
    /** Beyond this squared distance from the player, a shape is skipped entirely. */
    private static final double MAX_SHAPE_DISTANCE_SQ = 48 * 48;
    /** Piece outlines (white, densest layer) are only drawn within this squared distance. */
    private static final double PIECE_DRAW_DISTANCE_SQ = 24 * 24;

    private static final ParticleOptions PIECE_PARTICLE =
            new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 1.0f);
    private static final ParticleOptions CLUSTER_NEUTRAL =
            new DustParticleOptions(new Vector3f(1.0f, 0.6f, 0.1f), 1.2f);
    private static final ParticleOptions CLUSTER_ACTIVE =
            new DustParticleOptions(new Vector3f(1.0f, 0.1f, 0.1f), 1.4f);

    private ClusterDebugRenderer() {}

    public static boolean toggle(UUID uuid) {
        if (ENABLED.contains(uuid)) {
            ENABLED.remove(uuid);
            return false;
        }
        ENABLED.add(uuid);
        return true;
    }

    public static void disable(UUID uuid) {
        ENABLED.remove(uuid);
    }

    public static boolean isEnabled(UUID uuid) {
        return ENABLED.contains(uuid);
    }

    public static void tick(ServerPlayer player, List<StructureCluster> nearby, List<StructureCluster> activeLocked) {
        if (!ENABLED.contains(player.getUUID())) return;
        if (player.tickCount % RENDER_INTERVAL != 0) return;
        if (nearby == null || nearby.isEmpty()) return;

        ServerLevel level = player.serverLevel();
        Set<StructureCluster> active = activeLocked == null ? Set.of() : new HashSet<>(activeLocked);

        double px = player.getX(), py = player.getY(), pz = player.getZ();

        for (StructureCluster cluster : nearby) {
            double clusterDistSq = distSqToBox(cluster.bounds(), px, py, pz);
            if (clusterDistSq > MAX_SHAPE_DISTANCE_SQ) continue;

            // Piece outlines only at close range — they're the densest and most redundant
            // with the lock-shape outlines (which already contain the inflated pieces).
            if (clusterDistSq <= PIECE_DRAW_DISTANCE_SQ) {
                for (BoundingBox piece : cluster.pieceBoxes()) {
                    if (distSqToBox(piece, px, py, pz) > PIECE_DRAW_DISTANCE_SQ) continue;
                    drawBoxEdges(level, player, piece, PIECE_PARTICLE);
                }
            }

            ParticleOptions shapeParticle = active.contains(cluster) ? CLUSTER_ACTIVE : CLUSTER_NEUTRAL;
            for (BoundingBox shape : cluster.lockShapes()) {
                if (distSqToBox(shape, px, py, pz) > MAX_SHAPE_DISTANCE_SQ) continue;
                drawBoxEdges(level, player, shape, shapeParticle);
            }
        }
    }

    /** Squared min distance from a point to a BlockPos-based bounding box. */
    private static double distSqToBox(BoundingBox bb, double px, double py, double pz) {
        double dx = clampDelta(px, bb.minX(), bb.maxX() + 1.0);
        double dy = clampDelta(py, bb.minY(), bb.maxY() + 1.0);
        double dz = clampDelta(pz, bb.minZ(), bb.maxZ() + 1.0);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double clampDelta(double p, double min, double max) {
        if (p < min) return min - p;
        if (p > max) return p - max;
        return 0.0;
    }

    private static void drawBoxEdges(ServerLevel level, ServerPlayer player, BoundingBox bb, ParticleOptions particle) {
        double x0 = bb.minX(), y0 = bb.minY(), z0 = bb.minZ();
        double x1 = bb.maxX() + 1.0, y1 = bb.maxY() + 1.0, z1 = bb.maxZ() + 1.0;

        drawLine(level, player, x0, y0, z0, x1, y0, z0, particle);
        drawLine(level, player, x0, y0, z1, x1, y0, z1, particle);
        drawLine(level, player, x0, y0, z0, x0, y0, z1, particle);
        drawLine(level, player, x1, y0, z0, x1, y0, z1, particle);
        drawLine(level, player, x0, y1, z0, x1, y1, z0, particle);
        drawLine(level, player, x0, y1, z1, x1, y1, z1, particle);
        drawLine(level, player, x0, y1, z0, x0, y1, z1, particle);
        drawLine(level, player, x1, y1, z0, x1, y1, z1, particle);
        drawLine(level, player, x0, y0, z0, x0, y1, z0, particle);
        drawLine(level, player, x1, y0, z0, x1, y1, z0, particle);
        drawLine(level, player, x0, y0, z1, x0, y1, z1, particle);
        drawLine(level, player, x1, y0, z1, x1, y1, z1, particle);
    }

    private static void drawLine(ServerLevel level, ServerPlayer player,
                                 double x0, double y0, double z0,
                                 double x1, double y1, double z1,
                                 ParticleOptions particle) {
        double dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length <= 0) return;
        int steps = Math.max(1, (int) Math.ceil(length / PARTICLE_STEP));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = x0 + dx * t;
            double y = y0 + dy * t;
            double z = z0 + dz * t;
            level.sendParticles(player, particle, false, x, y, z, 1, 0, 0, 0, 0);
        }
    }
}
