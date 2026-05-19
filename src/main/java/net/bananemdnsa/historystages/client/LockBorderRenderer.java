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
 * Draws a force-field "patch" on the faces of locked-structure bounding boxes
 * cached by {@link LockBorderClientCache}. Instead of covering the whole face,
 * only a square region around the player's projection onto the face is rendered;
 * the patch radius grows as the player approaches the wall.
 *
 * <p>UV mapping is world-anchored (texture stays put on the wall as the player moves)
 * with a slow time-based scroll for the classic force-field animation.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class LockBorderRenderer {

    private static final ResourceLocation FORCEFIELD =
            ResourceLocation.parse("minecraft:textures/misc/forcefield.png");

    /** How many blocks one texture tile covers. Smaller = more tiling on the wall. */
    private static final float TILE_BLOCKS = 4.0f;

    /** Alpha of the patch center; falls off linearly with distance from camera. */
    private static final float CENTER_ALPHA = 0.7f;

    private LockBorderRenderer() {}

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!Config.CLIENT.structureBorderEnabled.get()) return;

        List<BoundingBox> boxes = LockBorderClientCache.get();
        if (boxes.isEmpty()) return;

        double threshold = Config.CLIENT.structureBorderDistance.get();

        Vec3 cam = event.getCamera().getPosition();
        float scroll = (float) ((Util.getMillis() % 3000L) / 3000.0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, FORCEFIELD);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f matrix = pose.last().pose();

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (BoundingBox bb : boxes) {
            renderBox(buffer, matrix, bb, cam, threshold, scroll);
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

    private static void renderBox(BufferBuilder buf, Matrix4f m, BoundingBox bb,
                                   Vec3 cam, double threshold, float scroll) {
        float x0 = bb.minX();
        float y0 = bb.minY();
        float z0 = bb.minZ();
        float x1 = bb.maxX() + 1.0f;
        float y1 = bb.maxY() + 1.0f;
        float z1 = bb.maxZ() + 1.0f;

        // Cheap broad-phase: closest point of the box to the camera.
        double cx = clamp(cam.x, x0, x1);
        double cy = clamp(cam.y, y0, y1);
        double cz = clamp(cam.z, z0, z1);
        double dx = cam.x - cx, dy = cam.y - cy, dz = cam.z - cz;
        double boxDistSq = dx * dx + dy * dy + dz * dz;
        if (boxDistSq > threshold * threshold) return;

        // Faces with normals along X (west / east) — vary in Y and Z.
        renderFaceX(buf, m, x0, y0, y1, z0, z1, cam, threshold, scroll);
        renderFaceX(buf, m, x1, y0, y1, z0, z1, cam, threshold, scroll);
        // Faces with normals along Y (bottom / top) — vary in X and Z.
        renderFaceY(buf, m, y0, x0, x1, z0, z1, cam, threshold, scroll);
        renderFaceY(buf, m, y1, x0, x1, z0, z1, cam, threshold, scroll);
        // Faces with normals along Z (north / south) — vary in X and Y.
        renderFaceZ(buf, m, z0, x0, x1, y0, y1, cam, threshold, scroll);
        renderFaceZ(buf, m, z1, x0, x1, y0, y1, cam, threshold, scroll);
    }

    private static void renderFaceX(BufferBuilder buf, Matrix4f m,
                                     float planeX, float minY, float maxY, float minZ, float maxZ,
                                     Vec3 cam, double threshold, float scroll) {
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
        float uMin = patchMinZ / TILE_BLOCKS + scroll;
        float uMax = patchMaxZ / TILE_BLOCKS + scroll;
        float vMin = patchMinY / TILE_BLOCKS - scroll;
        float vMax = patchMaxY / TILE_BLOCKS - scroll;

        buf.addVertex(m, planeX, patchMinY, patchMinZ).setUv(uMin, vMin).setColor(255, 255, 255, alpha);
        buf.addVertex(m, planeX, patchMinY, patchMaxZ).setUv(uMax, vMin).setColor(255, 255, 255, alpha);
        buf.addVertex(m, planeX, patchMaxY, patchMaxZ).setUv(uMax, vMax).setColor(255, 255, 255, alpha);
        buf.addVertex(m, planeX, patchMaxY, patchMinZ).setUv(uMin, vMax).setColor(255, 255, 255, alpha);
    }

    private static void renderFaceY(BufferBuilder buf, Matrix4f m,
                                     float planeY, float minX, float maxX, float minZ, float maxZ,
                                     Vec3 cam, double threshold, float scroll) {
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
        float uMin = patchMinX / TILE_BLOCKS + scroll;
        float uMax = patchMaxX / TILE_BLOCKS + scroll;
        float vMin = patchMinZ / TILE_BLOCKS - scroll;
        float vMax = patchMaxZ / TILE_BLOCKS - scroll;

        buf.addVertex(m, patchMinX, planeY, patchMinZ).setUv(uMin, vMin).setColor(255, 255, 255, alpha);
        buf.addVertex(m, patchMaxX, planeY, patchMinZ).setUv(uMax, vMin).setColor(255, 255, 255, alpha);
        buf.addVertex(m, patchMaxX, planeY, patchMaxZ).setUv(uMax, vMax).setColor(255, 255, 255, alpha);
        buf.addVertex(m, patchMinX, planeY, patchMaxZ).setUv(uMin, vMax).setColor(255, 255, 255, alpha);
    }

    private static void renderFaceZ(BufferBuilder buf, Matrix4f m,
                                     float planeZ, float minX, float maxX, float minY, float maxY,
                                     Vec3 cam, double threshold, float scroll) {
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
        float uMin = patchMinX / TILE_BLOCKS + scroll;
        float uMax = patchMaxX / TILE_BLOCKS + scroll;
        float vMin = patchMinY / TILE_BLOCKS - scroll;
        float vMax = patchMaxY / TILE_BLOCKS - scroll;

        buf.addVertex(m, patchMinX, patchMinY, planeZ).setUv(uMin, vMin).setColor(255, 255, 255, alpha);
        buf.addVertex(m, patchMaxX, patchMinY, planeZ).setUv(uMax, vMin).setColor(255, 255, 255, alpha);
        buf.addVertex(m, patchMaxX, patchMaxY, planeZ).setUv(uMax, vMax).setColor(255, 255, 255, alpha);
        buf.addVertex(m, patchMinX, patchMaxY, planeZ).setUv(uMin, vMax).setColor(255, 255, 255, alpha);
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
