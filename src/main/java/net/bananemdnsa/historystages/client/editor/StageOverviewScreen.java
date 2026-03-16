package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.ContextMenu;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.network.DeleteStagePacket;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SaveStagePacket;
import net.bananemdnsa.historystages.network.ToggleStageLockPacket;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StageOverviewScreen extends Screen {

    private static final int ENTRY_HEIGHT = 28;
    private static final int LIST_PADDING = 40;
    private static final int HEADER_HEIGHT = 30;

    private List<String> stageOrder;
    private double scrollOffset = 0;
    private int maxScroll = 0;
    private boolean draggingScrollbar = false;
    private ContextMenu contextMenu;

    public StageOverviewScreen() {
        super(Component.translatable("editor.historystages.title"));
    }

    @Override
    protected void init() {
        stageOrder = StageManager.getStageOrder();

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.new_stage"),
                btn -> openStageIdInputDialog(null),
                10, this.height - 30, 100, 20));

        this.addRenderableWidget(StyledButton.of(
                Component.literal("\u2699"),
                btn -> this.minecraft.setScreen(new ConfigEditorScreen(this)),
                this.width - 30, 5, 20, 20));

        contextMenu = new ContextMenu();
        updateMaxScroll();
    }

    private void updateMaxScroll() {
        int listHeight = this.height - HEADER_HEIGHT - LIST_PADDING - 40;
        int contentHeight = stageOrder.size() * ENTRY_HEIGHT;
        maxScroll = Math.max(0, contentHeight - listHeight);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        guiGraphics.fill(10, HEADER_HEIGHT, this.width - 10, HEADER_HEIGHT + 1, 0xFF555555);

        int listTop = HEADER_HEIGHT + 5;
        int listBottom = this.height - 40;
        int listLeft = 20;
        int listRight = this.width - 20;

        guiGraphics.enableScissor(listLeft, listTop, listRight, listBottom);

        Map<String, StageEntry> stages = StageManager.getStages();
        int y = listTop - (int) scrollOffset;

        for (int i = 0; i < stageOrder.size(); i++) {
            String stageId = stageOrder.get(i);
            StageEntry entry = stages.get(stageId);
            if (entry == null) continue;

            int entryTop = y + i * ENTRY_HEIGHT;
            int entryBottom = entryTop + ENTRY_HEIGHT - 2;

            if (entryBottom < listTop || entryTop > listBottom) continue;

            boolean hovered = mouseX >= listLeft && mouseX <= listRight
                    && mouseY >= Math.max(entryTop, listTop) && mouseY <= Math.min(entryBottom, listBottom);

            int bgColor = hovered ? 0x40FFFFFF : 0x20FFFFFF;
            guiGraphics.fill(listLeft, entryTop, listRight, entryBottom, bgColor);

            // Lock/unlock icon
            boolean unlocked = ClientStageCache.isStageUnlocked(stageId);
            String icon = unlocked ? "\u2714" : "\uD83D\uDD12";
            int iconColor = unlocked ? 0xFFCC00 : 0x888888;
            guiGraphics.drawString(this.font, icon, listLeft + 5, entryTop + 6, iconColor, false);

            // Stage name
            String displayText = entry.getDisplayName() + " \u00A77(" + stageId + ")";
            guiGraphics.drawString(this.font, displayText, listLeft + 22, entryTop + 4, 0xFFFFFF, false);

            // Item count info
            int itemCount = entry.getItems().size() + entry.getTags().size() + entry.getMods().size();
            String info = itemCount + " entries";
            guiGraphics.drawString(this.font, info, listLeft + 22, entryTop + 15, 0x888888, false);

            // Lock/Unlock toggle button (right side)
            int lockBtnX = listRight - 60;
            int lockBtnY = entryTop + 5;
            int lockBtnW = 50;
            int lockBtnH = 16;
            boolean lockBtnHovered = mouseX >= lockBtnX && mouseX <= lockBtnX + lockBtnW
                    && mouseY >= lockBtnY && mouseY <= lockBtnY + lockBtnH
                    && mouseY >= listTop && mouseY <= listBottom;

            int lockBg = lockBtnHovered ? 0x50FFCC00 : 0x25FFFFFF;
            guiGraphics.fill(lockBtnX, lockBtnY, lockBtnX + lockBtnW, lockBtnY + lockBtnH, lockBg);
            // Bottom accent (gold, like StyledButton)
            int lockAccent = lockBtnHovered ? 0xFFFFCC00 : 0x60FFCC00;
            guiGraphics.fill(lockBtnX, lockBtnY + lockBtnH - 1, lockBtnX + lockBtnW, lockBtnY + lockBtnH, lockAccent);

            String lockLabel = Component.translatable(unlocked ? "editor.historystages.lock" : "editor.historystages.unlock").getString();
            int lockTextColor = lockBtnHovered ? 0xFFFFFF : 0xCCCCCC;
            int textW = this.font.width(lockLabel);
            guiGraphics.drawString(this.font, lockLabel, lockBtnX + (lockBtnW - textW) / 2, lockBtnY + 4, lockTextColor, false);
        }

        guiGraphics.disableScissor();

        if (maxScroll > 0) {
            int scrollBarHeight = Math.max(20, (int) ((float) (listBottom - listTop) / (maxScroll + (listBottom - listTop)) * (listBottom - listTop)));
            int scrollBarY = listTop + (int) ((float) scrollOffset / maxScroll * (listBottom - listTop - scrollBarHeight));
            guiGraphics.fill(listRight + 2, scrollBarY, listRight + 5, scrollBarY + scrollBarHeight, 0x80FFFFFF);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 200);
        contextMenu.render(guiGraphics, this.font, mouseX, mouseY);
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (contextMenu.isVisible()) {
            contextMenu.mouseClicked(mouseX, mouseY, button);
            return true;
        }

        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int listTop = HEADER_HEIGHT + 5;
        int listBottom = this.height - 40;
        int listLeft = 20;
        int listRight = this.width - 20;

        if (maxScroll > 0 && mouseX >= listRight + 1 && mouseX <= listRight + 6
                && mouseY >= listTop && mouseY <= listBottom) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY, listTop, listBottom);
            return true;
        }

        if (mouseX < listLeft || mouseX > listRight || mouseY < listTop || mouseY > listBottom) return false;

        Map<String, StageEntry> stages = StageManager.getStages();
        int y = listTop - (int) scrollOffset;

        for (int i = 0; i < stageOrder.size(); i++) {
            String stageId = stageOrder.get(i);
            StageEntry entry = stages.get(stageId);
            if (entry == null) continue;

            int entryTop = y + i * ENTRY_HEIGHT;
            int entryBottom = entryTop + ENTRY_HEIGHT - 2;

            if (mouseY >= entryTop && mouseY <= entryBottom) {
                // Check lock/unlock button click
                boolean unlocked = ClientStageCache.isStageUnlocked(stageId);
                int lockBtnX = listRight - 60;
                int lockBtnY = entryTop + 5;
                if (button == 0 && mouseX >= lockBtnX && mouseX <= lockBtnX + 50
                        && mouseY >= lockBtnY && mouseY <= lockBtnY + 16) {
                    PacketHandler.sendToServer(new ToggleStageLockPacket(stageId, !unlocked));
                    return true;
                }

                // Right-click context menu
                if (button == 1) {
                    contextMenu = new ContextMenu();
                    contextMenu.addEntry(Component.translatable("editor.historystages.edit").getString(), () -> {
                        this.minecraft.setScreen(new StageDetailScreen(this, stageId, entry));
                    });
                    contextMenu.addEntry(Component.translatable("editor.historystages.duplicate").getString(), () -> {
                        openStageIdInputDialog(stageId);
                    });
                    contextMenu.addEntry(Component.translatable("editor.historystages.delete").getString(), () -> {
                        Screen self = this;
                        this.minecraft.setScreen(new ConfirmDialog(this,
                                Component.translatable("editor.historystages.confirm_delete_title"),
                                Component.translatable("editor.historystages.confirm_delete", stageId),
                                () -> { PacketHandler.sendToServer(new DeleteStagePacket(stageId)); stageOrder.remove(stageId); updateMaxScroll(); Minecraft.getInstance().setScreen(self); }));
                    });
                    contextMenu.show((int) mouseX, (int) mouseY, this.font);
                    return true;
                }

                // Left-click on stage entry -> open detail editor
                this.minecraft.setScreen(new StageDetailScreen(this, stageId, entry));
                return true;
            }
        }
        return false;
    }

    private void openStageIdInputDialog(String duplicateFromId) {
        this.minecraft.setScreen(new StageIdInputScreen(this, duplicateFromId));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 10));
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            int listTop = HEADER_HEIGHT + 5;
            int listBottom = this.height - 40;
            updateScrollFromMouse(mouseY, listTop, listBottom);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) { draggingScrollbar = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateScrollFromMouse(double mouseY, int listTop, int listBottom) {
        int scrollAreaHeight = listBottom - listTop;
        float ratio = (float) Math.max(0, Math.min(1, (mouseY - listTop) / (double) scrollAreaHeight));
        scrollOffset = Math.round(ratio * maxScroll);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public void onClose() { this.minecraft.setScreen(null); }
    @Override public boolean isPauseScreen() { return true; }

    /**
     * Dialog screen that asks for a Stage ID before creating/duplicating a stage.
     */
    static class StageIdInputScreen extends Screen {
        private final StageOverviewScreen parent;
        private final String duplicateFromId;
        private EditBox idField;
        private String errorMessage = "";

        protected StageIdInputScreen(StageOverviewScreen parent, String duplicateFromId) {
            super(Component.translatable("editor.historystages.new_stage"));
            this.parent = parent;
            this.duplicateFromId = duplicateFromId;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            idField = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20,
                    Component.translatable("editor.historystages.field.stage_id"));
            idField.setMaxLength(64);
            idField.setFocused(true);
            idField.setFilter(s -> s.matches("[a-zA-Z0-9_\\-]*"));
            idField.setResponder(val -> errorMessage = "");
            this.addRenderableWidget(idField);
            this.setFocused(idField);

            String confirmLabel = duplicateFromId != null
                    ? Component.translatable("editor.historystages.duplicate").getString()
                    : Component.translatable("editor.historystages.confirm").getString();

            this.addRenderableWidget(StyledButton.of(Component.literal(confirmLabel),
                    btn -> confirmId(), centerX - 105, centerY + 20, 100, 20));
            this.addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.cancel"),
                    btn -> this.minecraft.setScreen(parent), centerX + 5, centerY + 20, 100, 20));
        }

        private void confirmId() {
            String id = idField.getValue().trim();
            if (id.isEmpty()) { errorMessage = Component.translatable("editor.historystages.id_empty").getString(); return; }
            if (!id.matches("[a-zA-Z0-9_\\-]+")) { errorMessage = Component.translatable("editor.historystages.id_invalid").getString(); return; }
            if (StageManager.getStages().containsKey(id)) { errorMessage = Component.translatable("editor.historystages.id_exists").getString(); return; }

            if (duplicateFromId != null) {
                StageEntry source = StageManager.getStages().get(duplicateFromId);
                if (source != null) {
                    StageEntry copy = source.copy();
                    PacketHandler.sendToServer(new SaveStagePacket(id, copy));
                    this.minecraft.setScreen(new StageDetailScreen(parent, id, copy));
                } else { this.minecraft.setScreen(parent); }
            } else {
                this.minecraft.setScreen(new StageDetailScreen(parent, id, null));
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 257) { confirmId(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xC0000000);
            int boxW = 260;
            int boxH = 110;
            int boxX = (this.width - boxW) / 2;
            int boxY = (this.height - boxH) / 2 - 10;
            guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF2D2D2D);
            guiGraphics.fill(boxX + 1, boxY + 1, boxX + boxW - 1, boxY + boxH - 1, 0xFF1A1A1A);

            String title = duplicateFromId != null
                    ? Component.translatable("editor.historystages.duplicate").getString() + " \u2014 " + duplicateFromId
                    : Component.translatable("editor.historystages.new_stage").getString();
            guiGraphics.drawCenteredString(this.font, title, this.width / 2, boxY + 6, 0xFFFFFF);
            guiGraphics.drawString(this.font, Component.translatable("editor.historystages.field.stage_id").getString(), boxX + 10, this.height / 2 - 22, 0xAAAAAA, false);
            if (!errorMessage.isEmpty()) {
                guiGraphics.drawCenteredString(this.font, errorMessage, this.width / 2, this.height / 2 + 46, 0xFF5555);
            }
            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override public void onClose() { this.minecraft.setScreen(parent); }
    }
}
