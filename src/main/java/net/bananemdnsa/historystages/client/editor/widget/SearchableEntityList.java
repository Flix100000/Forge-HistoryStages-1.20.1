package net.bananemdnsa.historystages.client.editor.widget;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Searchable list of all registered LivingEntity types with 3D preview.
 * Rendered as an overlay panel within the parent screen.
 */
public class SearchableEntityList {
    private static final int ROW_HEIGHT = 20;
    private static final int VISIBLE_ROWS = 8;
    private static final int SEARCH_HEIGHT = 20;
    private static final int PADDING = 6;
    private static final int PANEL_WIDTH = 260;

    private final List<EntityEntry> allEntities = new ArrayList<>();
    private final List<EntityEntry> filteredEntities = new ArrayList<>();
    private final Consumer<String> onSelect;

    // Entity cache for rendering
    private final Map<String, LivingEntity> entityCache = new HashMap<>();

    private int panelX, panelY, panelW, panelH;
    private boolean visible = false;
    private int scrollRow = 0;
    private int maxScrollRow = 0;
    private String filter = "";
    private boolean searchFocused = true;
    private boolean draggingScrollbar = false;
    private boolean allSelected = false;


    public SearchableEntityList(Consumer<String> onSelect) {
        this.onSelect = onSelect;

        // Only include LivingEntity types
        for (EntityType<?> entityType : ForgeRegistries.ENTITY_TYPES) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(entityType);
            if (key != null && isLivingEntityType(entityType)) {
                String displayName = entityType.getDescription().getString();
                allEntities.add(new EntityEntry(key.toString(), displayName, displayName.toLowerCase()));
            }
        }
        allEntities.sort((a, b) -> a.displayName.compareToIgnoreCase(b.displayName));
        filteredEntities.addAll(allEntities);
    }

    @SuppressWarnings("unchecked")
    private boolean isLivingEntityType(EntityType<?> type) {
        try {
            if (Minecraft.getInstance().level != null) {
                Entity entity = type.create(Minecraft.getInstance().level);
                boolean isLiving = entity instanceof LivingEntity;
                if (entity != null) entity.discard();
                return isLiving;
            }
            // Fallback: check by category (most mobs are living)
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
            EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(rl);
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

    public void show(int centerX, int centerY, int parentWidth) {
        panelW = PANEL_WIDTH;
        panelH = SEARCH_HEIGHT + PADDING * 2 + VISIBLE_ROWS * ROW_HEIGHT + PADDING + 4;
        panelX = centerX - panelW / 2;
        panelY = centerY - panelH / 2;

        if (panelX < 4) panelX = 4;
        if (panelY < 4) panelY = 4;

        this.visible = true;
        this.scrollRow = 0;
        this.filter = "";
        this.searchFocused = true;
        filteredEntities.clear();
        filteredEntities.addAll(allEntities);
        updateMaxScroll();
    }

    public void hide() {
        this.visible = false;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setFilter(String filter) {
        this.filter = filter.toLowerCase();
        this.scrollRow = 0;
        filteredEntities.clear();
        if (this.filter.isEmpty()) {
            filteredEntities.addAll(allEntities);
        } else {
            for (EntityEntry entry : allEntities) {
                if (entry.id.contains(this.filter) || entry.searchName.contains(this.filter)) {
                    filteredEntities.add(entry);
                }
            }
        }
        updateMaxScroll();
    }

    private void updateMaxScroll() {
        maxScrollRow = Math.max(0, filteredEntities.size() - VISIBLE_ROWS);
    }

    public void render(GuiGraphics guiGraphics, Font font, int mouseX, int mouseY) {
        if (!visible) return;

        // Panel background
        guiGraphics.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF3D3D3D);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFF1A1A1A);

        // Search bar
        int searchX = panelX + PADDING;
        int searchY = panelY + PADDING;
        int searchW = panelW - PADDING * 2;
        guiGraphics.fill(searchX - 1, searchY - 1, searchX + searchW + 1, searchY + SEARCH_HEIGHT + 1, 0xFF4A4A4A);
        guiGraphics.fill(searchX, searchY, searchX + searchW, searchY + SEARCH_HEIGHT, 0xFF0D0D0D);

        // Search text
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 300);
        String displayFilter = filter.isEmpty() ? "\u00A77Search entities..." : filter;

        if (allSelected && !filter.isEmpty()) {
            int textW = font.width(filter);
            guiGraphics.fill(searchX + 3, searchY + 3, searchX + 5 + textW, searchY + SEARCH_HEIGHT - 3, 0xFF4A6A9A);
        }

        guiGraphics.drawString(font, displayFilter, searchX + 4, searchY + 6, filter.isEmpty() ? 0x666666 : 0xFFFFFF, false);

        if (searchFocused && !allSelected && (System.currentTimeMillis() / 500) % 2 == 0) {
            int cursorX = searchX + 4 + (filter.isEmpty() ? 0 : font.width(filter));
            guiGraphics.fill(cursorX, searchY + 4, cursorX + 1, searchY + SEARCH_HEIGHT - 4, 0xFFFFFFFF);
        }
        guiGraphics.pose().popPose();

        // List area
        int listX = panelX + PADDING;
        int listY = searchY + SEARCH_HEIGHT + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        // Track hovered entity for tooltip
        String currentHovered = null;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;

            boolean rowHovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            guiGraphics.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT,
                    rowHovered ? 0xFF353535 : 0xFF252525);

            if (index < filteredEntities.size()) {
                EntityEntry entry = filteredEntities.get(index);

                // Render small entity preview
                LivingEntity living = getOrCreateEntity(entry.id);
                if (living != null) {
                    try {
                        float angle = (System.currentTimeMillis() % 3600) / 10.0f;
                        guiGraphics.enableScissor(listX, rowY, listX + 18, rowY + ROW_HEIGHT);
                        int entityScale = (int) Math.max(3, 9.0f / Math.max(living.getBbWidth(), living.getBbHeight()));
                        renderSpinningEntity(guiGraphics, listX + 9, rowY + ROW_HEIGHT - 2, entityScale, angle, living);
                        guiGraphics.disableScissor();
                    } catch (Exception ignored) {}
                }

                // Text next to preview
                String text = entry.displayName + " \u00A77(" + entry.id + ")";
                if (font.width(text) > listW - 22) {
                    text = font.plainSubstrByWidth(text, listW - 28) + "...";
                }
                guiGraphics.drawString(font, text, listX + 20, rowY + 6, rowHovered ? 0xFFFFFF : 0xBBBBBB, false);

                if (rowHovered) currentHovered = entry.id;
            }
        }

        // Scrollbar
        if (maxScrollRow > 0) {
            int scrollBarX = listX + listW + 2;
            int scrollBarTop = listY;
            int scrollBarBottom = listY + VISIBLE_ROWS * ROW_HEIGHT;
            int scrollBarHeight = scrollBarBottom - scrollBarTop;

            guiGraphics.fill(scrollBarX, scrollBarTop, scrollBarX + 4, scrollBarBottom, 0xFF252525);

            int thumbHeight = Math.max(10, (int) ((float) VISIBLE_ROWS / (maxScrollRow + VISIBLE_ROWS) * scrollBarHeight));
            int thumbY = scrollBarTop + (int) ((float) scrollRow / maxScrollRow * (scrollBarHeight - thumbHeight));
            guiGraphics.fill(scrollBarX, thumbY, scrollBarX + 4, thumbY + thumbHeight, 0xFF888888);
        }

    }

    /**
     * Renders a LivingEntity spinning around its Y axis. Uses direct entity rendering
     * instead of InventoryScreen helper to allow full 360° rotation.
     * Uses Z=1500 model view offset (final Z=550) to render above GUI elements at Z=400.
     */
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

        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
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
            modelViewStack.popPose();
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

        if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
            hide();
            return true;
        }

        // Scrollbar click
        if (maxScrollRow > 0) {
            int searchY = panelY + PADDING;
            int listY = searchY + SEARCH_HEIGHT + PADDING;
            int listW = panelW - PADDING * 2 - 8;
            int scrollBarX = panelX + PADDING + listW + 2;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 6
                    && mouseY >= listY && mouseY < listY + VISIBLE_ROWS * ROW_HEIGHT) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, listY);
                return true;
            }
        }

        // List clicks
        int searchY = panelY + PADDING;
        int listX = panelX + PADDING;
        int listY = searchY + SEARCH_HEIGHT + PADDING;
        int listW = panelW - PADDING * 2 - 8;

        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int index = scrollRow + i;
            int rowY = listY + i * ROW_HEIGHT;
            if (index < filteredEntities.size() && mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                onSelect.accept(filteredEntities.get(index).id);
                hide();
                return true;
            }
        }

        searchFocused = true;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY) {
        if (!visible || !draggingScrollbar) return false;
        int searchY = panelY + PADDING;
        int listY = searchY + SEARCH_HEIGHT + PADDING;
        updateScrollFromMouse(mouseY, listY);
        return true;
    }

    public boolean mouseReleased() {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    private void updateScrollFromMouse(double mouseY, int listY) {
        int listH = VISIBLE_ROWS * ROW_HEIGHT;
        float ratio = (float) Math.max(0, Math.min(1, (mouseY - listY) / (double) listH));
        scrollRow = Math.round(ratio * maxScrollRow);
        scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow));
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!visible) return false;
        if (mouseX >= panelX && mouseX <= panelX + panelW && mouseY >= panelY && mouseY <= panelY + panelH) {
            scrollRow = Math.max(0, Math.min(maxScrollRow, scrollRow - (int) delta));
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (!visible || !searchFocused) return false;

        if (keyCode == 256) { // ESC
            hide();
            return true;
        }
        if (keyCode == 259) { // BACKSPACE
            if (allSelected) {
                allSelected = false;
                setFilter("");
            } else if (!filter.isEmpty()) {
                setFilter(filter.substring(0, filter.length() - 1));
            }
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 65) { // Ctrl+A
            if (!filter.isEmpty()) allSelected = true;
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 67) { // Ctrl+C
            if (!filter.isEmpty()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(filter);
            }
            return true;
        }
        if (Screen.hasControlDown() && keyCode == 86) { // Ctrl+V
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clipboard != null && !clipboard.isEmpty()) {
                setFilter(allSelected ? clipboard : filter + clipboard);
                allSelected = false;
            }
            return true;
        }
        return false;
    }

    public boolean charTyped(char c) {
        if (!visible || !searchFocused) return false;
        if (Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '.' || c == ' ' || c == '-') {
            setFilter(allSelected ? String.valueOf(c) : filter + c);
            allSelected = false;
            return true;
        }
        return false;
    }

    private record EntityEntry(String id, String displayName, String searchName) {}
}
