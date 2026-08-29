package net.bananemdnsa.historystages.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.Util;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws a force-field "patch" on the faces of the orange lock zone — the union of all
 * {@link BoundingBox}es pushed by the server. Each face is subdivided into 1-block cells
 * and a cell is only drawn if its exterior side is not occluded by another shape in the
 * zone, so the texture traces the silhouette of the union instead of every individual box.
 *
 * <p>Only cells near the camera's projection onto each face are drawn (patch reveal —
 * texture appears as you approach the wall). UV mapping is world-anchored with a slow
 * time-based scroll for the classic force-field animation.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class LockBorderRenderer {

    private static final ResourceLocation FORCEFIELD =
            ResourceLocation.parse("minecraft:textures/misc/forcefield.png");

    /** How many blocks one texture tile covers. Smaller = more tiling on the wall. */
    private static final float TILE_BLOCKS = 4.0f;

    /** Alpha of the patch center; falls off linearly with distance from camera. */
    private static final float CENTER_ALPHA = 0.9f;

    private LockBorderRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!Config.VISUAL.structureBorderEnabled.get()) return;

        List<BoundingBox> boxes = LockBorderClientCache.get();
        if (boxes.isEmpty()) return;

        double threshold = Config.VISUAL.structureBorderDistance.get();

        Vec3 cam = event.getCamera().getPosition();
        float scroll = (float) ((Util.getMillis() % 3000L) / 3000.0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        // Depth test ON: only the parts of the force-field in front of blocks are drawn.
        // depthMask(false) keeps us from writing to the depth buffer ourselves.
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, FORCEFIELD);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = pose.last().pose();

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (int i = 0; i < boxes.size(); i++) {
            renderBox(buffer, matrix, boxes, i, boxes.get(i), cam, threshold, scroll);
        }

        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        pose.popPose();

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void renderBox(BufferBuilder buf, Matrix4f m, List<BoundingBox> all, int ownerIdx,
                                   BoundingBox bb, Vec3 cam, double threshold, float scroll) {
        float x0 = bb.minX();
        float y0 = bb.minY();
        float z0 = bb.minZ();
        float x1 = bb.maxX() + 1.0f;
        float y1 = bb.maxY() + 1.0f;
        float z1 = bb.maxZ() + 1.0f;

        // Broad-phase: skip the whole box if its closest point is beyond the threshold.
        double cx = clamp(cam.x, x0, x1);
        double cy = clamp(cam.y, y0, y1);
        double cz = clamp(cam.z, z0, z1);
        double dx = cam.x - cx, dy = cam.y - cy, dz = cam.z - cz;
        double boxDistSq = dx * dx + dy * dy + dz * dz;
        if (boxDistSq > threshold * threshold) return;

        // X-normal faces
        renderFaceX(buf, m, all, ownerIdx, bb, x0, y0, y1, z0, z1, cam, threshold, scroll, 0);
        renderFaceX(buf, m, all, ownerIdx, bb, x1, y0, y1, z0, z1, cam, threshold, scroll, 1);
        // Y-normal faces
        renderFaceY(buf, m, all, ownerIdx, bb, y0, x0, x1, z0, z1, cam, threshold, scroll, 2);
        renderFaceY(buf, m, all, ownerIdx, bb, y1, x0, x1, z0, z1, cam, threshold, scroll, 3);
        // Z-normal faces
        renderFaceZ(buf, m, all, ownerIdx, bb, z0, x0, x1, y0, y1, cam, threshold, scroll, 4);
        renderFaceZ(buf, m, all, ownerIdx, bb, z1, x0, x1, y0, y1, cam, threshold, scroll, 5);
    }

    private static void renderFaceX(BufferBuilder buf, Matrix4f m, List<BoundingBox> all, int ownerIdx,
                                     BoundingBox owner, float planeX, float minY, float maxY,
                                     float minZ, float maxZ, Vec3 cam, double threshold, float scroll,
                                     int face) {
        double py = clamp(cam.y, minY, maxY);
        double pz = clamp(cam.z, minZ, maxZ);
        double ddx = cam.x - planeX, ddy = cam.y - py, ddz = cam.z - pz;
        double dist = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
        if (dist > threshold) return;

        float radius = (float) ((1.0 - dist / threshold) * threshold);
        if (radius <= 0) return;

        float patchMinY = Math.max(minY, (float) py - radius);
        float patchMaxY = Math.min(maxY, (float) py + radius);
        float patchMinZ = Math.max(minZ, (float) pz - radius);
        float patchMaxZ = Math.min(maxZ, (float) pz + radius);
        if (patchMinY >= patchMaxY || patchMinZ >= patchMaxZ) return;

        int alpha = alphaForDist(dist, threshold);
        boolean[] mask = maskFor(all, ownerIdx, face);
        int zSpan = owner.maxZ() - owner.minZ() + 1;

        int yStart = (int) Math.floor(patchMinY);
        int yEnd = (int) Math.ceil(patchMaxY);
        int zStart = (int) Math.floor(patchMinZ);
        int zEnd = (int) Math.ceil(patchMaxZ);

        for (int yi = yStart; yi < yEnd; yi++) {
            float cy0 = Math.max(yi, patchMinY);
            float cy1 = Math.min(yi + 1, patchMaxY);
            if (cy0 >= cy1) continue;
            for (int zi = zStart; zi < zEnd; zi++) {
                float cz0 = Math.max(zi, patchMinZ);
                float cz1 = Math.min(zi + 1, patchMaxZ);
                if (cz0 >= cz1) continue;
                if (!mask[(yi - owner.minY()) * zSpan + (zi - owner.minZ())]) continue;

                float uMin = cz0 / TILE_BLOCKS + scroll;
                float uMax = cz1 / TILE_BLOCKS + scroll;
                float vMin = cy0 / TILE_BLOCKS - scroll;
                float vMax = cy1 / TILE_BLOCKS - scroll;
                buf.addVertex(m, planeX, cy0, cz0).setUv(uMin, vMin).setColor(230, 20, 20, alpha);
                buf.addVertex(m, planeX, cy0, cz1).setUv(uMax, vMin).setColor(230, 20, 20, alpha);
                buf.addVertex(m, planeX, cy1, cz1).setUv(uMax, vMax).setColor(230, 20, 20, alpha);
                buf.addVertex(m, planeX, cy1, cz0).setUv(uMin, vMax).setColor(230, 20, 20, alpha);
            }
        }
    }

    private static void renderFaceY(BufferBuilder buf, Matrix4f m, List<BoundingBox> all, int ownerIdx,
                                     BoundingBox owner, float planeY, float minX, float maxX,
                                     float minZ, float maxZ, Vec3 cam, double threshold, float scroll,
                                     int face) {
        double px = clamp(cam.x, minX, maxX);
        double pz = clamp(cam.z, minZ, maxZ);
        double ddx = cam.x - px, ddy = cam.y - planeY, ddz = cam.z - pz;
        double dist = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
        if (dist > threshold) return;

        float radius = (float) ((1.0 - dist / threshold) * threshold);
        if (radius <= 0) return;

        float patchMinX = Math.max(minX, (float) px - radius);
        float patchMaxX = Math.min(maxX, (float) px + radius);
        float patchMinZ = Math.max(minZ, (float) pz - radius);
        float patchMaxZ = Math.min(maxZ, (float) pz + radius);
        if (patchMinX >= patchMaxX || patchMinZ >= patchMaxZ) return;

        int alpha = alphaForDist(dist, threshold);
        boolean[] mask = maskFor(all, ownerIdx, face);
        int zSpanY = owner.maxZ() - owner.minZ() + 1;

        int xStart = (int) Math.floor(patchMinX);
        int xEnd = (int) Math.ceil(patchMaxX);
        int zStart = (int) Math.floor(patchMinZ);
        int zEnd = (int) Math.ceil(patchMaxZ);

        for (int xi = xStart; xi < xEnd; xi++) {
            float cx0 = Math.max(xi, patchMinX);
            float cx1 = Math.min(xi + 1, patchMaxX);
            if (cx0 >= cx1) continue;
            for (int zi = zStart; zi < zEnd; zi++) {
                float cz0 = Math.max(zi, patchMinZ);
                float cz1 = Math.min(zi + 1, patchMaxZ);
                if (cz0 >= cz1) continue;
                if (!mask[(xi - owner.minX()) * zSpanY + (zi - owner.minZ())]) continue;

                float uMin = cx0 / TILE_BLOCKS + scroll;
                float uMax = cx1 / TILE_BLOCKS + scroll;
                float vMin = cz0 / TILE_BLOCKS - scroll;
                float vMax = cz1 / TILE_BLOCKS - scroll;
                buf.addVertex(m, cx0, planeY, cz0).setUv(uMin, vMin).setColor(230, 20, 20, alpha);
                buf.addVertex(m, cx1, planeY, cz0).setUv(uMax, vMin).setColor(230, 20, 20, alpha);
                buf.addVertex(m, cx1, planeY, cz1).setUv(uMax, vMax).setColor(230, 20, 20, alpha);
                buf.addVertex(m, cx0, planeY, cz1).setUv(uMin, vMax).setColor(230, 20, 20, alpha);
            }
        }
    }

    private static void renderFaceZ(BufferBuilder buf, Matrix4f m, List<BoundingBox> all, int ownerIdx,
                                     BoundingBox owner, float planeZ, float minX, float maxX,
                                     float minY, float maxY, Vec3 cam, double threshold, float scroll,
                                     int face) {
        double px = clamp(cam.x, minX, maxX);
        double py = clamp(cam.y, minY, maxY);
        double ddx = cam.x - px, ddy = cam.y - py, ddz = cam.z - planeZ;
        double dist = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
        if (dist > threshold) return;

        float radius = (float) ((1.0 - dist / threshold) * threshold);
        if (radius <= 0) return;

        float patchMinX = Math.max(minX, (float) px - radius);
        float patchMaxX = Math.min(maxX, (float) px + radius);
        float patchMinY = Math.max(minY, (float) py - radius);
        float patchMaxY = Math.min(maxY, (float) py + radius);
        if (patchMinX >= patchMaxX || patchMinY >= patchMaxY) return;

        int alpha = alphaForDist(dist, threshold);
        boolean[] mask = maskFor(all, ownerIdx, face);
        int ySpanZ = owner.maxY() - owner.minY() + 1;

        int xStart = (int) Math.floor(patchMinX);
        int xEnd = (int) Math.ceil(patchMaxX);
        int yStart = (int) Math.floor(patchMinY);
        int yEnd = (int) Math.ceil(patchMaxY);

        for (int xi = xStart; xi < xEnd; xi++) {
            float cx0 = Math.max(xi, patchMinX);
            float cx1 = Math.min(xi + 1, patchMaxX);
            if (cx0 >= cx1) continue;
            for (int yi = yStart; yi < yEnd; yi++) {
                float cy0 = Math.max(yi, patchMinY);
                float cy1 = Math.min(yi + 1, patchMaxY);
                if (cy0 >= cy1) continue;
                if (!mask[(xi - owner.minX()) * ySpanZ + (yi - owner.minY())]) continue;

                float uMin = cx0 / TILE_BLOCKS + scroll;
                float uMax = cx1 / TILE_BLOCKS + scroll;
                float vMin = cy0 / TILE_BLOCKS - scroll;
                float vMax = cy1 / TILE_BLOCKS - scroll;
                buf.addVertex(m, cx0, cy0, planeZ).setUv(uMin, vMin).setColor(230, 20, 20, alpha);
                buf.addVertex(m, cx1, cy0, planeZ).setUv(uMax, vMin).setColor(230, 20, 20, alpha);
                buf.addVertex(m, cx1, cy1, planeZ).setUv(uMax, vMax).setColor(230, 20, 20, alpha);
                buf.addVertex(m, cx0, cy1, planeZ).setUv(uMin, vMax).setColor(230, 20, 20, alpha);
            }
        }
    }

    // ---------- Face masks ----------

    /**
     * Which cells of a box face lie on the outside of the union, worked out once instead of per
     * frame.
     *
     * <p>{@link #exteriorHidden} and the {@code coincidentDuplicate*} tests each walk every box
     * in the zone, and they used to run per cell per frame — so drawing a wall cost roughly
     * (cells x boxes x boxes) every frame, sixty times a second, for an answer that had not
     * changed.
     *
     * <p>Neither test looks at the camera. A cell is on the outside of the union or it is not,
     * and that only changes when the server sends a different set of shapes. The camera decides
     * only <em>which</em> cells fall inside the reveal patch, and that stays a per-frame
     * decision, so the patch still grows and fades exactly as before.
     *
     * <p>A cell clipped by the patch edge gets the same verdict as the whole cell it sits in:
     * every box bound is an integer and every cell is unit-aligned, so any point strictly inside
     * the cell is inside the same set of boxes. That is what makes one mask per cell enough.
     *
     * <p>Built lazily, one face at a time. A zone has far more surface than a player ever looks
     * at, and building all of it on arrival would trade the per-frame cost for a hitch every
     * time the borders sync.
     */
    private static final int FACES_PER_BOX = 6;

    /** The zone the masks below were built for, compared by identity. */
    private static List<BoundingBox> maskedZone = null;
    /** {@code [boxIndex * FACES_PER_BOX + face]}; a null slot has not been built yet. */
    private static boolean[][] faceMasks = null;

    private static boolean[] maskFor(List<BoundingBox> all, int ownerIdx, int face) {
        // LockBorderClientCache.set copies, so a new sync is a new list instance and every mask
        // built against the old shapes is dropped here.
        if (maskedZone != all) {
            maskedZone = all;
            faceMasks = new boolean[all.size() * FACES_PER_BOX][];
        }
        int slot = ownerIdx * FACES_PER_BOX + face;
        boolean[] mask = faceMasks[slot];
        if (mask == null) {
            mask = switch (face) {
                case 0, 1 -> buildMaskX(all, ownerIdx, face == 1);
                case 2, 3 -> buildMaskY(all, ownerIdx, face == 3);
                default -> buildMaskZ(all, ownerIdx, face == 5);
            };
            faceMasks[slot] = mask;
        }
        return mask;
    }

    private static boolean[] buildMaskX(List<BoundingBox> all, int ownerIdx, boolean maxSide) {
        BoundingBox owner = all.get(ownerIdx);
        float plane = maxSide ? owner.maxX() + 1.0f : owner.minX();
        double sampleX = plane + (maxSide ? 0.5 : -0.5);

        int ySpan = owner.maxY() - owner.minY() + 1;
        int zSpan = owner.maxZ() - owner.minZ() + 1;
        boolean[] mask = new boolean[ySpan * zSpan];

        for (int yi = owner.minY(); yi <= owner.maxY(); yi++) {
            for (int zi = owner.minZ(); zi <= owner.maxZ(); zi++) {
                if (exteriorHidden(all, owner, sampleX, yi + 0.5, zi + 0.5)) continue;
                if (coincidentDuplicateX(all, ownerIdx, plane, yi, yi + 1, zi, zi + 1)) continue;
                mask[(yi - owner.minY()) * zSpan + (zi - owner.minZ())] = true;
            }
        }
        return mask;
    }

    private static boolean[] buildMaskY(List<BoundingBox> all, int ownerIdx, boolean maxSide) {
        BoundingBox owner = all.get(ownerIdx);
        float plane = maxSide ? owner.maxY() + 1.0f : owner.minY();
        double sampleY = plane + (maxSide ? 0.5 : -0.5);

        int xSpan = owner.maxX() - owner.minX() + 1;
        int zSpan = owner.maxZ() - owner.minZ() + 1;
        boolean[] mask = new boolean[xSpan * zSpan];

        for (int xi = owner.minX(); xi <= owner.maxX(); xi++) {
            for (int zi = owner.minZ(); zi <= owner.maxZ(); zi++) {
                if (exteriorHidden(all, owner, xi + 0.5, sampleY, zi + 0.5)) continue;
                if (coincidentDuplicateY(all, ownerIdx, plane, xi, xi + 1, zi, zi + 1)) continue;
                mask[(xi - owner.minX()) * zSpan + (zi - owner.minZ())] = true;
            }
        }
        return mask;
    }

    private static boolean[] buildMaskZ(List<BoundingBox> all, int ownerIdx, boolean maxSide) {
        BoundingBox owner = all.get(ownerIdx);
        float plane = maxSide ? owner.maxZ() + 1.0f : owner.minZ();
        double sampleZ = plane + (maxSide ? 0.5 : -0.5);

        int xSpan = owner.maxX() - owner.minX() + 1;
        int ySpan = owner.maxY() - owner.minY() + 1;
        boolean[] mask = new boolean[xSpan * ySpan];

        for (int xi = owner.minX(); xi <= owner.maxX(); xi++) {
            for (int yi = owner.minY(); yi <= owner.maxY(); yi++) {
                if (exteriorHidden(all, owner, xi + 0.5, yi + 0.5, sampleZ)) continue;
                if (coincidentDuplicateZ(all, ownerIdx, plane, xi, xi + 1, yi, yi + 1)) continue;
                mask[(xi - owner.minX()) * ySpan + (yi - owner.minY())] = true;
            }
        }
        return mask;
    }

    /** Drops every mask. Called when the client leaves a world, so nothing outlives its zone. */
    public static void forgetMasks() {
        maskedZone = null;
        faceMasks = null;
    }

    /**
     * Dedup for coincident same-direction faces. When two boxes have their outward face on the
     * exact same plane (e.g. both L-bridge segments ending at the same X, or two pieces ending
     * at the same Y), the per-sample {@link #exteriorHidden} test passes for both — neither
     * box's exterior sample lies inside the other. Without dedup both render and the texture
     * doubles up. Rule: render only if {@code ownerIdx} is the lowest-indexed box claiming the
     * cell.
     */
    private static boolean coincidentDuplicateX(List<BoundingBox> all, int ownerIdx, float planeX,
                                                 float cellMinY, float cellMaxY, float cellMinZ, float cellMaxZ) {
        for (int i = 0; i < ownerIdx; i++) {
            BoundingBox b = all.get(i);
            if (b.maxX() + 1.0f != planeX && (float) b.minX() != planeX) continue;
            if (cellMaxY > b.minY() && cellMinY < b.maxY() + 1.0f
                    && cellMaxZ > b.minZ() && cellMinZ < b.maxZ() + 1.0f) {
                return true;
            }
        }
        return false;
    }

    private static boolean coincidentDuplicateY(List<BoundingBox> all, int ownerIdx, float planeY,
                                                 float cellMinX, float cellMaxX, float cellMinZ, float cellMaxZ) {
        for (int i = 0; i < ownerIdx; i++) {
            BoundingBox b = all.get(i);
            if (b.maxY() + 1.0f != planeY && (float) b.minY() != planeY) continue;
            if (cellMaxX > b.minX() && cellMinX < b.maxX() + 1.0f
                    && cellMaxZ > b.minZ() && cellMinZ < b.maxZ() + 1.0f) {
                return true;
            }
        }
        return false;
    }

    private static boolean coincidentDuplicateZ(List<BoundingBox> all, int ownerIdx, float planeZ,
                                                 float cellMinX, float cellMaxX, float cellMinY, float cellMaxY) {
        for (int i = 0; i < ownerIdx; i++) {
            BoundingBox b = all.get(i);
            if (b.maxZ() + 1.0f != planeZ && (float) b.minZ() != planeZ) continue;
            if (cellMaxX > b.minX() && cellMinX < b.maxX() + 1.0f
                    && cellMaxY > b.minY() && cellMinY < b.maxY() + 1.0f) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when a half-block sample on the OUTSIDE of the face lies strictly inside another
     * shape in the zone — meaning the face is interior to the union and shouldn't be drawn.
     * Coincident-face cells (sample sits exactly on another box's boundary) survive here and
     * are handled by the {@code coincidentDuplicate*} dedup instead.
     */
    private static boolean exteriorHidden(List<BoundingBox> all, BoundingBox owner,
                                           double x, double y, double z) {
        for (BoundingBox b : all) {
            if (b == owner) continue;
            if (x > b.minX() && x < b.maxX() + 1.0
                    && y > b.minY() && y < b.maxY() + 1.0
                    && z > b.minZ() && z < b.maxZ() + 1.0) {
                return true;
            }
        }
        return false;
    }

    private static int alphaForDist(double dist, double threshold) {
        double t = 1.0 - dist / threshold;
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        return Math.max(0, Math.min(255, (int) (CENTER_ALPHA * t * 255.0)));
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
