package net.bananemdnsa.historystages.screen;

import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class ResearchPedestalScreen extends AbstractContainerScreen<ResearchPedestalMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(HistoryStages.MOD_ID,
            "textures/gui/research_pedestal_gui.png");
    private static final ResourceLocation TEXTURE_DEP = new ResourceLocation(HistoryStages.MOD_ID,
            "textures/gui/research_pedestal_depen-gui.png");

    // The dependency PNG is 512x512; the GUI sits at (113,140) with total size
    // 245x163
    // The base panel (without right box) is 176 wide — so the right box is 69px
    // wide (with some padding).
    // We render the entire 245x163 region from the texture.
    private static final int DEP_TEXTURE_SIZE = 512; // Canvas size of the dep texture
    private static final int DEP_GUI_U = 113; // U offset in the dep texture
    private static final int DEP_GUI_V = 140; // V offset in the dep texture
    private static final int DEP_GUI_TOTAL_W = 245; // Total GUI width (main + right box)
    private static final int DEP_GUI_H = 163; // GUI height
    private static final int DEP_PANEL_CONTENT_OFFSET = 180; // X offset into the combined GUI where the right box
                                                             // starts (pixels from left GUI edge)
    private static final int DEP_PANEL_CONTENT_WIDTH = 62; // Usable width inside the right box

    // Unified color palette
    private static final int COLOR_PRIMARY = 0x404040; // Main text (stage name, labels)
    private static final int COLOR_SECONDARY = 0x707070; // Secondary info (time, searching, idle states)
    private static final int COLOR_ACCENT = 0x2E8B57; // Active state (progress, finalizing)
    private static final int COLOR_ERROR = 0xAA3333; // Warnings (already learned, invalid)

    private int sidePanelWidth = DEP_PANEL_CONTENT_WIDTH;
    private int totalGuiWidth = 176;
    private boolean hasDependencies = false;
    private Component pendingTooltip = null;

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
                guiGraphics.drawString(this.font, alreadyLearnedText, (this.imageWidth / 2) - (textWidth / 2), 55,
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
                    guiGraphics.drawString(this.font, "§c⚠ Dependencies not met", 48, 28, COLOR_ERROR, false);
                }

                // Lines 3-4: Progress + Time
                if (stack.getTag().contains("ResearchProgress")) {
                    int currentProgress = stack.getTag().getInt("ResearchProgress");
                    int maxProgress = stack.getTag().contains("MaxProgress") ? stack.getTag().getInt("MaxProgress")
                            : 400;

                    int percent = (int) (((double) currentProgress / maxProgress) * 100);
                    guiGraphics.drawString(this.font, "Progress: " + Math.min(100, percent) + "%", 48, 52, COLOR_ACCENT,
                            false);

                    int remainingTicks = Math.max(0, maxProgress - currentProgress);
                    int remainingSeconds = (remainingTicks / 20) + (remainingTicks % 20 > 0 ? 1 : 0);
                    if (percent >= 100)
                        remainingSeconds = 0;

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
            guiGraphics.drawString(this.font, "Invalid Book!", 48, 28, COLOR_ERROR, false);
        } else {
            int ticks = (int) (Minecraft.getInstance().level.getGameTime() / 10) % 4;
            guiGraphics.drawString(this.font, "Searching" + ".".repeat(ticks), 48, 28, COLOR_SECONDARY, false);
        }

        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                COLOR_PRIMARY, false);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float pPartialTick, int pMouseX, int pMouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        ItemStack stack = menu.getSlot(36).getItem();
        hasDependencies = false;

        if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains("StageResearch")) {
            String stageId = stack.getTag().getString("StageResearch");
            if (!ModItems.CREATIVE_STAGE_ID.equals(stageId)) {
                StageEntry entry = StageManager.isIndividualStage(stageId)
                        ? StageManager.getIndividualStages().get(stageId)
                        : StageManager.getStages().get(stageId);
                if (entry != null && entry.hasDependencies()) {
                    hasDependencies = true;
                }
            }
        }

        if (hasDependencies) {
            // Use the wider dependency texture
            totalGuiWidth = DEP_GUI_TOTAL_W;
            this.imageWidth = DEP_GUI_TOTAL_W;
            this.imageHeight = DEP_GUI_H;
            int x = (width - DEP_GUI_TOTAL_W) / 2;
            int y = (height - DEP_GUI_H) / 2;
            this.leftPos = x;
            this.topPos = y;

            RenderSystem.setShaderTexture(0, TEXTURE_DEP);
            guiGraphics.blit(TEXTURE_DEP, x, y, DEP_GUI_U, DEP_GUI_V, DEP_GUI_TOTAL_W, DEP_GUI_H, DEP_TEXTURE_SIZE,
                    DEP_TEXTURE_SIZE);
        } else {
            // Use normal texture
            totalGuiWidth = 176;
            this.imageWidth = 176;
            this.imageHeight = 166;
            int x = (width - 176) / 2;
            int y = (height - 166) / 2;
            this.leftPos = x;
            this.topPos = y;

            RenderSystem.setShaderTexture(0, TEXTURE);
            guiGraphics.blit(TEXTURE, x, y, 0, 0, 176, 166, 256, 256);
        }

        int x = this.leftPos;
        int y = this.topPos;

        if (hasDependencies) {
            renderDependencyPanel(guiGraphics, x + DEP_PANEL_CONTENT_OFFSET, y, pMouseX, pMouseY);
        }

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
        pendingTooltip = null;
        super.render(guiGraphics, mouseX, mouseY, delta);

        if (menu.isIndividualMode()) {
            ItemStack stack = menu.getSlot(36).getItem();
            if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains("StageResearch")) {
                String stageId = stack.getTag().getString("StageResearch");
                if (!ClientIndividualStageCache.isStageUnlocked(stageId)) {
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
            guiGraphics.renderTooltip(this.font, pendingTooltip, mouseX, mouseY);
        } else {
            renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    private void renderDependencyPanel(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        ItemStack stack = menu.getSlot(36).getItem();
        if (stack.isEmpty() || !stack.hasTag())
            return;
        String stageId = stack.getTag().getString("StageResearch");

        // The PNG already draws the panel background — no need to fill it manually
        // here.
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400); // Render above JEI

        DependencyResult result = ClientDependencyCache.get(stageId);
        if (result == null) {
            PacketHandler.sendToServer(new CheckDependencyPacket(stageId, StageManager.isIndividualStage(stageId)));
            guiGraphics.drawString(this.font, "Loading...", x + 10, y + 20, COLOR_SECONDARY, false);
            return;
        }

        guiGraphics.drawString(this.font, "Dependencies", x + 10, y + 8, COLOR_PRIMARY, false);

        int currentY = y + 20;
        int groupIdx = 0;
        for (DependencyResult.GroupResult group : result.getGroups()) {
            if (result.getGroups().size() > 1) {
                guiGraphics.drawString(this.font, "Group " + (groupIdx + 1) + ":", x + 6, currentY, COLOR_SECONDARY,
                        false);
                currentY += 10;
            }

            for (DependencyResult.EntryResult entry : group.getEntries()) {
                if (currentY > y + imageHeight - 15)
                    break;

                boolean fulfilled = entry.isFulfilled();
                int color = fulfilled ? COLOR_ACCENT : COLOR_ERROR;
                String check = fulfilled ? "[\u2714] " : "[\u2718] ";

                if ("item".equals(entry.getType())) {
                    ResourceLocation itemRl = ResourceLocation.tryParse(entry.getId());
                    if (itemRl != null) {
                        ItemStack itemIcon = new ItemStack(ForgeRegistries.ITEMS.getValue(itemRl));
                        guiGraphics.renderItem(itemIcon, x + 10, currentY);
                        String progressText = entry.getCurrent() + "/" + entry.getRequired();
                        guiGraphics.drawString(this.font, progressText, x + 30, currentY + 4, color, false);

                        if (mouseX >= x + 8 && mouseX <= x + sidePanelWidth - 12 && mouseY >= currentY
                                && mouseY <= currentY + 16) {
                            guiGraphics.fill(x + 5, currentY - 1, x + sidePanelWidth - 9, currentY + 17, 0x30FFFFFF);
                            pendingTooltip = Component.literal(entry.getDescription()).withStyle(ChatFormatting.GRAY);
                        }
                        currentY += 18;
                    }
                } else if ("xp_level".equals(entry.getType())) {
                    guiGraphics.drawString(this.font, check + entry.getDescription(), x + 10, currentY, color, false);
                    if (!fulfilled && entry.getDescription().contains("(consumed)")) {
                        if (mouseX >= x + 8 && mouseX <= x + sidePanelWidth - 12 && mouseY >= currentY
                                && mouseY <= currentY + 10) {
                            guiGraphics.fill(x + 5, currentY - 1, x + sidePanelWidth - 9, currentY + 11, 0x30FFFFFF);
                            pendingTooltip = Component.literal("Click to deposit levels")
                                    .withStyle(ChatFormatting.YELLOW);
                        }
                    }
                    currentY += 12;
                } else {
                    String desc = entry.getDescription();
                    if (this.font.width(desc) > sidePanelWidth - 30) {
                        desc = this.font.plainSubstrByWidth(desc, sidePanelWidth - 40) + "...";
                    }
                    guiGraphics.drawString(this.font, check + desc, x + 10, currentY, color, false);
                    if (mouseX >= x + 8 && mouseX <= x + sidePanelWidth - 12 && mouseY >= currentY
                            && mouseY <= currentY + 10) {
                        pendingTooltip = Component.literal(entry.getDescription()).withStyle(ChatFormatting.GRAY);
                    }
                    currentY += 11;
                }
            }
            groupIdx++;
            currentY += 4;
        }
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (hasDependencies && pButton == 0) {
            int xIdx = this.leftPos + DEP_PANEL_CONTENT_OFFSET;
            int yIdx = this.topPos;

            ItemStack scroll = menu.getSlot(36).getItem();
            if (!scroll.isEmpty() && scroll.hasTag()) {
                String stageId = scroll.getTag().getString("StageResearch");
                DependencyResult result = ClientDependencyCache.get(stageId);

                if (result != null) {
                    int currentY = yIdx + 20;
                    int groupIdx = 0;
                    for (DependencyResult.GroupResult group : result.getGroups()) {
                        if (result.getGroups().size() > 1)
                            currentY += 10;
                        for (DependencyResult.EntryResult entry : group.getEntries()) {
                            if ("item".equals(entry.getType())) {
                                if (pMouseX >= xIdx + 8 && pMouseX <= xIdx + sidePanelWidth - 12 && pMouseY >= currentY
                                        && pMouseY <= currentY + 16) {
                                    String itId = entry.getId();
                                    PacketHandler.sendToServer(
                                            new DepositDependencyPacket(menu.getBlockPos(), groupIdx, "ITEM", itId));
                                    return true;
                                }
                                currentY += 18;
                            } else if ("xp_level".equals(entry.getType())) {
                                if (!entry.isFulfilled() && entry.getDescription().contains("(consumed)")) {
                                    if (pMouseX >= xIdx + 8 && pMouseX <= xIdx + sidePanelWidth - 12
                                            && pMouseY >= currentY && pMouseY <= currentY + 10) {
                                        PacketHandler.sendToServer(
                                                new DepositDependencyPacket(menu.getBlockPos(), groupIdx, "XP", ""));
                                        return true;
                                    }
                                }
                                currentY += 12;
                            } else {
                                currentY += 11;
                            }
                        }
                        groupIdx++;
                        currentY += 4;
                    }
                }
            }
        }
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }
}
