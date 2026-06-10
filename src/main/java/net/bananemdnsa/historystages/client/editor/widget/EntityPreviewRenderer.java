package net.bananemdnsa.historystages.client.editor.widget;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableEntityList;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared cache + renderer for spinning {@link LivingEntity} previews used by the editor
 * widgets. Extracted from {@code SearchableEntityList} so non-widget screens (e.g. the
 * Auto-Trigger Editor) can show the same model previews.
 */
public final class EntityPreviewRenderer {

    private EntityPreviewRenderer() {}

    private static final Map<String, LivingEntity> CACHE = new HashMap<>();

    /**
     * Returns a cached client-side {@link LivingEntity} instance for the given entity id,
     * or {@code null} if the id is invalid, the type is not living, or no level is loaded.
     */
    public static LivingEntity getOrCreate(String entityId) {
        if (CACHE.containsKey(entityId)) return CACHE.get(entityId);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        try {
            ResourceLocation rl = ResourceLocation.tryParse(entityId);
            if (rl == null) return null;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
            if (type == null) return null;
            Entity entity = type.create(mc.level);
            if (entity instanceof LivingEntity living) {
                CACHE.put(entityId, living);
                return living;
            }
            if (entity != null) entity.discard();
        } catch (Exception ignored) {
        }
        CACHE.put(entityId, null);
        return null;
    }

    /** Renders {@code entity} spinning around the Y axis at screen position (x, y). */
    public static void renderSpinning(GuiGraphics g, int x, int y, int scale, float angleDegrees,
                                      LivingEntity entity) {
        float origBodyRot = entity.yBodyRot;
        float origYRot = entity.getYRot();
        float origXRot = entity.getXRot();
        float origHeadRotO = entity.yHeadRotO;
        float origHeadRot = entity.yHeadRot;

        entity.yBodyRot = 180.0F;
        entity.setYRot(180.0F);
        entity.setXRot(0.0F);
        entity.yHeadRot = 180.0F;
        entity.yHeadRotO = 180.0F;

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try {
            modelViewStack.translate(0.0F, 0.0F, 1500.0F);
            RenderSystem.applyModelViewMatrix();

            PoseStack poseStack = new PoseStack();
            poseStack.translate((double) x, (double) y, -950.0D);
            poseStack.scale((float) scale, (float) scale, (float) scale);

            Quaternionf flipAndSpin = new Quaternionf().rotateZ((float) Math.PI);
            flipAndSpin.mul(new Quaternionf().rotateY(angleDegrees * ((float) Math.PI / 180.0F)));
            poseStack.mulPose(flipAndSpin);

            Lighting.setupForEntityInInventory();
            RenderSystem.disableDepthTest();

            EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            dispatcher.overrideCameraOrientation(new Quaternionf());
            dispatcher.setRenderShadow(false);

            MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F,
                    poseStack, bufferSource, 15728880));
            bufferSource.endBatch();
            dispatcher.setRenderShadow(true);
            RenderSystem.enableDepthTest();
        } finally {
            modelViewStack.popMatrix();
            RenderSystem.applyModelViewMatrix();
            Lighting.setupFor3DItems();

            entity.yBodyRot = origBodyRot;
            entity.setYRot(origYRot);
            entity.setXRot(origXRot);
            entity.yHeadRotO = origHeadRotO;
            entity.yHeadRot = origHeadRot;
        }
    }
}
