package net.bananemdnsa.historystages.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.screen.ResearchPedestalMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

public class ResearchPedestalScreen extends AbstractContainerScreen<ResearchPedestalMenu> {
    private static final ResourceLocation TEXTURE = HistoryStages.id("textures/gui/research_pedestal_gui.png");

    public ResearchPedestalScreen(ResearchPedestalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        int progressWidth = menu.getScaledProgress();
        if (progressWidth > 0) {
            guiGraphics.fill(leftPos + 57, topPos + 40, leftPos + 57 + progressWidth, topPos + 47, 0xFF00AA55);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);

        ItemStack scroll = menu.getSlot(36).getItem();
        if (isValidResearchScroll(scroll)) {
            String stageName = "Unknown";
            var tag = scroll.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            boolean creative = false;
            boolean individual = false;
            if (tag.contains("StageResearch")) {
                String stageId = tag.getString("StageResearch");
                if (ModItems.CREATIVE_STAGE_ID.equals(stageId)) {
                    stageName = "Creative";
                    creative = true;
                } else {
                    individual = StageManager.isIndividualStage(stageId);
                    StageEntry entry = StageManager.getStages().get(stageId);
                    if (entry == null) {
                        entry = StageManager.getIndividualStages().get(stageId);
                    }
                    stageName = entry != null ? entry.getDisplayName() : stageId;
                }
            }
            int finishDelay = menu.data.get(2);
            String prefix = finishDelay > 0 ? "Finalizing: " : "Researching: ";
            guiGraphics.drawString(this.font, prefix + stageName, 8, 18, finishDelay > 0 ? 0x2E8B57 : 0x606060, false);
            if (tag.contains("OwnerName")) {
                guiGraphics.drawString(this.font, "Owner: " + tag.getString("OwnerName"), 68, 72, 0x606060, false);
            }
            if (!menu.areDependenciesMet()) {
                guiGraphics.drawString(this.font, Component.translatable("screen.historystages.dependencies_not_met"), 48, 58, 0xAA3333, false);
            }
            if (tag.contains("ResearchProgress")) {
                int progress = tag.getInt("ResearchProgress");
                int maxProgress = tag.contains("MaxProgress") ? Math.max(1, tag.getInt("MaxProgress")) : 400;
                int percent = (int) Math.min(100, ((double) progress / maxProgress) * 100);
                guiGraphics.drawString(this.font, "Progress: " + percent + "%", 48, 52, 0x2E8B57, false);

                int remainingTicks = Math.max(0, maxProgress - progress);
                int remainingSeconds = (remainingTicks / 20) + (remainingTicks % 20 > 0 ? 1 : 0);
                if (percent >= 100) {
                    remainingSeconds = 0;
                }
                String remainingText = remainingSeconds >= 60
                        ? "Remaining Time: " + (remainingSeconds / 60) + "min " + (remainingSeconds % 60) + "s"
                        : "Remaining Time: " + remainingSeconds + "s";
                guiGraphics.drawString(this.font, remainingText, 48, 62, 0x606060, false);
            } else if (creative) {
                guiGraphics.drawString(this.font, "Progress: 100%", 48, 52, 0x2E8B57, false);
            }
            guiGraphics.drawString(this.font, individual ? "Individual" : "Global", 132, 18, 0x606060, false);
        } else if (!scroll.isEmpty()) {
            guiGraphics.drawString(this.font, "Invalid Book!", 48, 28, 0xAA3333, false);
        } else {
            int ticks = (int) (this.minecraft.level != null ? (this.minecraft.level.getGameTime() / 10) % 4 : 0);
            guiGraphics.drawString(this.font, "Searching" + ".".repeat(ticks), 48, 28, 0x606060, false);
        }
    }

    private boolean isValidResearchScroll(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (!(stack.is(ModItems.RESEARCH_SCROLL) || stack.is(ModItems.CREATIVE_SCROLL))) {
            return false;
        }
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("StageResearch")) {
            return false;
        }
        String stageId = tag.getString("StageResearch");
        return ModItems.CREATIVE_STAGE_ID.equals(stageId)
                || StageManager.getStages().containsKey(stageId)
                || StageManager.getIndividualStages().containsKey(stageId);
    }
}
