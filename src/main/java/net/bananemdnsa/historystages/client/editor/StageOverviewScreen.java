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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StageOverviewScreen extends Screen {

    private static final int ENTRY_HEIGHT = 28;
    private static final int LIST_PADDING = 40;
    private static final int HEADER_HEIGHT = 30;
    private static final int SECTION_HEADER_HEIGHT = 22;

    private List<String> stageOrder;
    private List<String> individualStageOrder;
    private List<String> filteredStageOrder = new ArrayList<>();
    private List<String> filteredIndividualStageOrder = new ArrayList<>();
    private EditBox searchBox;
    private String searchFilter = "";
    private int lastKnownStageCount = -1;
    private int lastKnownIndividualCount = -1;
    private double scrollOffset = 0;
    private int maxScroll = 0;
    private boolean draggingScrollbar = false;
    private ContextMenu contextMenu;

    // Animation state
    private final java.util.Map<Integer, Float> hoverProgress = new java.util.HashMap<>();
    private int lastHoveredIndex = -1;

    // Marquee state
    private int hoveredStageIndex = -1;
    private long stageHoverStartTime = 0;
    private static final long MARQUEE_DELAY_MS = 800;
    private static final float MARQUEE_SPEED = 25.0f;

    // Smooth scroll
    private float smoothScroll = 0;

    public StageOverviewScreen() {
        super(Component.translatable("editor.historystages.title"));
    }

    @Override
    protected void init() {
        stageOrder = StageManager.getStageOrder();
        individualStageOrder = StageManager.getIndividualStageOrder();

        searchFilter = "";
        int searchW = 120;
        searchBox = new EditBox(this.font, 12, 8, searchW - 4, 14,
                Component.translatable("editor.historystages.search"));
        searchBox.setMaxLength(128);
        searchBox.setBordered(false);
        searchBox.setValue(searchFilter);
        searchBox.setResponder(val -> {
            searchFilter = val;
            applyFilter();
        });
        this.addRenderableWidget(searchBox);

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.new_stage"),
                btn -> openStageIdInputDialog(null, false),
                10, this.height - 30, 100, 20));

        this.addRenderableWidget(StyledButton.of(
                Component.literal("\u2699"),
                btn -> this.minecraft.setScreen(new ConfigEditorScreen(this)),
                this.width - 30, 5, 20, 20));

        contextMenu = new ContextMenu();
        applyFilter();
    }

    private void applyFilter() {
        String query = searchFilter.toLowerCase().trim();
        Map<String, StageEntry> stages = StageManager.getStages();
        Map<String, StageEntry> individualStages = StageManager.getIndividualStages();

        filteredStageOrder = new ArrayList<>();
        for (String id : stageOrder) {
            if (query.isEmpty() || matchesFilter(id, stages.get(id), query)) {
                filteredStageOrder.add(id);
            }
        }
        filteredIndividualStageOrder = new ArrayList<>();
        for (String id : individualStageOrder) {
            if (query.isEmpty() || matchesFilter(id, individualStages.get(id), query)) {
                filteredIndividualStageOrder.add(id);
            }
        }
        updateMaxScroll();
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private boolean matchesFilter(String stageId, StageEntry entry, String query) {
        if (stageId.toLowerCase().contains(query)) return true;
        if (entry != null && entry.getDisplayName().toLowerCase().contains(query)) return true;
        return false;
    }

    private void updateMaxScroll() {
        int listHeight = this.height - HEADER_HEIGHT - LIST_PADDING - 40;
        int contentHeight = SECTION_HEADER_HEIGHT + filteredStageOrder.size() * ENTRY_HEIGHT;
        if (!filteredIndividualStageOrder.isEmpty()) {
            contentHeight += SECTION_HEADER_HEIGHT + filteredIndividualStageOrder.size() * ENTRY_HEIGHT;
        }
        maxScroll = Math.max(0, contentHeight - listHeight);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op — we draw our own background in render() and want to avoid 1.21's menu blur shader
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Refresh stage list if another admin changed definitions (broadcast via SyncStageDefinitionsPacket)
        int currentCount = StageManager.getStages().size();
        int currentIndividualCount = StageManager.getIndividualStages().size();
        if (currentCount != lastKnownStageCount || !StageManager.getStages().keySet().containsAll(stageOrder) || !stageOrder.containsAll(StageManager.getStages().keySet())
                || currentIndividualCount != lastKnownIndividualCount) {
            stageOrder = StageManager.getStageOrder();
            individualStageOrder = StageManager.getIndividualStageOrder();
            lastKnownStageCount = currentCount;
            lastKnownIndividualCount = currentIndividualCount;
            applyFilter();
        }

        // Smooth scroll
        smoothScroll += ((float) scrollOffset - smoothScroll) * 0.25f;
        if (Math.abs(smoothScroll - (float) scrollOffset) < 0.5f) smoothScroll = (float) scrollOffset;

        guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        // Search bar (left side, same row as title)
        int searchW = 120;
        int searchX = 10;
        guiGraphics.fill(searchX, 5, searchX + searchW, 23, 0x25FFFFFF);
        guiGraphics.fill(searchX, 23, searchX + searchW, 24, searchBox.isFocused() ? 0xFFFFCC00 : 0xFF555555);
        if (searchFilter.isEmpty() && !searchBox.isFocused()) {
            guiGraphics.drawString(this.font, Component.translatable("editor.historystages.search").getString(),
                    searchX + 4, 10, 0x888888, false);
        }

        guiGraphics.fill(10, HEADER_HEIGHT, this.width - 10, HEADER_HEIGHT + 1, 0xFF555555);

        int listTop = HEADER_HEIGHT + 5;
        int listBottom = this.height - 40;
        int listLeft = 20;
        int listRight = this.width - 20;

        guiGraphics.enableScissor(listLeft, listTop, listRight, listBottom);

        boolean overlayOpen = contextMenu != null && contextMenu.isVisible();
        int effectiveMouseX = overlayOpen ? -1 : mouseX;
        int effectiveMouseY = overlayOpen ? -1 : mouseY;

        Map<String, StageEntry> stages = StageManager.getStages();
        Map<String, StageEntry> individualStages = StageManager.getIndividualStages();
        int y = listTop - (int) smoothScroll;

        int currentHovered = -1;
        int currentHoveredStage = -1;

        // --- Global Stages Section Header ---
        int globalHeaderY = y;
        if (globalHeaderY + SECTION_HEADER_HEIGHT > listTop && globalHeaderY < listBottom) {
            guiGraphics.fill(listLeft, globalHeaderY + 8, listRight, globalHeaderY + 9, 0xFF555555);
            String globalLabel = "\u00A78Global Stages (" + filteredStageOrder.size() + ")";
            int glLabelW = this.font.width(globalLabel);
            int glLabelX = listLeft + 5;
            guiGraphics.fill(glLabelX - 2, globalHeaderY + 3, glLabelX + glLabelW + 2, globalHeaderY + 15, 0xE0101010);
            guiGraphics.drawString(this.font, globalLabel, glLabelX, globalHeaderY + 4, 0x888888, false);
        }
        y += SECTION_HEADER_HEIGHT;

        // --- Global Stages ---
        for (int i = 0; i < filteredStageOrder.size(); i++) {
            String stageId = filteredStageOrder.get(i);
            StageEntry entry = stages.get(stageId);
            if (entry == null) continue;

            int entryTop = y + i * ENTRY_HEIGHT;
            int entryBottom = entryTop + ENTRY_HEIGHT - 2;

            if (entryBottom < listTop || entryTop > listBottom) { continue; }

            // Lock button bounds (calculated early for hover exclusion)
            boolean unlocked = ClientStageCache.isStageUnlocked(stageId);
            String lockLabel = Component.translatable(unlocked ? "editor.historystages.lock" : "editor.historystages.unlock").getString();
            int lockBtnW = Math.max(50, this.font.width(lockLabel) + 12);
            int lockBtnX = listRight - lockBtnW - 10;
            int lockBtnH = 16;
            int lockBtnY = entryTop + 5;
            boolean onLockBtn = effectiveMouseX >= lockBtnX && effectiveMouseX <= lockBtnX + lockBtnW
                    && effectiveMouseY >= lockBtnY && effectiveMouseY <= lockBtnY + lockBtnH;

            boolean hovered = effectiveMouseX >= listLeft && effectiveMouseX <= listRight
                    && effectiveMouseY >= Math.max(entryTop, listTop) && effectiveMouseY <= Math.min(entryBottom, listBottom)
                    && !onLockBtn;

            if (hovered) { currentHovered = i; currentHoveredStage = i; }

            // Smooth hover animation (0.0 -> 1.0)
            float progress = hoverProgress.getOrDefault(i, 0.0f);
            if (hovered) {
                progress = Math.min(1.0f, progress + 0.08f);
            } else {
                progress = Math.max(0.0f, progress - 0.06f);
            }
            if (progress > 0.001f) hoverProgress.put(i, progress);
            else hoverProgress.remove(i);

            // Animated background - subtle gold tint on hover
            int bgAlpha = (int) (0x20 + progress * 0x25);
            int bgG = (int) (0xFF + progress * (0xCC - 0xFF));
            int bgB = (int) (0xFF + progress * (0x00 - 0xFF));
            int bgColor = (bgAlpha << 24) | (0xFF << 16) | (bgG << 8) | bgB;
            guiGraphics.fill(listLeft, entryTop, listRight, entryBottom, bgColor);

            // Gold accent bar on left when hovered
            if (progress > 0.01f) {
                int accentAlpha = (int) (progress * 0xFF);
                guiGraphics.fill(listLeft, entryTop, listLeft + 2, entryBottom, (accentAlpha << 24) | 0xFFCC00);
            }

            // Lock/unlock icon
            String icon = unlocked ? "\u2714" : "\uD83D\uDD12";
            int iconColor = unlocked ? 0xFFCC00 : 0x888888;
            guiGraphics.drawString(this.font, icon, listLeft + 5, entryTop + 6, iconColor, false);

            // Stage name with marquee for long names
            String displayText = entry.getDisplayName() + " \u00A77(" + stageId + ")";
            int nameColor = progress > 0.01f ? 0xFFFFFF : 0xEEEEEE;
            int nameX = listLeft + 22;
            int nameAvailW = (listRight - lockBtnW - 10) - nameX - 5; // account for lock button
            int nameW = this.font.width(displayText);

            if (nameW > nameAvailW && hovered && i == hoveredStageIndex) {
                long elapsed = System.currentTimeMillis() - stageHoverStartTime;
                if (elapsed > MARQUEE_DELAY_MS) {
                    float scrollProg = (elapsed - MARQUEE_DELAY_MS) / 1000.0f * MARQUEE_SPEED;
                    int maxMarquee = nameW - nameAvailW + 10;
                    float cycle = (float) maxMarquee * 2;
                    float pos = scrollProg % cycle;
                    int scrollOff = pos <= maxMarquee ? (int) pos : (int) (cycle - pos);
                    guiGraphics.enableScissor(nameX, entryTop, nameX + nameAvailW, entryBottom);
                    guiGraphics.drawString(this.font, displayText, nameX - scrollOff, entryTop + 4, nameColor, false);
                    guiGraphics.disableScissor();
                } else {
                    guiGraphics.drawString(this.font, displayText, nameX, entryTop + 4, nameColor, false);
                }
            } else {
                guiGraphics.drawString(this.font, displayText, nameX, entryTop + 4, nameColor, false);
            }

            // Item count info
            int itemCount = entry.getItemEntries().size() + entry.getTags().size() + entry.getMods().size()
                    + entry.getRecipes().size() + entry.getDimensions().size() + entry.getStructures().size()
                    + entry.getEntities().getAttacklock().size() + entry.getEntities().getSpawnlock().size();
            String info = itemCount + " entries";
            int infoColor = (int) (0x88 + progress * 0x33);
            guiGraphics.drawString(this.font, info, listLeft + 22, entryTop + 15, (0xFF << 24) | (infoColor << 16) | (infoColor << 8) | infoColor, false);
            if (entry.hasDependencies()) {
                int depBadgeX = listLeft + 22 + this.font.width(info) + 6;
                guiGraphics.drawString(this.font, "\u00A76[Dep]", depBadgeX, entryTop + 15, 0xFFAA55, false);
            }

            // Lock/Unlock toggle button (right side) - bounds already calculated above
            boolean lockBtnHovered = onLockBtn && mouseY >= listTop && mouseY <= listBottom;

            int lockBg = lockBtnHovered ? 0x50FFCC00 : 0x25FFFFFF;
            guiGraphics.fill(lockBtnX, lockBtnY, lockBtnX + lockBtnW, lockBtnY + lockBtnH, lockBg);
            // Bottom accent (gold, like StyledButton)
            int lockAccent = lockBtnHovered ? 0xFFFFCC00 : 0x60FFCC00;
            guiGraphics.fill(lockBtnX, lockBtnY + lockBtnH - 1, lockBtnX + lockBtnW, lockBtnY + lockBtnH, lockAccent);

            int lockTextColor = lockBtnHovered ? 0xFFFFFF : 0xCCCCCC;
            int textW = this.font.width(lockLabel);
            guiGraphics.drawString(this.font, lockLabel, lockBtnX + (lockBtnW - textW) / 2, lockBtnY + 4, lockTextColor, false);
        }

        // --- Individual Stages Section ---
        if (!filteredIndividualStageOrder.isEmpty()) {
            int sectionY = y + filteredStageOrder.size() * ENTRY_HEIGHT; // y already includes global header offset

            // Section header
            if (sectionY + SECTION_HEADER_HEIGHT > listTop && sectionY < listBottom) {
                guiGraphics.fill(listLeft, sectionY + 8, listRight, sectionY + 9, 0xFF555555);
                String sectionLabel = "\u00A78Individual Stages (" + filteredIndividualStageOrder.size() + ")";
                int labelW = this.font.width(sectionLabel);
                int labelX = listLeft + 5;
                guiGraphics.fill(labelX - 2, sectionY + 3, labelX + labelW + 2, sectionY + 15, 0xE0101010);
                guiGraphics.drawString(this.font, sectionLabel, labelX, sectionY + 4, 0x888888, false);
            }

            int indY = sectionY + SECTION_HEADER_HEIGHT;
            for (int i = 0; i < filteredIndividualStageOrder.size(); i++) {
                String stageId = filteredIndividualStageOrder.get(i);
                StageEntry entry = individualStages.get(stageId);
                if (entry == null) continue;

                int entryTop = indY + i * ENTRY_HEIGHT;
                int entryBottom = entryTop + ENTRY_HEIGHT - 2;

                if (entryBottom < listTop || entryTop > listBottom) continue;

                // Unique hover key for individual stages (offset to avoid collision with global)
                int hoverKey = 10000 + i;

                boolean hovered = effectiveMouseX >= listLeft && effectiveMouseX <= listRight
                        && effectiveMouseY >= Math.max(entryTop, listTop) && effectiveMouseY <= Math.min(entryBottom, listBottom);

                if (hovered) { currentHovered = hoverKey; currentHoveredStage = hoverKey; }

                float progress = hoverProgress.getOrDefault(hoverKey, 0.0f);
                if (hovered) {
                    progress = Math.min(1.0f, progress + 0.08f);
                } else {
                    progress = Math.max(0.0f, progress - 0.06f);
                }
                if (progress > 0.001f) hoverProgress.put(hoverKey, progress);
                else hoverProgress.remove(hoverKey);

                // Animated background - subtle silver tint on hover
                int bgAlpha = (int) (0x20 + progress * 0x25);
                int bgG = (int) (0xFF + progress * (0xCC - 0xFF));
                int bgColor = (bgAlpha << 24) | (bgG << 16) | (bgG << 8) | 0xFF;
                guiGraphics.fill(listLeft, entryTop, listRight, entryBottom, bgColor);

                // Silver accent bar on left when hovered
                if (progress > 0.01f) {
                    int accentAlpha = (int) (progress * 0xFF);
                    guiGraphics.fill(listLeft, entryTop, listLeft + 2, entryBottom, (accentAlpha << 24) | 0xBBBBBB);
                }

                // Silver lock icon (individual stages are per-player, no global unlock state)
                guiGraphics.drawString(this.font, "\uD83D\uDD12", listLeft + 5, entryTop + 6, 0xBBBBBB, false);

                // Stage name with marquee
                String displayText = entry.getDisplayName() + " \u00A78(" + stageId + ")";
                int nameColor = progress > 0.01f ? 0xDDDDDD : 0xBBBBBB;
                int nameX = listLeft + 22;
                int nameAvailW = listRight - nameX - 5;
                int nameW = this.font.width(displayText);

                if (nameW > nameAvailW && hovered && hoverKey == hoveredStageIndex) {
                    long elapsed = System.currentTimeMillis() - stageHoverStartTime;
                    if (elapsed > MARQUEE_DELAY_MS) {
                        float scrollProg = (elapsed - MARQUEE_DELAY_MS) / 1000.0f * MARQUEE_SPEED;
                        int maxMarquee = nameW - nameAvailW + 10;
                        float cycle = (float) maxMarquee * 2;
                        float pos = scrollProg % cycle;
                        int scrollOff = pos <= maxMarquee ? (int) pos : (int) (cycle - pos);
                        guiGraphics.enableScissor(nameX, entryTop, nameX + nameAvailW, entryBottom);
                        guiGraphics.drawString(this.font, displayText, nameX - scrollOff, entryTop + 4, nameColor, false);
                        guiGraphics.disableScissor();
                    } else {
                        guiGraphics.drawString(this.font, displayText, nameX, entryTop + 4, nameColor, false);
                    }
                } else {
                    guiGraphics.drawString(this.font, displayText, nameX, entryTop + 4, nameColor, false);
                }

                // Item count info
                int itemCount = entry.getItemEntries().size() + entry.getTags().size() + entry.getMods().size()
                        + entry.getDimensions().size() + entry.getStructures().size()
                        + entry.getEntities().getAttacklock().size();
                String info = itemCount + " entries";
                int infoColor = (int) (0x88 + progress * 0x33);
                guiGraphics.drawString(this.font, info, listLeft + 22, entryTop + 15, (0xFF << 24) | (infoColor << 16) | (infoColor << 8) | infoColor, false);
            }
        }

        lastHoveredIndex = currentHovered;

        // Update marquee tracking
        if (currentHoveredStage != hoveredStageIndex) {
            hoveredStageIndex = currentHoveredStage;
            stageHoverStartTime = System.currentTimeMillis();
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
        // Unfocus search box when clicking outside it
        if (searchBox.isFocused() && !(mouseX >= 10 && mouseX <= 130 && mouseY >= 5 && mouseY <= 24)) {
            searchBox.setFocused(false);
        }

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
        Map<String, StageEntry> individualStages = StageManager.getIndividualStages();
        int y = listTop - (int) scrollOffset + SECTION_HEADER_HEIGHT; // skip global header

        // Global stages
        for (int i = 0; i < filteredStageOrder.size(); i++) {
            String stageId = filteredStageOrder.get(i);
            StageEntry entry = stages.get(stageId);
            if (entry == null) continue;

            int entryTop = y + i * ENTRY_HEIGHT;
            int entryBottom = entryTop + ENTRY_HEIGHT - 2;

            if (mouseY >= entryTop && mouseY <= entryBottom) {
                // Check lock/unlock button click
                boolean unlocked = ClientStageCache.isStageUnlocked(stageId);
                String lockLabelClick = Component.translatable(unlocked ? "editor.historystages.lock" : "editor.historystages.unlock").getString();
                int lockBtnWClick = Math.max(50, this.font.width(lockLabelClick) + 12);
                int lockBtnX = listRight - lockBtnWClick - 10;
                int lockBtnY = entryTop + 5;
                if (button == 0 && mouseX >= lockBtnX && mouseX <= lockBtnX + lockBtnWClick
                        && mouseY >= lockBtnY && mouseY <= lockBtnY + 16) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    PacketHandler.sendToServer(new ToggleStageLockPacket(stageId, !unlocked));
                    return true;
                }

                // Right-click context menu
                if (button == 1) {
                    contextMenu = new ContextMenu();
                    contextMenu.addEntry(Component.translatable("editor.historystages.edit").getString(), () -> {
                        this.minecraft.setScreen(new StageDetailScreen(this, stageId, entry, false));
                    });
                    contextMenu.addEntry(Component.translatable("editor.historystages.duplicate").getString(), () -> {
                        openStageIdInputDialog(stageId, false);
                    });
                    contextMenu.addEntry(Component.translatable("editor.historystages.delete").getString(), () -> {
                        Screen self = this;
                        this.minecraft.setScreen(new ConfirmDialog(this,
                                Component.translatable("editor.historystages.confirm_delete_title"),
                                Component.translatable("editor.historystages.confirm_delete", stageId),
                                () -> { PacketHandler.sendToServer(new DeleteStagePacket(stageId, false)); stageOrder.remove(stageId); applyFilter(); Minecraft.getInstance().setScreen(self); }));
                    });
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    contextMenu.show((int) mouseX, (int) mouseY, this.font);
                    return true;
                }

                // Left-click on stage entry -> open detail editor
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                this.minecraft.setScreen(new StageDetailScreen(this, stageId, entry, false));
                return true;
            }
        }

        // Individual stages
        if (!filteredIndividualStageOrder.isEmpty()) {
            int indY = y + filteredStageOrder.size() * ENTRY_HEIGHT + SECTION_HEADER_HEIGHT;
            for (int i = 0; i < filteredIndividualStageOrder.size(); i++) {
                String stageId = filteredIndividualStageOrder.get(i);
                StageEntry entry = individualStages.get(stageId);
                if (entry == null) continue;

                int entryTop = indY + i * ENTRY_HEIGHT;
                int entryBottom = entryTop + ENTRY_HEIGHT - 2;

                if (mouseY >= entryTop && mouseY <= entryBottom) {
                    if (button == 1) {
                        contextMenu = new ContextMenu();
                        contextMenu.addEntry(Component.translatable("editor.historystages.edit").getString(), () -> {
                            this.minecraft.setScreen(new StageDetailScreen(this, stageId, entry, true));
                        });
                        contextMenu.addEntry(Component.translatable("editor.historystages.duplicate").getString(), () -> {
                            openStageIdInputDialog(stageId, true);
                        });
                        contextMenu.addEntry(Component.translatable("editor.historystages.delete").getString(), () -> {
                            Screen self = this;
                            this.minecraft.setScreen(new ConfirmDialog(this,
                                    Component.translatable("editor.historystages.confirm_delete_title"),
                                    Component.translatable("editor.historystages.confirm_delete", stageId),
                                    () -> { PacketHandler.sendToServer(new DeleteStagePacket(stageId, true)); individualStageOrder.remove(stageId); applyFilter(); Minecraft.getInstance().setScreen(self); }));
                        });
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        contextMenu.show((int) mouseX, (int) mouseY, this.font);
                        return true;
                    }

                    // Left-click -> open detail editor (individual mode)
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    this.minecraft.setScreen(new StageDetailScreen(this, stageId, entry, true));
                    return true;
                }
            }
        }

        return false;
    }

    private void openStageIdInputDialog(String duplicateFromId, boolean individual) {
        this.minecraft.setScreen(new StageIdInputScreen(this, duplicateFromId, individual));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 10));
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
        int listH = listBottom - listTop;
        int totalH = maxScroll + listH;
        int thumbHeight = Math.max(20, (int) ((float) listH / totalH * listH));
        float usableH = listH - thumbHeight;
        if (usableH > 0) {
            float ratio = (float) (mouseY - listTop - thumbHeight / 2.0) / usableH;
            ratio = Math.max(0, Math.min(1, ratio));
            scrollOffset = Math.round(ratio * maxScroll);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        }
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
        private boolean individual;
        private EditBox idField;
        private String errorMessage = "";

        // Dropdown state
        private boolean dropdownOpen = false;
        private int dropdownX, dropdownY, dropdownW;

        protected StageIdInputScreen(StageOverviewScreen parent, String duplicateFromId, boolean individual) {
            super(Component.translatable("editor.historystages.new_stage"));
            this.parent = parent;
            this.duplicateFromId = duplicateFromId;
            this.individual = individual;
        }

        @Override
        protected void init() {
            int boxW = 300;
            int boxH = 130;
            int boxX = (this.width - boxW) / 2;
            int boxY = (this.height - boxH) / 2 - 10;

            int fieldX = boxX + 50;
            int fieldW = boxW - 100;
            int fieldY = boxY + 48;

            idField = new EditBox(this.font, fieldX, fieldY, fieldW, 20,
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

            int btnY = fieldY + 28;
            this.addRenderableWidget(StyledButton.of(Component.literal(confirmLabel),
                    btn -> confirmId(), fieldX, btnY, (fieldW - 10) / 2, 20));
            this.addRenderableWidget(StyledButton.of(Component.translatable("editor.historystages.cancel"),
                    btn -> this.minecraft.setScreen(parent), fieldX + (fieldW + 10) / 2, btnY, (fieldW - 10) / 2, 20));
        }

        private void confirmId() {
            String id = idField.getValue().trim();
            if (id.isEmpty()) { errorMessage = Component.translatable("editor.historystages.id_empty").getString(); return; }
            if (!id.matches("[a-zA-Z0-9_\\-]+")) { errorMessage = Component.translatable("editor.historystages.id_invalid").getString(); return; }
            if (StageManager.getStages().containsKey(id) || StageManager.getIndividualStages().containsKey(id)) {
                errorMessage = Component.translatable("editor.historystages.id_exists").getString(); return;
            }

            if (duplicateFromId != null) {
                StageEntry source = individual
                        ? StageManager.getIndividualStages().get(duplicateFromId)
                        : StageManager.getStages().get(duplicateFromId);
                if (source != null) {
                    StageEntry copy = source.copy();
                    PacketHandler.sendToServer(new SaveStagePacket(id, copy, individual));
                    this.minecraft.setScreen(new StageDetailScreen(parent, id, copy, individual));
                } else { this.minecraft.setScreen(parent); }
            } else {
                this.minecraft.setScreen(new StageDetailScreen(parent, id, null, individual));
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (dropdownOpen && keyCode == 256) { dropdownOpen = false; return true; }
            if (keyCode == 257) { confirmId(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                // Check dropdown item clicks first (when open)
                if (dropdownOpen) {
                    int optH = 16;
                    // "Global" option
                    int globalY = dropdownY + 18;
                    if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownW
                            && mouseY >= globalY && mouseY < globalY + optH) {
                        individual = false;
                        dropdownOpen = false;
                        Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                    // "Individual" option
                    int indY = globalY + optH;
                    if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownW
                            && mouseY >= indY && mouseY < indY + optH) {
                        individual = true;
                        dropdownOpen = false;
                        Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        return true;
                    }
                    // Click outside dropdown closes it
                    dropdownOpen = false;
                    return true;
                }

                // Check dropdown button click
                if (mouseX >= dropdownX && mouseX <= dropdownX + dropdownW
                        && mouseY >= dropdownY && mouseY < dropdownY + 16) {
                    dropdownOpen = !dropdownOpen;
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // Render parent screen behind overlay
            if (parent != null) {
                parent.render(guiGraphics, -1, -1, partialTick);
            }

            // Semi-transparent overlay
            guiGraphics.fill(0, 0, this.width, this.height, 0xC0000000);

            int boxW = 300;
            int boxH = 130;
            int boxX = (this.width - boxW) / 2;
            int boxY = (this.height - boxH) / 2 - 10;
            guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF2D2D2D);
            guiGraphics.fill(boxX + 1, boxY + 1, boxX + boxW - 1, boxY + boxH - 1, 0xFF1A1A1A);

            String title = duplicateFromId != null
                    ? Component.translatable("editor.historystages.duplicate").getString() + " \u2014 " + duplicateFromId
                    : Component.translatable("editor.historystages.new_stage").getString();
            guiGraphics.drawCenteredString(this.font, title, this.width / 2, boxY + 6, 0xFFFFFF);

            // Type dropdown (top-right of dialog box)
            dropdownW = 80;
            dropdownX = boxX + boxW - dropdownW - 8;
            dropdownY = boxY + 4;

            String typeLabel = individual ? "Individual" : "Global";
            int typeColor = individual ? 0xBBBBBB : 0xFFCC00;
            boolean dropHovered = mouseX >= dropdownX && mouseX <= dropdownX + dropdownW
                    && mouseY >= dropdownY && mouseY < dropdownY + 16;

            // Dropdown button
            int dropBg = dropHovered ? 0x40FFFFFF : 0x25FFFFFF;
            guiGraphics.fill(dropdownX, dropdownY, dropdownX + dropdownW, dropdownY + 16, dropBg);
            guiGraphics.fill(dropdownX, dropdownY + 14, dropdownX + dropdownW, dropdownY + 16,
                    dropHovered ? (typeColor | 0xFF000000) : 0x60FFFFFF);
            guiGraphics.drawString(this.font, typeLabel, dropdownX + 4, dropdownY + 4, typeColor, false);
            // Arrow indicator
            String arrow = dropdownOpen ? "\u25B2" : "\u25BC";
            guiGraphics.drawString(this.font, arrow, dropdownX + dropdownW - 10, dropdownY + 4, 0x999999, false);

            // Stage ID label above the text field
            int fieldX = boxX + 50;
            int fieldY = boxY + 48;
            guiGraphics.drawString(this.font, Component.translatable("editor.historystages.field.stage_id").getString(),
                    fieldX, fieldY - 12, 0xAAAAAA, false);

            if (!errorMessage.isEmpty()) {
                guiGraphics.drawCenteredString(this.font, errorMessage, this.width / 2, boxY + boxH - 16, 0xFF5555);
            }

            super.render(guiGraphics, mouseX, mouseY, partialTick);

            // Render dropdown options on top of everything
            if (dropdownOpen) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, 300);
                int optY = dropdownY + 18;
                int optH = 16;
                // Background
                guiGraphics.fill(dropdownX - 1, optY - 1, dropdownX + dropdownW + 1, optY + optH * 2 + 1, 0xFF333333);
                guiGraphics.fill(dropdownX, optY, dropdownX + dropdownW, optY + optH * 2, 0xFF1A1A1A);

                // "Global" option
                boolean globalHov = mouseX >= dropdownX && mouseX <= dropdownX + dropdownW
                        && mouseY >= optY && mouseY < optY + optH;
                if (globalHov) guiGraphics.fill(dropdownX, optY, dropdownX + dropdownW, optY + optH, 0x30FFCC00);
                if (!individual) guiGraphics.fill(dropdownX, optY, dropdownX + 2, optY + optH, 0xFFFFCC00);
                guiGraphics.drawString(this.font, "Global", dropdownX + 6, optY + 4,
                        globalHov ? 0xFFFFFF : (!individual ? 0xFFCC00 : 0xAAAAAA), false);

                // "Individual" option
                int indOptY = optY + optH;
                boolean indHov = mouseX >= dropdownX && mouseX <= dropdownX + dropdownW
                        && mouseY >= indOptY && mouseY < indOptY + optH;
                if (indHov) guiGraphics.fill(dropdownX, indOptY, dropdownX + dropdownW, indOptY + optH, 0x30BBBBBB);
                if (individual) guiGraphics.fill(dropdownX, indOptY, dropdownX + 2, indOptY + optH, 0xFFBBBBBB);
                guiGraphics.drawString(this.font, "Individual", dropdownX + 6, indOptY + 4,
                        indHov ? 0xFFFFFF : (individual ? 0xBBBBBB : 0xAAAAAA), false);

                guiGraphics.pose().popPose();
            }
        }

        @Override public void onClose() { this.minecraft.setScreen(parent); }
    }
}
