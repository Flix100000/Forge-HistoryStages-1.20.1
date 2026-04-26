package net.bananemdnsa.historystages.client.editor.widget;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Popup that shows all entities from a specific mod with checkboxes for spawnlock and attacklock.
 * Appears after adding a mod in the editor, allowing users to configure entity locks.
 * Only one checkbox per entity can be active at a time (mutually exclusive).
 */
public class ModEntitySelectionPopup {
    private static final int ROW_HEIGHT = 24;
    private static final int VISIBLE_ROWS = 6;
    private static final int PADDING = 8;
    private static final int PANEL_WIDTH = 280;
    private static final int CHECKBOX_SIZE = 12;
    private static final int HEADER_HEIGHT = 38;
    private static final int FOOTER_HEIGHT = 30;

    // Marquee settings
    private static final long MARQUEE_DELAY_MS = 800;
    private static final float MARQUEE_SPEED = 25.0f;

    private final List<EntityRow> entities = new ArrayList<>();
    private final Map<String, LivingEntity> entityCache = new HashMap<>();
    private final BiConsumer<List<String>, List<String>> onConfirm; // (spawnlock, attacklock)

    private int panelX, panelY, panelW, panelH;
    private boolean visible = false;
    private int scrollRow = 0;
    private int maxScrollRow = 0;
    private boolean draggingScrollbar = false;
    private String modDisplayName = "";

    // Button hover animation
    private float confirmHoverProgress = 0;
    private float skipHoverProgress = 0;

    // Checkbox hover animation: key = "index_s" or "index_a"
    private final Map<String, Float> checkboxHoverProgress = new HashMap<>();

    // Select-all state
    private boolean allSpawn = false;
    private boolean allAttack = false;

    // Tooltip hover tracking
    private String hoveredTooltipKey = null;
    private long tooltipHoverStart = 0;
    private static final long TOOLTIP_DELAY_MS = 400;

    // Marquee hover tracking
    private int hoveredRowIndex = -1;
    private long rowHoverStartTime = 0;

    public ModEntitySelectionPopup(BiConsumer<List<String>, List<String>> onConfirm) {
        this.onConfirm = onConfirm;
    }

    public boolean showForMod(String modId, String modDisplayName, int centerX, int centerY) {
        this.modDisplayName = modDisplayName;
        entities.clear();
        entityCache.clear();
        confirmHoverProgress = 0;
        skipHoverProgress = 0;
        hoveredRowIndex = -1;
        allSpawn = false;
        allAttack = false;

        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType);
            if (key != null && key.getNamespace().equals(modId) && isLivingEntityType(entityType)) {
                String displayName = entityType.getDescription().getString();
                entities.add(new EntityRow(key.toString(), displayName, false, false));
            }
        }

        if (entities.isEmpty()) return false;

        entities.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));

        int visibleRows = Math.min(VISIBLE_ROWS, entities.size());
        panelW = PANEL_WIDTH;
        panelH = HEADER_HEIGHT + PADDING + visibleRows * ROW_HEIGHT + PADDING + FOOTER_HEIGHT;
        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;

        if (panelX < 4) panelX = 4;
        if (panelY < 4) panelY = 4;

        this.visible = true;
        this.scrollRow = 0;
        updateMaxScroll();
        return true;
    }

    private boolean isLivingEntityType(EntityType<?> type) {
        try {
            if (Minecraft.getInstance().level != null) {
                Entity entity = type.create(Minecraft.getInstance().level);
                boolean isLiving = entity instanceof LivingEntity;
                if (entity != null) entity.discard();
                return isLiving;
            }
            return type.getCategory() != net.minecraft.world.entity.MobCategory.MISC;
        } catch (Exception e) {
            return false;
        }
    }

    private LivingEntity getOrCreateEntity(String entityId) {
        if (entityCache.containsKey(entityId)) return entityCache.get(entityId);
        if (Minecraft.getInstance().level == null) return null;

        try {
            ResourceLocation rl = ResourceLocation.tryParse(entityId);
            if (rl == null) return null;
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(rl);
            if (type == null) return null;
            Entity entity = type.create(Minecraft.getInstance().level);
            if (entity instanceof LivingEntity living) {
                entityCache.put(entityId, living);
                return living;
            }
            if (entity != null) entity.discard();
        } catch (Exception ignored) {}
        entityCache.put(entityId, null);
        return null;
    }

    public void hide() {
        this.visible = false;
        entityCache.clear();
    }

    public boolean isVisible() {
        return visible;
    }

    private void updateMaxScroll() {
        int visibleRows = Math.min(VISIBLE_ROWS, entities.size());
        maxScrollRow = Math.max(0, entities.size() - visibleRows);
    }

    // Checkbox column positions relative to panel
    private int getCbSpawnX() {
        return panelX + panelW - PADDING - CHECKBOX_SIZE * 2 - 46;
    }

    private int getCbAttackX() {
        return getCbSpawnX() + CHECKBOX_SIZE + 20;
    }

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        int visibleRows = Math.min(VISIBLE_ROWS, entities.size());

        // Dimmed background
        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), 0x88000000);

        // Panel background
        guiGraphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        // Header with mod name
        String title = modDisplayName + " - Entities";
        if (font.width(title) > panelW - PADDING * 2 - 90) {
            title = font.plainSubstrByWidth(title, panelW - PADDING * 2 - 96) + "...";
        }
        guiGraphics.drawString(font, title, panelX + PADDING, panelY + 7, 0xFFFFFF, false);

        // Column headers with hover tooltips
        int cbSpawnX = getCbSpawnX();
        int cbAttackX = getCbAttackX();
        String spawnLabel = "Spawn";
        String attackLabel = "Attack";
        int spawnLabelW = font.width(spawnLabel);
        int attackLabelW = font.width(attackLabel);
        int spawnLabelX = cbSpawnX + (CHECKBOX_SIZE - spawnLabelW) / 2;
        int attackLabelX = cbAttackX + (CHECKBOX_SIZE - attackLabelW) / 2;

        boolean spawnHeaderHovered = mouseX >= spawnLabelX - 2 && mouseX < spawnLabelX + spawnLabelW + 2
                && mouseY >= panelY + 4 && mouseY < panelY + 16;
        boolean attackHeaderHovered = mouseX >= attackLabelX - 2 && mouseX < attackLabelX + attackLabelW + 2
                && mouseY >= panelY + 4 && mouseY < panelY + 16;

        guiGraphics.drawString(font, spawnLabel, spawnLabelX, panelY + 7,
                spawnHeaderHovered ? 0xDDDDDD : 0x999999, false);
        guiGraphics.drawString(font, attackLabel, attackLabelX, panelY + 7,
                attackHeaderHovered ? 0xDDDDDD : 0x999999, false);

        // Select-all checkboxes below labels
        int selectAllY = panelY + 20;
        renderCheckbox(guiGraphics, cbSpawnX, selectAllY, allSpawn, mouseX, mouseY, "all_s");
        renderCheckbox(guiGraphics, cbAttackX, selectAllY, allAttack, mouseX, mouseY, "all_a");

        String currentTooltipKey = null;
        String currentTooltipText = null;
        if (spawnHeaderHovered) {
            currentTooltipKey = "spawn_header";
            currentTooltipText = "Prevents this entity from spawning";
        } else if (attackHeaderHovered) {
            currentTooltipKey = "attack_header";
            currentTooltipText = "Prevents attacking this entity";
        }

        // List area
        int listY = panelY + HEADER_HEIGHT + PADDING;
        int listX = panelX + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        // Track current hover for marquee
        int currentHoveredRow = -1;

        for (int i = 0; i < visibleRows; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;

            if (index >= entities.size()) break;

            EntityRow row = entities.get(index);
            boolean rowHovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (rowHovered) currentHoveredRow = index;

            guiGraphics.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT,
                    rowHovered ? 0xFF353535 : 0xFF252525);

            // Entity preview
            LivingEntity living = getOrCreateEntity(row.id);
            if (living != null) {
                try {
                    float angle = (System.currentTimeMillis() % 3600) / 10.0f;
                    guiGraphics.enableScissor(listX, rowY, listX + 22, rowY + ROW_HEIGHT);
                    int entityScale = (int) Math.max(3, 9.0f / Math.max(living.getBbWidth(), living.getBbHeight()));
                    renderSpinningEntity(guiGraphics, listX + 11, rowY + ROW_HEIGHT - 2, entityScale, angle, living);
                    guiGraphics.disableScissor();
                } catch (Exception ignored) {}
            }

            // Entity name with marquee scroll
            int textStartX = listX + 24;
            int textAvailW = cbSpawnX - textStartX - 4;
            String text = row.displayName + " \u00A77(" + row.id + ")";
            int textW = font.width(text);
            int textColor = rowHovered ? 0xFFFFFF : 0xBBBBBB;

            if (textW > textAvailW && rowHovered && index == hoveredRowIndex) {
                long elapsed = System.currentTimeMillis() - rowHoverStartTime;
                if (elapsed > MARQUEE_DELAY_MS) {
                    float scrollProg = (elapsed - MARQUEE_DELAY_MS) / 1000.0f * MARQUEE_SPEED;
                    int maxMarquee = textW - textAvailW + 10;
                    float cycle = (float) maxMarquee * 2;
                    float pos = scrollProg % cycle;
                    int scrollOff = pos <= maxMarquee ? (int) pos : (int) (cycle - pos);
                    guiGraphics.enableScissor(textStartX, rowY, textStartX + textAvailW, rowY + ROW_HEIGHT);
                    guiGraphics.drawString(font, text, textStartX - scrollOff, rowY + 7, textColor, false);
                    guiGraphics.disableScissor();
                } else {
                    String truncated = font.plainSubstrByWidth(text, textAvailW - 8) + "...";
                    guiGraphics.drawString(font, truncated, textStartX, rowY + 7, textColor, false);
                }
            } else if (textW > textAvailW) {
                String truncated = font.plainSubstrByWidth(text, textAvailW - 8) + "...";
                guiGraphics.drawString(font, truncated, textStartX, rowY + 7, textColor, false);
            } else {
                guiGraphics.drawString(font, text, textStartX, rowY + 7, textColor, false);
            }

            // Spawnlock checkbox
            int cbY = rowY + (ROW_HEIGHT - CHECKBOX_SIZE) / 2;
            renderCheckbox(guiGraphics, cbSpawnX, cbY, row.spawnlock, mouseX, mouseY, index + "_s");

            // Attacklock checkbox
            renderCheckbox(guiGraphics, cbAttackX, cbY, row.attacklock, mouseX, mouseY, index + "_a");
        }

        // Update marquee hover tracking
        if (currentHoveredRow != hoveredRowIndex) {
            hoveredRowIndex = currentHoveredRow;
            rowHoverStartTime = System.currentTimeMillis();
        }

        // Scrollbar
        if (maxScrollRow > 0) {
            int scrollBarX = listX + listW + 2;
            int scrollBarTop = listY;
            int scrollBarBottom = listY + visibleRows * ROW_HEIGHT;
            int scrollBarHeight = scrollBarBottom - scrollBarTop;

            guiGraphics.fill(scrollBarX, scrollBarTop, scrollBarX + 4, scrollBarBottom, 0xFF252525);

            int thumbHeight = Math.max(10, (int) ((float) visibleRows / (maxScrollRow + visibleRows) * scrollBarHeight));
            int thumbY = scrollBarTop + (int) ((float) scrollRow / maxScrollRow * (scrollBarHeight - thumbHeight));
            guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, 0xFF888888);
        }

        // Footer with styled buttons (matching StyledButton look)
        int footerY = panelY + panelH - FOOTER_HEIGHT;
        int btnW = 80;
        int btnH = 20;
        int confirmX = panelX + panelW / 2 - btnW - 5;
        int skipX = panelX + panelW / 2 + 5;

        boolean confirmHovered = mouseX >= confirmX && mouseX < confirmX + btnW
                && mouseY >= footerY + 5 && mouseY < footerY + 5 + btnH;
        boolean skipHovered = mouseX >= skipX && mouseX < skipX + btnW
                && mouseY >= footerY + 5 && mouseY < footerY + 5 + btnH;

        // Smooth hover transitions
        confirmHoverProgress = confirmHovered
                ? Math.min(1.0f, confirmHoverProgress + 0.1f)
                : Math.max(0.0f, confirmHoverProgress - 0.08f);
        skipHoverProgress = skipHovered
                ? Math.min(1.0f, skipHoverProgress + 0.1f)
                : Math.max(0.0f, skipHoverProgress - 0.08f);

        renderStyledButton(guiGraphics, font, "Confirm", confirmX, footerY + 5, btnW, btnH, confirmHoverProgress);
        renderStyledButton(guiGraphics, font, "Skip", skipX, footerY + 5, btnW, btnH, skipHoverProgress);

        // Tooltip rendering with delay
        if (currentTooltipKey != null && currentTooltipText != null) {
            if (!currentTooltipKey.equals(hoveredTooltipKey)) {
                hoveredTooltipKey = currentTooltipKey;
                tooltipHoverStart = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - tooltipHoverStart >= TOOLTIP_DELAY_MS) {
                renderTooltip(guiGraphics, font, currentTooltipText, mouseX, mouseY);
            }
        } else {
            hoveredTooltipKey = null;
        }
    }

    private void renderTooltip(GuiGraphics guiGraphics, Font font, String text, int mouseX, int mouseY) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400);

        int tooltipW = font.width(text) + 8;
        int tooltipH = 16;
        int screenW = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 4;
        if (tooltipX + tooltipW + 2 > screenW - 4) tooltipX = mouseX - tooltipW - 4;
        if (tooltipY + tooltipH + 2 > screenH - 4) tooltipY = screenH - tooltipH - 6;
        if (tooltipX < 4) tooltipX = 4;
        if (tooltipY < 4) tooltipY = 4;

        guiGraphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipW + 2, tooltipY + tooltipH + 2, 0xFF3D3D3D);
        guiGraphics.fill(tooltipX, tooltipY, tooltipX + tooltipW, tooltipY + tooltipH, 0xFF0D0D0D);
        guiGraphics.drawString(font, text, tooltipX + 4, tooltipY + 4, 0xCCCCCC, false);

        guiGraphics.pose().popPose();
    }

    private void renderStyledButton(GuiGraphics guiGraphics, Font font, String text, int x, int y, int w, int h, float hoverProgress) {
        // Background: white-tint to gold-tint
        int bgAlpha = (int) (0x30 + hoverProgress * 0x20);
        int bgR = 0xFF;
        int bgG = (int) (0xFF - hoverProgress * 0x33);
        int bgB = (int) (0xFF - hoverProgress * 0xFF);
        guiGraphics.fill(x, y, x + w, y + h, (bgAlpha << 24) | (bgR << 16) | (bgG << 8) | bgB);

        // Bottom accent line (gold)
        int accentAlpha = (int) (0x60 + hoverProgress * 0x9F);
        guiGraphics.fill(x, y + h - 2, x + w, y + h, (accentAlpha << 24) | 0xFFCC00);

        // Subtle borders
        guiGraphics.fill(x, y, x + w, y + 1, 0x20FFFFFF);
        guiGraphics.fill(x, y, x + 1, y + h, 0x15FFFFFF);
        guiGraphics.fill(x + w - 1, y, x + w, y + h, 0x15FFFFFF);

        // Text
        int textGray = (int) (0xCC + hoverProgress * 0x33);
        int textColor = (0xFF << 24) | (textGray << 16) | (textGray << 8) | textGray;
        guiGraphics.drawString(font, text, x + (w - font.width(text)) / 2, y + (h - 8) / 2, textColor, false);
    }

    private void renderCheckbox(GuiGraphics guiGraphics, int x, int y, boolean checked, int mouseX, int mouseY, String hoverKey) {
        boolean hovered = mouseX >= x && mouseX < x + CHECKBOX_SIZE && mouseY >= y && mouseY < y + CHECKBOX_SIZE;

        // Smooth hover transition
        float hp = checkboxHoverProgress.getOrDefault(hoverKey, 0f);
        hp = hovered ? Math.min(1.0f, hp + 0.1f) : Math.max(0.0f, hp - 0.08f);
        checkboxHoverProgress.put(hoverKey, hp);

        float progress = checked ? 1.0f : hp;

        // Background: white-tint to gold-tint (same as StyledButton)
        int bgAlpha = (int) (0x30 + progress * 0x20);
        int bgR = 0xFF;
        int bgG = (int) (0xFF - progress * 0x33);
        int bgB = (int) (0xFF - progress * 0xFF);
        guiGraphics.fill(x, y, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, (bgAlpha << 24) | (bgR << 16) | (bgG << 8) | bgB);

        // Bottom accent line (gold)
        int accentAlpha = (int) (0x60 + progress * 0x9F);
        guiGraphics.fill(x, y + CHECKBOX_SIZE - 1, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, (accentAlpha << 24) | 0xFFCC00);

        // Subtle borders
        guiGraphics.fill(x, y, x + CHECKBOX_SIZE, y + 1, 0x20FFFFFF);
        guiGraphics.fill(x, y, x + 1, y + CHECKBOX_SIZE, 0x15FFFFFF);
        guiGraphics.fill(x + CHECKBOX_SIZE - 1, y, x + CHECKBOX_SIZE, y + CHECKBOX_SIZE, 0x15FFFFFF);

        // Checkmark when checked (✓ shape)
        if (checked) {
            int color = 0xFFDDDDDD;
            // Short leg going down-right
            guiGraphics.fill(x + 2, y + 5, x + 4, y + 7, color);
            guiGraphics.fill(x + 3, y + 6, x + 5, y + 8, color);
            guiGraphics.fill(x + 4, y + 7, x + 6, y + 9, color);
            // Long leg going up-right
            guiGraphics.fill(x + 5, y + 6, x + 7, y + 8, color);
            guiGraphics.fill(x + 6, y + 5, x + 8, y + 7, color);
            guiGraphics.fill(x + 7, y + 4, x + 9, y + 6, color);
            guiGraphics.fill(x + 8, y + 3, x + 10, y + 5, color);
        }
    }

    private static void renderSpinningEntity(GuiGraphics guiGraphics, int x, int y, int scale, float angleDegrees, LivingEntity entity) {
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

        org.joml.Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
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
            RenderSystem.runAsFancy(() -> {
                dispatcher.render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F, poseStack, bufferSource, 15728880);
            });
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

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!visible) return false;

        // Click outside panel closes it (skip)
        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        int visibleRows = Math.min(VISIBLE_ROWS, entities.size());

        // Footer buttons
        int footerY = panelY + panelH - FOOTER_HEIGHT;
        int btnW = 80;
        int btnH = 20;
        int confirmX = panelX + panelW / 2 - btnW - 5;
        int skipX = panelX + panelW / 2 + 5;

        if (mouseY >= footerY + 5 && mouseY < footerY + 5 + btnH) {
            if (mouseX >= confirmX && mouseX < confirmX + btnW) {
                List<String> spawnlockIds = new ArrayList<>();
                List<String> attacklockIds = new ArrayList<>();
                for (EntityRow row : entities) {
                    if (row.spawnlock) spawnlockIds.add(row.id);
                    if (row.attacklock) attacklockIds.add(row.id);
                }
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                onConfirm.accept(spawnlockIds, attacklockIds);
                hide();
                return true;
            }
            if (mouseX >= skipX && mouseX < skipX + btnW) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                hide();
                return true;
            }
        }

        // Scrollbar click
        if (maxScrollRow > 0) {
            int listY = panelY + HEADER_HEIGHT + PADDING;
            int listW = panelW - PADDING * 2 - 8;
            int scrollBarX = panelX + PADDING + listW + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= listY && mouseY < listY + visibleRows * ROW_HEIGHT) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, listY, visibleRows);
                return true;
            }
        }

        // Select-all checkbox clicks
        int cbSpawnX = getCbSpawnX();
        int cbAttackX = getCbAttackX();
        int selectAllY = panelY + 20;
        if (mouseX >= cbSpawnX && mouseX < cbSpawnX + CHECKBOX_SIZE
                && mouseY >= selectAllY && mouseY < selectAllY + CHECKBOX_SIZE) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            allSpawn = !allSpawn;
            if (allSpawn) {
                // ON: set all unchecked spawn to checked, clear their attacklock
                allAttack = false;
                for (int idx = 0; idx < entities.size(); idx++) {
                    EntityRow r = entities.get(idx);
                    entities.set(idx, new EntityRow(r.id, r.displayName, true, false));
                }
            } else {
                // OFF: clear all spawn
                for (int idx = 0; idx < entities.size(); idx++) {
                    EntityRow r = entities.get(idx);
                    entities.set(idx, new EntityRow(r.id, r.displayName, false, r.attacklock));
                }
            }
            return true;
        }
        if (mouseX >= cbAttackX && mouseX < cbAttackX + CHECKBOX_SIZE
                && mouseY >= selectAllY && mouseY < selectAllY + CHECKBOX_SIZE) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            allAttack = !allAttack;
            if (allAttack) {
                // ON: set all unchecked attack to checked, clear their spawnlock
                allSpawn = false;
                for (int idx = 0; idx < entities.size(); idx++) {
                    EntityRow r = entities.get(idx);
                    entities.set(idx, new EntityRow(r.id, r.displayName, false, true));
                }
            } else {
                // OFF: clear all attack
                for (int idx = 0; idx < entities.size(); idx++) {
                    EntityRow r = entities.get(idx);
                    entities.set(idx, new EntityRow(r.id, r.displayName, r.spawnlock, false));
                }
            }
            return true;
        }

        // Per-row checkbox clicks (mutually exclusive per row, deactivates select-all)
        int listY = panelY + HEADER_HEIGHT + PADDING;

        for (int i = 0; i < visibleRows; i++) {
            int index = scrollRow + i;
            if (index >= entities.size()) break;

            int rowY = listY + i * ROW_HEIGHT;
            int cbY = rowY + (ROW_HEIGHT - CHECKBOX_SIZE) / 2;

            // Spawnlock checkbox
            if (mouseX >= cbSpawnX && mouseX < cbSpawnX + CHECKBOX_SIZE
                    && mouseY >= cbY && mouseY < cbY + CHECKBOX_SIZE) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                EntityRow row = entities.get(index);
                boolean newSpawn = !row.spawnlock;
                entities.set(index, new EntityRow(row.id, row.displayName, newSpawn, newSpawn ? false : row.attacklock));
                allSpawn = false;
                return true;
            }

            // Attacklock checkbox
            if (mouseX >= cbAttackX && mouseX < cbAttackX + CHECKBOX_SIZE
                    && mouseY >= cbY && mouseY < cbY + CHECKBOX_SIZE) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                EntityRow row = entities.get(index);
                boolean newAttack = !row.attacklock;
                entities.set(index, new EntityRow(row.id, row.displayName, newAttack ? false : row.spawnlock, newAttack));
                allAttack = false;
                return true;
            }
        }

        return true; // consume clicks inside panel
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar) return false;
        int listY = panelY + HEADER_HEIGHT + PADDING;
        int visibleRows = Math.min(VISIBLE_ROWS, entities.size());
        updateScrollFromMouse(mouseY, listY, visibleRows);
        return true;
    }

    public boolean mouseReleased() {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    private void updateScrollFromMouse(double mouseY, int listY, int visibleRows) {
        int listH = visibleRows * ROW_HEIGHT;
        int totalRows = maxScrollRow + visibleRows;
        int thumbHeight = Math.max(10, (int) ((float) visibleRows / totalRows * listH));
        float usableH = listH - thumbHeight;
        if (usableH > 0) {
            float ratio = (float) (mouseY - listY - thumbHeight / 2.0) / usableH;
            ratio = Math.max(0, Math.min(1, ratio));
            scrollRow = Math.round(ratio * maxScrollRow);
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow));
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible) return false;
        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow - (int) scrollY));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (!visible) return false;
        if (keyCode == 256) { // ESC
            hide();
            return true;
        }
        return false;
    }

    private record EntityRow(String id, String displayName, boolean spawnlock, boolean attacklock) {}
}
