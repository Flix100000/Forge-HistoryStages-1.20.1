package net.bananemdnsa.historystages.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.bananemdnsa.historystages.util.ClientIndividualStageCache;
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

    // Unified color palette
    private static final int COLOR_PRIMARY = 0x404040;    // Main text (stage name, labels)
    private static final int COLOR_SECONDARY = 0x707070;  // Secondary info (time, searching, idle states)
    private static final int COLOR_ACCENT = 0x2E8B57;     // Active state (progress, finalizing)
    private static final int COLOR_ERROR = 0xAA3333;      // Warnings (already learned, invalid)

    public ResearchPedestalScreen(ResearchPedestalMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
        super(pMenu, pPlayerInventory, pTitle);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int pMouseX, int pMouseY) {
        // Line 1: "Research Pedestal"
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, COLOR_PRIMARY, false);

        ItemStack stack = menu.getSlot(36).getItem();

        if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains("StageResearch")) {
            String stageId = stack.getTag().getString("StageResearch");
            boolean isCreative = ModItems.CREATIVE_STAGE_ID.equals(stageId);
            boolean isIndividual = !isCreative && StageManager.isIndividualStage(stageId);

            // Resolve stage entry and display name
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

            // Line 2: Stage name with status prefix
            if (alreadyUnlocked && finishDelay == 0) {
                guiGraphics.drawString(this.font, "Research: " + stageName, 8, 18, COLOR_SECONDARY, false);

                Component alreadyLearnedText = Component.translatable("screen.historystages.already_learned");
                int textWidth = this.font.width(alreadyLearnedText);
                guiGraphics.drawString(this.font, alreadyLearnedText, (this.imageWidth / 2) - (textWidth / 2), 55, COLOR_ERROR, false);
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

                // Lines 3-4: Progress + Time
                if (stack.getTag().contains("ResearchProgress")) {
                    int currentProgress = stack.getTag().getInt("ResearchProgress");
                    int maxProgress = stack.getTag().contains("MaxProgress") ? stack.getTag().getInt("MaxProgress") : 400;

                    int percent = (int) (((double) currentProgress / maxProgress) * 100);
                    guiGraphics.drawString(this.font, "Progress: " + Math.min(100, percent) + "%", 48, 52, COLOR_ACCENT, false);

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

            // Line 5: Owner (only for individual stages)
            if (isIndividual && stack.getTag().contains("OwnerName")) {
                String ownerName = stack.getTag().getString("OwnerName");
                guiGraphics.drawString(this.font, "Owner: " + ownerName, 68, 72, COLOR_SECONDARY, false);
            }

        } else if (!stack.isEmpty()) {
            // Invalid book - above progress bar
            guiGraphics.drawString(this.font, "Invalid Book!", 48, 28, COLOR_ERROR, false);
        } else {
            // Empty slot - searching animation above progress bar
            int ticks = (int) (Minecraft.getInstance().level.getGameTime() / 10) % 4;
            guiGraphics.drawString(this.font, "Searching" + ".".repeat(ticks), 48, 28, COLOR_SECONDARY, false);
        }

        // Inventory label
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, COLOR_PRIMARY, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);

        // Progress bar
        if (menu.isCrafting()) {
            int progressWidth = menu.getScaledProgress();
            int startX = x + 57;
            int startY = y + 40;
            int barHeight = 7;
            guiGraphics.fill(startX, startY, startX + progressWidth, startY + barHeight, 0xFF00FF00);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, delta);

        // Gray overlay on individual scroll slot - always visible when scroll is locked
        if (menu.isIndividualMode()) {
            ItemStack stack = menu.getSlot(36).getItem();
            if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains("StageResearch")) {
                String stageId = stack.getTag().getString("StageResearch");
                if (!ClientIndividualStageCache.isStageUnlocked(stageId)) {
                    int x = (width - imageWidth) / 2;
                    int y = (height - imageHeight) / 2;
                    int slotX = x + 26;
                    int slotY = y + 35;
                    guiGraphics.pose().pushPose();
                    guiGraphics.pose().translate(0, 0, 200);
                    guiGraphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x80808080);
                    guiGraphics.pose().popPose();
                }
            }
        }

        renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
