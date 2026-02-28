package net.bananemdnsa.historystages.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bananemdnsa.historystages.HistoryStages;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class ResearchPedestalScreen extends AbstractContainerScreen<ResearchPedestalMenu> {
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(HistoryStages.MOD_ID, "textures/gui/research_pedestal_gui.png");

    public ResearchPedestalScreen(ResearchPedestalMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int pMouseX, int pMouseY) {
        // 1. Titel oben links
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 4210752, false);

        // 2. Dynamischer Forschungs-Status
        ItemStack stack = menu.getSlot(36).getItem();
        String displayTitle = "";
        int textColor = 0x404040;

        if (!stack.isEmpty()) {
            if (stack.hasTag() && stack.getTag().contains("StageResearch")) {
                String stageId = stack.getTag().getString("StageResearch");
                var entry = net.bananemdnsa.historystages.data.StageManager.getStages().get(stageId);
                boolean alreadyUnlocked = net.bananemdnsa.historystages.util.ClientStageCache.isStageUnlocked(stageId);
                int finishDelay = this.menu.data.get(2);

                // FALL A: Stage ist bereits bekannt
                if (alreadyUnlocked && finishDelay == 0) {
                    displayTitle = "Research: " + (entry != null ? entry.getDisplayName() : stageId);
                    textColor = 0x707070;

                    Component alreadyLearnedText = Component.translatable("screen.historystages.already_learned");
                    int textWidth = this.font.width(alreadyLearnedText);
                    guiGraphics.drawString(this.font, alreadyLearnedText, (this.imageWidth / 2) - (textWidth / 2), 55, 0xFF5555, false);
                }
                // FALL B: Stage wird gerade erforscht (oder ist in der Finalisierung)
                else {
                    if (finishDelay > 0) {
                        displayTitle = "Finalizing: " + (entry != null ? entry.getDisplayName() : stageId);
                        textColor = 0x2E8B57;
                    } else {
                        displayTitle = "Researching: " + (entry != null ? entry.getDisplayName() : stageId);
                        textColor = 0x707070;
                    }

                    // Fortschritt und Zeit nur anzeigen, wenn nicht "Already Learned" steht
                    if (stack.getTag().contains("ResearchProgress")) {
                        int currentProgress = stack.getTag().getInt("ResearchProgress");
                        int maxProgress = stack.getTag().contains("MaxProgress") ? stack.getTag().getInt("MaxProgress") : 400;

                        int percent = (int) (((double) currentProgress / maxProgress) * 100);
                        String progressText = "Progress: " + Math.min(100, percent) + "%";
                        guiGraphics.drawString(this.font, progressText, 75, 52, 0x2E8B57, false);

                        int remainingTicks = Math.max(0, maxProgress - currentProgress);
                        int remainingSeconds = (remainingTicks / 20) + (remainingTicks % 20 > 0 ? 1 : 0);
                        if (percent >= 100) remainingSeconds = 0;

                        String timeText = "Remaining Time: " + remainingSeconds + "s";
                        guiGraphics.drawString(this.font, timeText, 65, 62, 0x707070, false);
                    }
                }
            } else {
                displayTitle = "Invalid Book!";
                textColor = 0xFF5555;
            }
        } else {
            int ticks = (int) (Minecraft.getInstance().level.getGameTime() / 10) % 4;
            displayTitle = "Searching" + ".".repeat(ticks);
            textColor = 0x707070;
        }

        guiGraphics.drawString(this.font, displayTitle, 48, 25, textColor, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 4210752, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

        // Fortschrittsbalken nur zeichnen, wenn wirklich gearbeitet wird
        if (menu.isCrafting()) {
            int progressWidth = menu.getScaledProgress();
            int startX = x + 57;
            int startY = y + 40;
            int barHeight = 7;

            // Zeichnet einen grünen Balken
            guiGraphics.fill(startX, startY, startX + progressWidth, startY + barHeight, 0xFF00FF00);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, delta);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}