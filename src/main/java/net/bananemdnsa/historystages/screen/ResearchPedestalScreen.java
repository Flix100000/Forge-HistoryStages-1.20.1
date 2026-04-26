package net.bananemdnsa.historystages.screen;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.network.CheckDependencyPacket;
import net.bananemdnsa.historystages.network.DepositDependencyPacket;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.bananemdnsa.historystages.util.ClientIndividualStageCache;
import net.bananemdnsa.historystages.util.ClientDependencyCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class ResearchPedestalScreen extends AbstractContainerScreen<ResearchPedestalMenu> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "textures/gui/research_pedestal_gui.png");
    private static final ResourceLocation TEXTURE_DEP =
            ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "textures/gui/research_pedestal_depen-gui.png");

    // The dependency PNG is 512x512; the GUI sits at (113,138) with total size 271x166.
    // The base panel (without right box) is 176 wide — the right box is ~95px wide.
    private static final int DEP_TEXTURE_SIZE = 512;
    private static final int DEP_GUI_U = 113;
    private static final int DEP_GUI_V = 138;
    private static final int DEP_GUI_TOTAL_W = 271;
    private static final int DEP_GUI_H = 166;
    private static final int DEP_PANEL_CONTENT_OFFSET = 180; // X offset where right-box content starts
    private static final int DEP_PANEL_CONTENT_WIDTH = 88;   // Usable width inside the right box

    // Unified color palette
    private static final int COLOR_PRIMARY   = 0x404040; // Main text (stage name, labels)
    private static final int COLOR_SECONDARY = 0x707070; // Secondary info (time, searching, idle states)
    private static final int COLOR_ACCENT    = 0x2E8B57; // Active state (progress, finalizing)
    private static final int COLOR_ERROR     = 0xAA3333; // Warnings (already learned, invalid)

    private boolean hasDependencies = false;
    private Component pendingTooltip = null;

    // Scrolling state
    private float scrollAmount = 0.0f;
    private int totalContentHeight = 0;
    private CompoundTag lastDepositedNBT = null;
    private long lastDependencyCheck = 0;

    public ResearchPedestalScreen(ResearchPedestalMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
    }

    @Override
    protected void init() {
        // Determine dependency state before super.init() so imageWidth is set correctly
        checkDependencies();

        this.imageWidth = hasDependencies ? DEP_GUI_TOTAL_W : 176;
        this.imageHeight = 166;

        super.init();

        // If we have dependencies, anchor leftPos so the MAIN 176-px area stays centered.
        // This keeps all slots at their expected screen positions.
        if (hasDependencies) {
            this.leftPos = (this.width - 176) / 2;
        }
    }

    private void checkDependencies() {
        ItemStack stack = menu.getSlot(36).getItem();
        hasDependencies = false;
        if (!stack.isEmpty()) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.contains("StageResearch")) {
                String stageId = tag.getString("StageResearch");
                if (!ModItems.CREATIVE_STAGE_ID.equals(stageId)) {
                    StageEntry entry = StageManager.isIndividualStage(stageId)
                            ? StageManager.getIndividualStages().get(stageId)
                            : StageManager.getStages().get(stageId);
                    if (entry != null && entry.hasDependencies()) {
                        hasDependencies = true;
                    }
                }
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int pMouseX, int pMouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, COLOR_PRIMARY, false);

        ItemStack stack = menu.getSlot(36).getItem();

        if (!stack.isEmpty()) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.contains("StageResearch")) {
                String stageId = tag.getString("StageResearch");
                boolean isCreative = ModItems.CREATIVE_STAGE_ID.equals(stageId);
                boolean isIndividual = !isCreative && StageManager.isIndividualStage(stageId);

                String stageName;
                boolean alreadyUnlocked;

                if (isCreative) {
                    stageName = "Creative";
                    alreadyUnlocked = false;
                } else {
                    StageEntry entry;
                    if (isIndividual) {
                        entry = StageManager.getIndividualStages().get(stageId);
                        alreadyUnlocked = ClientIndividualStageCache.isStageUnlocked(stageId);
                    } else {
                        entry = StageManager.getStages().get(stageId);
                        alreadyUnlocked = ClientStageCache.isStageUnlocked(stageId);
                    }
                    stageName = entry != null ? entry.getDisplayName() : stageId;
                }

                int finishDelay = this.menu.data.get(2);

                if (alreadyUnlocked && finishDelay == 0) {
                    guiGraphics.drawString(this.font, "Research: " + stageName, 8, 18, COLOR_SECONDARY, false);
                    Component alreadyLearnedText = Component.translatable("screen.historystages.already_learned");
                    int textWidth = this.font.width(alreadyLearnedText);
                    // Center relative to the main 176-px panel
                    guiGraphics.drawString(this.font, alreadyLearnedText, (176 / 2) - (textWidth / 2), 55,
                            COLOR_ERROR, false);
                } else {
                    String prefix;
                    int nameColor;
                    if (finishDelay > 0) {
                        prefix = "Finalizing: ";
                        nameColor = COLOR_ACCENT;
                    } else {
                        prefix = "Researching: ";
                        nameColor = COLOR_SECONDARY;
                    }
                    guiGraphics.drawString(this.font, prefix + stageName, 8, 18, nameColor, false);

                    // Dependency status warning
                    if (!menu.areDependenciesMet()) {
                        Component warning = Component.translatable("screen.historystages.dependencies_not_met");
                        int ww = this.font.width(warning);
                        guiGraphics.drawString(this.font, warning, (176 / 2) - (ww / 2), 58, COLOR_ERROR, false);
                    }

                    if (tag.contains("ResearchProgress")) {
                        int currentProgress = tag.getInt("ResearchProgress");
                        int maxProgress = tag.contains("MaxProgress") ? tag.getInt("MaxProgress") : 400;

                        int percent = (int) (((double) currentProgress / maxProgress) * 100);
                        guiGraphics.drawString(this.font, "Progress: " + Math.min(100, percent) + "%",
                                48, 52, COLOR_ACCENT, false);

                        int remainingTicks = Math.max(0, maxProgress - currentProgress);
                        int remainingSeconds = (remainingTicks / 20) + (remainingTicks % 20 > 0 ? 1 : 0);
                        if (percent >= 100) remainingSeconds = 0;

                        String timeText;
                        if (remainingSeconds >= 60) {
                            int mins = remainingSeconds / 60;
                            int secs = remainingSeconds % 60;
                            timeText = "Remaining Time: " + mins + "min " + secs + "s";
                        } else {
                            timeText = "Remaining Time: " + remainingSeconds + "s";
                        }
                        guiGraphics.drawString(this.font, timeText, 48, 62, COLOR_SECONDARY, false);
                    }
                }

                // Owner label for individual stages
                if (isIndividual && tag.contains("OwnerName")) {
                    guiGraphics.drawString(this.font, "Owner: " + tag.getString("OwnerName"),
                            68, 72, COLOR_SECONDARY, false);
                }

            } else {
                guiGraphics.drawString(this.font, "Invalid Book!", 48, 28, COLOR_ERROR, false);
            }
        } else {
            int ticks = (int) (Minecraft.getInstance().level.getGameTime() / 10) % 4;
            guiGraphics.drawString(this.font, "Searching" + ".".repeat(ticks), 48, 28, COLOR_SECONDARY, false);
        }

        guiGraphics.drawString(this.font, this.playerInventoryTitle,
                this.inventoryLabelX, this.inventoryLabelY, COLOR_PRIMARY, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        // Update dependency state in case it changed (scroll inserted/removed)
        boolean prevDeps = hasDependencies;
        checkDependencies();

        // Re-initialize if state changed so imageWidth and leftPos are recalculated
        if (hasDependencies != prevDeps) {
            this.init(this.minecraft, this.width, this.height);
        }

        if (hasDependencies) {
            guiGraphics.blit(TEXTURE_DEP, this.leftPos, this.topPos,
                    DEP_GUI_U, DEP_GUI_V, DEP_GUI_TOTAL_W, DEP_GUI_H, DEP_TEXTURE_SIZE, DEP_TEXTURE_SIZE);
        } else {
            guiGraphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, 176, 166, 256, 256);
        }

        if (hasDependencies) {
            renderDependencyPanel(guiGraphics, this.leftPos + DEP_PANEL_CONTENT_OFFSET, this.topPos, pMouseX, pMouseY);
        }

        // Progress bar
        if (menu.isCrafting()) {
            int progressWidth = menu.getScaledProgress();
            int startX = this.leftPos + 57;
            int startY = this.topPos + 40;
            guiGraphics.fill(startX, startY, startX + progressWidth, startY + 7, 0xFF00FF00);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        pendingTooltip = null;
        super.render(guiGraphics, mouseX, mouseY, delta);

        ItemStack stack = menu.getSlot(36).getItem();
        if (!stack.isEmpty()) {
            CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (tag.contains("StageResearch")) {
                String stageId = tag.getString("StageResearch");

                // Cache invalidation: if deposited NBT changed, clear and force re-request
                CompoundTag currentDeposited = tag.getCompound("DepositedDependencies");
                if (lastDepositedNBT == null || !lastDepositedNBT.equals(currentDeposited)) {
                    ClientDependencyCache.remove(stageId);
                    lastDepositedNBT = currentDeposited.copy();
                    lastDependencyCheck = 0; // force immediate re-request
                }

                // Gray overlay on scroll slot for locked individual stages
                if (menu.isIndividualMode() && !ClientIndividualStageCache.isStageUnlocked(stageId)) {
                    int slotX = this.leftPos + 26;
                    int slotY = this.topPos + 35;
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(0, 0, 200);
                    guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80808080);
                    guiGraphics.pose().popPose();
                }
            }
        }

        if (pendingTooltip != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 500);
            guiGraphics.renderTooltip(this.font, pendingTooltip, mouseX, mouseY);
            guiGraphics.pose().popPose();
        } else {
            renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pScrollX, double pScrollY) {
        if (hasDependencies) {
            int panelX = this.leftPos + 176;
            if (pMouseX >= panelX && pMouseX <= panelX + 95) {
                int maxScroll = Math.max(0, totalContentHeight - 115);
                if (maxScroll > 0) {
                    scrollAmount = (float) Math.max(0, Math.min(maxScroll, scrollAmount - pScrollY * 12));
                    return true;
                }
            }
        }
        return super.mouseScrolled(pMouseX, pMouseY, pScrollX, pScrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hasDependencies && button == 0) {
            ItemStack scroll = menu.getSlot(36).getItem();
            if (!scroll.isEmpty()) {
                CompoundTag scrollTag = scroll.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                if (scrollTag.contains("StageResearch")) {
                    String stageId = scrollTag.getString("StageResearch");
                    DependencyResult result = ClientDependencyCache.get(stageId);

                    if (result != null) {
                        int groupIdx = 0;
                        for (DependencyResult.GroupResult group : result.getGroups()) {
                            for (DependencyResult.EntryResult entry : group.getEntries()) {
                                if (entry.canDeposit()) {
                                    // XP PAY button position
                                    int btnX = this.leftPos + DEP_PANEL_CONTENT_OFFSET + 5;
                                    int btnY = this.topPos + 152;
                                    if (mouseX >= btnX && mouseX <= btnX + 45
                                            && mouseY >= btnY && mouseY <= btnY + 10) {
                                        PacketHandler.sendToServer(new DepositDependencyPacket(
                                                menu.getBlockPos(), groupIdx, "XP", ""));
                                        return true;
                                    }
                                }
                            }
                            groupIdx++;
                        }
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderDependencyPanel(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        ItemStack stack = menu.getSlot(36).getItem();
        if (stack.isEmpty()) return;
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("StageResearch")) return;
        String stageId = tag.getString("StageResearch");

        DependencyResult result = ClientDependencyCache.get(stageId);

        // Poll server once per second to keep dep status fresh
        long now = System.currentTimeMillis();
        if (now - lastDependencyCheck > 1000) {
            PacketHandler.sendToServer(new CheckDependencyPacket(
                    stageId, StageManager.isIndividualStage(stageId), menu.getBlockPos()));
            lastDependencyCheck = now;
        }

        boolean isUnlocked = menu.isIndividualMode()
                ? ClientIndividualStageCache.isStageUnlocked(stageId)
                : ClientStageCache.isStageUnlocked(stageId);

        if (isUnlocked) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 100);
            Component text = Component.translatable("screen.historystages.already_learned");
            int tw = this.font.width(text);
            guiGraphics.drawString(this.font, text, x + (95 / 2) - (tw / 2), y + 70, COLOR_ERROR, false);
            guiGraphics.pose().popPose();
            return;
        }

        if (result == null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 100);
            guiGraphics.drawString(this.font, "Loading...", x + 10, y + 20, COLOR_SECONDARY, false);
            guiGraphics.pose().popPose();
            return;
        }

        // Panel title
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 100);
        String panelTitle = "Requirements";
        guiGraphics.drawString(this.font, panelTitle,
                x + (DEP_PANEL_CONTENT_WIDTH / 2) - (font.width(panelTitle) / 2), y + 8, COLOR_PRIMARY, false);

        // Deposit slot label
        guiGraphics.drawString(this.font, "Deposit:", x + 5, y + 142, COLOR_SECONDARY, false);

        // Deposit progress bar (shows while an item is being processed)
        int dDelay = menu.data.get(5);
        if (dDelay > 0) {
            ItemStack depositStack = menu.getSlot(37).getItem();
            if (!depositStack.isEmpty()) {
                ResourceLocation dRl = BuiltInRegistries.ITEM.getKey(depositStack.getItem());
                String dId = dRl != null ? dRl.toString() : "";
                boolean isNeeded = result.getGroups().stream()
                        .flatMap(g -> g.getEntries().stream())
                        .anyMatch(e -> "item".equals(e.getType()) && dId.equals(e.getId()) && !e.isFulfilled());
                if (isNeeded) {
                    int barWidth = 16;
                    int filledWidth = (int) ((double) dDelay / 20.0 * barWidth);
                    // Position: absolute screen coords for the deposit slot (slot 37 is at 246,142 relative to leftPos)
                    int bx = this.leftPos + 246;
                    int by = this.topPos + 142 + 18;
                    guiGraphics.fill(bx, by, bx + barWidth, by + 2, 0xFF404040);
                    guiGraphics.fill(bx, by, bx + filledWidth, by + 2, 0xFFAAAAAA);
                }
            }
        }
        guiGraphics.pose().popPose();

        // Clipped scrollable content area
        int clipX = x - 2;
        int clipY = y + 20;
        int clipW = DEP_PANEL_CONTENT_WIDTH + 10;
        int clipH = 115; // Space available before the deposit slot row

        guiGraphics.enableScissor(clipX, clipY, clipX + clipW, clipY + clipH);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y + 20 - scrollAmount, 100);

        int currentY = 0;
        int groupIdx = 0;
        for (DependencyResult.GroupResult group : result.getGroups()) {
            if (result.getGroups().size() > 1) {
                guiGraphics.drawString(this.font, "Group " + (groupIdx + 1), 5, currentY, 0x606060, false);
                currentY += 12;
            }

            for (DependencyResult.EntryResult entry : group.getEntries()) {
                boolean fulfilled = entry.isFulfilled();
                int bgColor     = fulfilled ? 0x202E8B57 : 0x20AA3333;
                int borderColor = fulfilled ? 0x802E8B57 : 0x80AA3333;

                int cardH = "item".equals(entry.getType()) ? 22 : 15;

                // Card background + left indicator stripe
                guiGraphics.fill(-2, currentY - 1, DEP_PANEL_CONTENT_WIDTH + 5, currentY + cardH - 1, bgColor);
                guiGraphics.fill(-2, currentY - 1, -1, currentY + cardH - 1, borderColor);

                if ("item".equals(entry.getType())) {
                    ResourceLocation itemRl = ResourceLocation.tryParse(entry.getId());
                    if (itemRl != null) {
                        var mcItem = BuiltInRegistries.ITEM.get(itemRl);
                        if (mcItem != null) {
                            guiGraphics.fill(2, currentY, 20, currentY + 18, 0x40000000);
                            guiGraphics.renderItem(new ItemStack(mcItem), 3, currentY + 1);
                        }
                        String progressText = entry.getCurrent() + "/" + entry.getRequired();
                        guiGraphics.drawString(this.font, progressText, 25, currentY + 5,
                                fulfilled ? COLOR_ACCENT : COLOR_PRIMARY, false);

                        // Hover tooltip (use scrolled screen coords)
                        if (mouseX >= x && mouseX <= x + DEP_PANEL_CONTENT_WIDTH
                                && mouseY >= y + 20 + currentY - scrollAmount
                                && mouseY < y + 20 + currentY + cardH - scrollAmount
                                && mouseY >= clipY && mouseY <= clipY + clipH) {
                            pendingTooltip = Component.literal(entry.getDescription())
                                    .withStyle(ChatFormatting.GRAY);
                        }
                    }
                } else if ("xp_level".equals(entry.getType())) {
                    guiGraphics.renderItem(new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE), 2, currentY - 1);
                    String text = entry.getDescription();
                    if (font.width(text) > 65)
                        text = font.plainSubstrByWidth(text, 60) + "..";
                    guiGraphics.drawString(this.font, text, 22, currentY + 3,
                            fulfilled ? COLOR_ACCENT : COLOR_PRIMARY, false);

                    if (mouseX >= x && mouseX <= x + DEP_PANEL_CONTENT_WIDTH
                            && mouseY >= y + 20 + currentY - scrollAmount
                            && mouseY < y + 20 + currentY + cardH - scrollAmount
                            && mouseY >= clipY && mouseY <= clipY + clipH) {
                        pendingTooltip = Component.literal(entry.getDescription())
                                .withStyle(ChatFormatting.GRAY);
                    }
                } else {
                    String desc = entry.getDescription();
                    if (this.font.width(desc) > DEP_PANEL_CONTENT_WIDTH - 5) {
                        desc = this.font.plainSubstrByWidth(desc, DEP_PANEL_CONTENT_WIDTH - 15) + "...";
                    }
                    guiGraphics.drawString(this.font, (fulfilled ? "§a" : "§7") + desc, 5, currentY + 2,
                            COLOR_PRIMARY, false);

                    if (mouseX >= x && mouseX <= x + DEP_PANEL_CONTENT_WIDTH
                            && mouseY >= y + 20 + currentY - scrollAmount
                            && mouseY < y + 20 + currentY + cardH - scrollAmount
                            && mouseY >= clipY && mouseY <= clipY + clipH) {
                        pendingTooltip = Component.literal(entry.getDescription())
                                .withStyle(ChatFormatting.GRAY);
                    }
                }
                currentY += cardH + 2;
            }
            groupIdx++;
            currentY += 5;
        }
        totalContentHeight = currentY;

        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();

        // Suppress tooltip when hovering directly over the deposit slot
        if (mouseX >= this.leftPos + 245 && mouseX <= this.leftPos + 263
                && mouseY >= this.topPos + 141 && mouseY <= this.topPos + 159) {
            pendingTooltip = null;
        }

        // Scrollbar
        if (totalContentHeight > clipH) {
            int barX = x + DEP_PANEL_CONTENT_WIDTH + 6;
            int barY = y + 22;
            int barH = clipH - 4;
            guiGraphics.fill(barX, barY, barX + 2, barY + barH, 0x40000000);

            float scrollPercent = scrollAmount / (float) (totalContentHeight - clipH);
            int thumbH = Math.max(10, (int) (barH * ((float) clipH / totalContentHeight)));
            int thumbY = (int) (scrollPercent * (barH - thumbH));
            guiGraphics.fill(barX, barY + thumbY, barX + 2, barY + thumbY + thumbH, 0x80FFFFFF);
        }

        // XP PAY button (shown when an XP deposit is needed and not yet fulfilled)
        for (DependencyResult.GroupResult group : result.getGroups()) {
            for (DependencyResult.EntryResult entry : group.getEntries()) {
                if (entry.canDeposit()) {
                    int btnX = x + 5;
                    int btnY = y + 152;
                    boolean hovered = mouseX >= btnX && mouseX <= btnX + 45
                            && mouseY >= btnY && mouseY <= btnY + 10;
                    guiGraphics.fill(btnX, btnY, btnX + 45, btnY + 10, hovered ? 0xFF666666 : 0xFF333333);
                    guiGraphics.drawString(this.font, "PAY XP", btnX + 4, btnY + 1, 0xFFFFFFFF, false);
                    return; // Only show one button
                }
            }
        }
    }
}
