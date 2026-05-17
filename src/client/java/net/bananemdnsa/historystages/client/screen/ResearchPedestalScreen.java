package net.bananemdnsa.historystages.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.network.CheckDependencyPacket;
import net.bananemdnsa.historystages.network.DepositDependencyPacket;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.screen.ResearchPedestalMenu;
import net.bananemdnsa.historystages.util.ClientDependencyCache;
import net.bananemdnsa.historystages.util.ClientIndividualStageCache;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public class ResearchPedestalScreen extends AbstractContainerScreen<ResearchPedestalMenu> {
    private static final ResourceLocation TEXTURE = HistoryStages.id("textures/gui/research_pedestal_gui.png");
    private static final int DEP_GUI_TOTAL_W = 271;
    private static final int DEP_PANEL_CONTENT_OFFSET = 180;
    private static final int DEP_PANEL_CONTENT_WIDTH = 88;

    private static final int COLOR_PRIMARY = 0x404040;
    private static final int COLOR_SECONDARY = 0x707070;
    private static final int COLOR_ACCENT = 0x2E8B57;
    private static final int COLOR_ERROR = 0xAA3333;

    private boolean hasDependencies;
    private Component pendingTooltip;
    private float scrollAmount;
    private int totalContentHeight;
    private CompoundTag lastDepositedNBT;
    private long lastDependencyCheck;

    public ResearchPedestalScreen(ResearchPedestalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        checkDependencies();
        this.imageWidth = hasDependencies ? DEP_GUI_TOTAL_W : 176;
        this.imageHeight = 166;
        super.init();
        if (hasDependencies) {
            this.leftPos = (this.width - 176) / 2;
        }
    }

    private void checkDependencies() {
        ItemStack stack = menu.getSlot(36).getItem();
        hasDependencies = false;
        if (!Config.COMMON.showDependencyScreenInPedestal || stack.isEmpty()) {
            return;
        }
        CompoundTag tag = customData(stack);
        if (!tag.contains("StageResearch")) {
            return;
        }
        String stageId = tag.getString("StageResearch");
        if (ModItems.CREATIVE_STAGE_ID.equals(stageId)) {
            return;
        }
        StageEntry entry = StageManager.isIndividualStage(stageId)
                ? StageManager.getIndividualStages().get(stageId)
                : StageManager.getStages().get(stageId);
        hasDependencies = entry != null && entry.hasDependencies();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        pendingTooltip = null;
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        ItemStack stack = menu.getSlot(36).getItem();
        CompoundTag tag = customData(stack);
        if (!stack.isEmpty() && tag.contains("StageResearch")) {
            String stageId = tag.getString("StageResearch");
            CompoundTag deposited = tag.contains("DepositedDependencies")
                    ? tag.getCompound("DepositedDependencies")
                    : new CompoundTag();
            if (lastDepositedNBT == null || !lastDepositedNBT.equals(deposited)) {
                ClientDependencyCache.remove(stageId);
                lastDepositedNBT = deposited.copy();
                lastDependencyCheck = 0L;
            }

            if (menu.isIndividualMode() && !ClientIndividualStageCache.isStageUnlocked(stageId)) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, 0, 200);
                guiGraphics.fill(this.leftPos + 26, this.topPos + 35, this.leftPos + 42, this.topPos + 51, 0x80808080);
                guiGraphics.pose().popPose();
            }
        }

        if (pendingTooltip != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 500);
            guiGraphics.renderTooltip(this.font, pendingTooltip, mouseX, mouseY);
            guiGraphics.pose().popPose();
        } else {
            this.renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        boolean previous = hasDependencies;
        checkDependencies();
        if (previous != hasDependencies) {
            this.init(this.minecraft, this.width, this.height);
        }

        if (hasDependencies) {
            RenderSystem.setShaderTexture(0, TEXTURE);
            guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, 176, 166, 256, 256);
            renderDependencyPanelBackground(guiGraphics);
            renderDependencyPanel(guiGraphics, leftPos + DEP_PANEL_CONTENT_OFFSET, topPos, mouseX, mouseY);
        } else {
            RenderSystem.setShaderTexture(0, TEXTURE);
            guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, 176, 166, 256, 256);
        }

        if (menu.isCrafting()) {
            int progressWidth = menu.getScaledProgress();
            guiGraphics.fill(leftPos + 57, topPos + 40, leftPos + 57 + progressWidth, topPos + 47, 0xFF00FF00);
        }
    }

    private void renderDependencyPanelBackground(GuiGraphics guiGraphics) {
        int panelX = leftPos + 176;
        int panelY = topPos;
        int panelW = DEP_GUI_TOTAL_W - 176;
        int panelH = 166;

        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFFC6C6C6);
        guiGraphics.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFFFFFFFF);
        guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelH, 0xFFFFFFFF);
        guiGraphics.fill(panelX + panelW - 1, panelY, panelX + panelW, panelY + panelH, 0xFF555555);
        guiGraphics.fill(panelX, panelY + panelH - 1, panelX + panelW, panelY + panelH, 0xFF555555);
        guiGraphics.fill(panelX, panelY + 136, panelX + panelW, panelY + 137, 0xFF777777);

        int slotX = leftPos + 246;
        int slotY = topPos + 142;
        guiGraphics.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, 0xFF555555);
        guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0xFF8B8B8B);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, COLOR_PRIMARY, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                COLOR_PRIMARY, false);

        ItemStack scroll = menu.getSlot(36).getItem();
        CompoundTag tag = customData(scroll);
        if (isResearchScroll(scroll)) {
            String stageId = tag.getString("StageResearch");
            boolean creative = ModItems.CREATIVE_STAGE_ID.equals(stageId);
            boolean individual = !creative && StageManager.isIndividualStage(stageId);
            String stageName = resolveStageName(stageId, creative, individual);
            boolean alreadyUnlocked = !creative && (individual
                    ? ClientIndividualStageCache.isStageUnlocked(stageId)
                    : ClientStageCache.isStageUnlocked(stageId));
            int finishDelay = menu.data.get(2);

            if (alreadyUnlocked && finishDelay == 0) {
                guiGraphics.drawString(this.font, "Research: " + stageName, 8, 18, COLOR_SECONDARY, false);
                Component alreadyLearned = Component.translatable("screen.historystages.already_learned");
                guiGraphics.drawString(this.font, alreadyLearned, 88 - this.font.width(alreadyLearned) / 2, 55,
                        COLOR_ERROR, false);
            } else {
                String prefix = finishDelay > 0 ? "Finalizing: " : "Researching: ";
                guiGraphics.drawString(this.font, prefix + stageName, 8, 18,
                        finishDelay > 0 ? COLOR_ACCENT : COLOR_SECONDARY, false);

                if (!menu.areDependenciesMet()) {
                    Component warning = Component.translatable("screen.historystages.dependencies_not_met");
                    guiGraphics.drawString(this.font, warning, 88 - this.font.width(warning) / 2, 58,
                            COLOR_ERROR, false);
                }

                if (tag.contains("ResearchProgress")) {
                    int progress = tag.getInt("ResearchProgress");
                    int maxProgress = tag.contains("MaxProgress") ? Math.max(1, tag.getInt("MaxProgress")) : 400;
                    int percent = (int) Math.min(100, ((double) progress / maxProgress) * 100);
                    guiGraphics.drawString(this.font, "Progress: " + percent + "%", 48, 52, COLOR_ACCENT, false);

                    int remainingTicks = Math.max(0, maxProgress - progress);
                    int remainingSeconds = (remainingTicks / 20) + (remainingTicks % 20 > 0 ? 1 : 0);
                    if (percent >= 100) {
                        remainingSeconds = 0;
                    }
                    String remainingText = remainingSeconds >= 60
                            ? "Remaining Time: " + (remainingSeconds / 60) + "min " + (remainingSeconds % 60) + "s"
                            : "Remaining Time: " + remainingSeconds + "s";
                    guiGraphics.drawString(this.font, remainingText, 48, 62, COLOR_SECONDARY, false);
                }
            }

            if (individual && tag.contains("OwnerName")) {
                guiGraphics.drawString(this.font, "Owner: " + tag.getString("OwnerName"), 68, 72, COLOR_SECONDARY, false);
            }
        } else if (!scroll.isEmpty()) {
            guiGraphics.drawString(this.font, "Invalid Book!", 48, 28, COLOR_ERROR, false);
        } else {
            int ticks = (int) (this.minecraft.level != null ? (this.minecraft.level.getGameTime() / 10) % 4 : 0);
            guiGraphics.drawString(this.font, "Searching" + ".".repeat(ticks), 48, 28, COLOR_SECONDARY, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (hasDependencies && mouseX >= leftPos + 176 && mouseX <= leftPos + 271) {
            int maxScroll = Math.max(0, totalContentHeight - 115);
            if (maxScroll > 0) {
                scrollAmount = (float) Math.max(0, Math.min(maxScroll, scrollAmount - deltaY * 12));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hasDependencies && button == 0) {
            ItemStack scroll = menu.getSlot(36).getItem();
            CompoundTag tag = customData(scroll);
            if (tag.contains("StageResearch")) {
                DependencyResult result = ClientDependencyCache.get(tag.getString("StageResearch"));
                if (result != null) {
                    int groupIndex = 0;
                    for (DependencyResult.GroupResult group : result.getGroups()) {
                        for (DependencyResult.EntryResult entry : group.getEntries()) {
                            if (entry.canDeposit()) {
                                int buttonX = leftPos + 185;
                                int buttonY = topPos + 152;
                                if (mouseX >= buttonX && mouseX <= buttonX + 45
                                        && mouseY >= buttonY && mouseY <= buttonY + 10) {
                                    PacketHandler.sendToServer(new DepositDependencyPacket(menu.getBlockPos(),
                                            groupIndex, "XP", ""));
                                    return true;
                                }
                            }
                        }
                        groupIndex++;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderDependencyPanel(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        ItemStack stack = menu.getSlot(36).getItem();
        CompoundTag tag = customData(stack);
        if (!tag.contains("StageResearch")) {
            return;
        }
        String stageId = tag.getString("StageResearch");

        DependencyResult result = ClientDependencyCache.get(stageId);
        long now = System.currentTimeMillis();
        if (now - lastDependencyCheck > 1000L) {
            PacketHandler.sendToServer(new CheckDependencyPacket(stageId, StageManager.isIndividualStage(stageId),
                    menu.getBlockPos()));
            lastDependencyCheck = now;
        }

        boolean unlocked = menu.isIndividualMode()
                ? ClientIndividualStageCache.isStageUnlocked(stageId)
                : ClientStageCache.isStageUnlocked(stageId);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 100);
        if (unlocked) {
            Component text = Component.translatable("screen.historystages.already_learned");
            guiGraphics.drawString(this.font, text, x + 47 - this.font.width(text) / 2, y + 70, COLOR_ERROR, false);
            guiGraphics.pose().popPose();
            return;
        }

        if (result == null) {
            guiGraphics.drawString(this.font, "Loading...", x + 10, y + 20, COLOR_SECONDARY, false);
            guiGraphics.pose().popPose();
            return;
        }

        String title = "Requirements";
        guiGraphics.drawString(this.font, title, x + DEP_PANEL_CONTENT_WIDTH / 2 - this.font.width(title) / 2,
                y + 8, COLOR_PRIMARY, false);
        guiGraphics.drawString(this.font, "Deposit:", x + 5, y + 142, COLOR_SECONDARY, false);
        renderDepositProgress(guiGraphics, result);
        guiGraphics.pose().popPose();

        int clipX = x - 2;
        int clipY = y + 20;
        int clipH = 115;
        guiGraphics.enableScissor(clipX, clipY, clipX + DEP_PANEL_CONTENT_WIDTH + 4, clipY + clipH);
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y + 20 - scrollAmount, 100);

        int currentY = 0;
        int groupIndex = 0;
        for (DependencyResult.GroupResult group : result.getGroups()) {
            if (result.getGroups().size() > 1) {
                guiGraphics.drawString(this.font, "Group " + (groupIndex + 1), 5, currentY, 0x606060, false);
                currentY += 12;
            }
            for (DependencyResult.EntryResult entry : group.getEntries()) {
                int cardHeight = "item".equals(entry.getType()) ? 22 : 15;
                guiGraphics.fill(2, currentY - 1, DEP_PANEL_CONTENT_WIDTH - 2, currentY + cardHeight - 1,
                        entry.isFulfilled() ? 0x202E8B57 : 0x20AA3333);
                renderDependencyEntry(guiGraphics, entry, currentY);
                if (mouseX >= x && mouseX <= x + DEP_PANEL_CONTENT_WIDTH
                        && mouseY >= y + 20 + currentY - scrollAmount
                        && mouseY < y + 20 + currentY + cardHeight - scrollAmount
                        && mouseY >= y + 20 && mouseY <= y + 20 + clipH) {
                    pendingTooltip = Component.literal(entry.getDescription()).withStyle(ChatFormatting.GRAY);
                }
                currentY += cardHeight + 2;
            }
            groupIndex++;
            currentY += 5;
        }
        totalContentHeight = currentY;
        guiGraphics.pose().popPose();
        guiGraphics.disableScissor();

        if (mouseX >= leftPos + 245 && mouseX <= leftPos + 265 && mouseY >= topPos + 141 && mouseY <= topPos + 161) {
            pendingTooltip = null;
        }

        renderDependencyScrollbar(guiGraphics, x, y, clipH);
        renderPayXpButton(guiGraphics, result, x, y, mouseX, mouseY);
    }

    private void renderDependencyEntry(GuiGraphics guiGraphics, DependencyResult.EntryResult entry, int currentY) {
        if ("item".equals(entry.getType())) {
            ItemStack itemIcon = itemIcon(entry.getId());
            guiGraphics.fill(2, currentY, 20, currentY + 18, 0x40000000);
            guiGraphics.renderItem(itemIcon, 3, currentY + 1);
            String progress = entry.getCurrent() + "/" + entry.getRequired();
            guiGraphics.drawString(this.font, progress, 25, currentY + 5,
                    entry.isFulfilled() ? COLOR_ACCENT : COLOR_PRIMARY, false);
            return;
        }

        if ("xp_level".equals(entry.getType())) {
            guiGraphics.renderItem(new ItemStack(Items.EXPERIENCE_BOTTLE), 2, currentY - 1);
            String text = trim(entry.getDescription(), 60);
            guiGraphics.drawString(this.font, text, 22, currentY + 3,
                    entry.isFulfilled() ? COLOR_ACCENT : COLOR_PRIMARY, false);
            return;
        }

        String text = trim(entry.getDescription(), DEP_PANEL_CONTENT_WIDTH - 15);
        guiGraphics.drawString(this.font, (entry.isFulfilled() ? "\u00A7a" : "\u00A77") + text, 5, currentY + 2,
                COLOR_PRIMARY, false);
    }

    private void renderDepositProgress(GuiGraphics guiGraphics, DependencyResult result) {
        int delay = menu.data.get(5);
        if (delay <= 0) {
            return;
        }
        ItemStack depositStack = menu.getSlot(37).getItem();
        if (depositStack.isEmpty()) {
            return;
        }
        String depositId = BuiltInRegistries.ITEM.getKey(depositStack.getItem()).toString();
        boolean needed = result.getGroups().stream()
                .flatMap(group -> group.getEntries().stream())
                .anyMatch(entry -> "item".equals(entry.getType())
                        && depositId.equals(entry.getId()) && !entry.isFulfilled());
        if (!needed) {
            return;
        }
        int filledWidth = (int) ((double) delay / 20.0D * 16);
        int x = leftPos + 246;
        int y = topPos + 160;
        guiGraphics.fill(x, y, x + 16, y + 2, 0xFF404040);
        guiGraphics.fill(x, y, x + filledWidth, y + 2, 0xFFAAAAAA);
    }

    private void renderDependencyScrollbar(GuiGraphics guiGraphics, int x, int y, int clipH) {
        if (totalContentHeight <= clipH) {
            return;
        }
        int barX = x + DEP_PANEL_CONTENT_WIDTH + 6;
        int barY = y + 22;
        int barHeight = clipH - 4;
        guiGraphics.fill(barX, barY, barX + 2, barY + barHeight, 0x40000000);
        float scrollPercent = scrollAmount / (float) (totalContentHeight - clipH);
        int thumbHeight = Math.max(10, (int) (barHeight * (clipH / (float) totalContentHeight)));
        int thumbY = (int) (scrollPercent * (barHeight - thumbHeight));
        guiGraphics.fill(barX, barY + thumbY, barX + 2, barY + thumbY + thumbHeight, 0x80FFFFFF);
    }

    private void renderPayXpButton(GuiGraphics guiGraphics, DependencyResult result, int x, int y, int mouseX, int mouseY) {
        for (DependencyResult.GroupResult group : result.getGroups()) {
            for (DependencyResult.EntryResult entry : group.getEntries()) {
                if (entry.canDeposit()) {
                    int buttonX = x + 5;
                    int buttonY = y + 152;
                    boolean hovered = mouseX >= buttonX && mouseX <= buttonX + 45
                            && mouseY >= buttonY && mouseY <= buttonY + 10;
                    guiGraphics.fill(buttonX, buttonY, buttonX + 45, buttonY + 10,
                            hovered ? 0xFF666666 : 0xFF333333);
                    guiGraphics.drawString(this.font, "PAY XP", buttonX + 4, buttonY + 1, 0xFFFFFFFF, false);
                    return;
                }
            }
        }
    }

    private ItemStack itemIcon(String id) {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(id);
        if (resourceLocation == null) {
            return new ItemStack(Items.BARRIER);
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(resourceLocation));
    }

    private String trim(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(1, width - this.font.width("..."))) + "...";
    }

    private boolean isResearchScroll(ItemStack stack) {
        if (stack.isEmpty() || !(stack.is(ModItems.RESEARCH_SCROLL) || stack.is(ModItems.CREATIVE_SCROLL))) {
            return false;
        }
        CompoundTag tag = customData(stack);
        if (!tag.contains("StageResearch")) {
            return false;
        }
        String stageId = tag.getString("StageResearch");
        return ModItems.CREATIVE_STAGE_ID.equals(stageId)
                || StageManager.getStages().containsKey(stageId)
                || StageManager.getIndividualStages().containsKey(stageId);
    }

    private String resolveStageName(String stageId, boolean creative, boolean individual) {
        if (creative) {
            return "Creative";
        }
        StageEntry entry = individual ? StageManager.getIndividualStages().get(stageId) : StageManager.getStages().get(stageId);
        if (entry == null) {
            entry = StageManager.getStages().get(stageId);
        }
        return entry != null ? entry.getDisplayName() : stageId;
    }

    private static CompoundTag customData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
}
