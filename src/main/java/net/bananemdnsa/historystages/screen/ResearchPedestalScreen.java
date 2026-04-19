package net.bananemdnsa.historystages.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.network.CheckDependencyPacket;
import net.bananemdnsa.historystages.network.DepositDependencyPacket;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.util.ClientDependencyCache;
import net.bananemdnsa.historystages.util.ClientIndividualStageCache;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import static net.bananemdnsa.historystages.util.ResourceLocationHelper.MOD_RESOURCE_LOCATION;

public class ResearchPedestalScreen extends AbstractContainerScreen<ResearchPedestalMenu> {
    private static final ResourceLocation TEXTURE = MOD_RESOURCE_LOCATION("textures/gui/research_pedestal_gui.png");
    private static final ResourceLocation TEXTURE_DEP = MOD_RESOURCE_LOCATION("textures/gui/research_pedestal_depen-gui.png");

    // The dependency PNG is 512x512; the GUI sits at (113,140) with total size
    // 271x163
    // The base panel (without right box) is 176 wide — so the right box is 95px
    // wide.
    // We render the entire 271x163 region from the texture.
    private static final int DEP_TEXTURE_SIZE = 512; // Canvas size of the dep texture
    private static final int DEP_GUI_U = 113; // U offset in the dep texture
    private static final int DEP_GUI_V = 138; // V offset (adjusted to 138 to match old alignment)
    private static final int DEP_GUI_TOTAL_W = 271; // Total GUI width (main + right box)
    private static final int DEP_GUI_H = 166; // Match old GUI height
    private static final int DEP_PANEL_CONTENT_OFFSET = 180; // X offset where right box content starts
    private static final int DEP_PANEL_CONTENT_WIDTH = 88; // Usable width inside the right box

    // Unified color palette
    private static final int COLOR_PRIMARY = 0x404040; // Main text (stage name, labels)
    private static final int COLOR_SECONDARY = 0x707070; // Secondary info (time, searching, idle states)
    private static final int COLOR_ACCENT = 0x2E8B57; // Active state (progress, finalizing)
    private static final int COLOR_ERROR = 0xAA3333; // Warnings (already learned, invalid)

    private int sidePanelWidth = DEP_PANEL_CONTENT_WIDTH;
    private boolean hasDependencies = false;
    private Component pendingTooltip = null;

    // Scrolling state
    private float scrollAmount = 0.0f;
    private int totalContentHeight = 0;
    private CompoundTag lastDepositedNBT = null;

    @Override
    protected void init() {
        // Calculate hasDependencies to determine width before super.init() centers it
        checkDependencies();

        if (hasDependencies) {
            this.imageWidth = DEP_GUI_TOTAL_W;
        } else {
            this.imageWidth = 176;
        }
        this.imageHeight = 166;

        super.init();

        // If we have dependencies, adjust leftPos so the MAIN area (176px) remains
        // centered.
        // This ensures the slots don't move and JEI doesn't cover them.
        if (hasDependencies) {
            int originalLeft = (this.width - 176) / 2;
            this.leftPos = originalLeft;
        }
    }

    private void checkDependencies() {
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
    }

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
                // Center relative to the main 176px panel
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
                    // Center under the research bar (bar is at y=40, height 7)
                    guiGraphics.drawString(this.font, warning, (176 / 2) - (ww / 2), 58, COLOR_ERROR, false);
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

        // Update dependency state in case it changed (e.g. scroll removed/added)
        boolean prevDeps = hasDependencies;
        checkDependencies();

        // If dependency state changed, re-initialize to update imageWidth and centering
        if (hasDependencies != prevDeps) {
            this.init(this.minecraft, this.width, this.height);
        }

        if (hasDependencies) {
            RenderSystem.setShaderTexture(0, TEXTURE_DEP);
            guiGraphics.blit(TEXTURE_DEP, this.leftPos, this.topPos, DEP_GUI_U, DEP_GUI_V,
                    DEP_GUI_TOTAL_W, DEP_GUI_H, DEP_TEXTURE_SIZE, DEP_TEXTURE_SIZE);
        } else {
            RenderSystem.setShaderTexture(0, TEXTURE);
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
            int barHeight = 7;
            guiGraphics.fill(startX, startY, startX + progressWidth, startY + barHeight, 0xFF00FF00);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        renderBackground(guiGraphics);
        pendingTooltip = null;
        super.render(guiGraphics, mouseX, mouseY, delta);

        ItemStack stack = menu.getSlot(36).getItem();
        if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains("StageResearch")) {
            String stageId = stack.getTag().getString("StageResearch");

            // CACHE INVALIDATION: If the deposited NBT has changed, clear the cache for
            // this stage
            CompoundTag currentDeposited = stack.getTag().getCompound("DepositedDependencies");
            if (lastDepositedNBT == null || !lastDepositedNBT.equals(currentDeposited)) {
                ClientDependencyCache.remove(stageId);
                lastDepositedNBT = currentDeposited.copy();
                lastDependencyCheck = 0; // Force immediate re-request
            }

            if (menu.isIndividualMode()) {
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

        if (hasDependencies) {
            // No need to manually render slot background here if PNG has it,
            // but we ensure the slot is visible over the panel.
            // Screen slots are handled by AbstractContainerScreen.renderSlot
        }

        if (pendingTooltip != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 500); // Forces tooltip to the very front
            guiGraphics.renderTooltip(this.font, pendingTooltip, mouseX, mouseY);
            guiGraphics.pose().popPose();
        } else {
            renderTooltip(guiGraphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseScrolled(double pMouseX, double pMouseY, double pDelta) {
        if (hasDependencies) {
            int panelX = this.leftPos + 176;
            if (pMouseX >= panelX && pMouseX <= panelX + 95) {
                int maxScroll = Math.max(0, totalContentHeight - (115));
                if (maxScroll > 0) {
                    scrollAmount = (float) Math.max(0, Math.min(maxScroll, scrollAmount - pDelta * 12));
                    return true;
                }
            }
        }
        return super.mouseScrolled(pMouseX, pMouseY, pDelta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (hasDependencies && button == 0) {
            ItemStack scroll = menu.getSlot(36).getItem();
            if (!scroll.isEmpty() && scroll.hasTag()) {
                String stageId = scroll.getTag().getString("StageResearch");
                DependencyResult result = ClientDependencyCache.get(stageId);
                if (result != null) {
                    int groupIdx = 0;
                    for (DependencyResult.GroupResult group : result.getGroups()) {
                        for (DependencyResult.EntryResult entry : group.getEntries()) {
                            if (entry.canDeposit()) {
                                int btnX = this.leftPos + 185; // Under "Deposit:" label (180 + 5)
                                int btnY = this.topPos + 152; // 10px below label
                                if (mouseX >= btnX && mouseX <= btnX + 45 && mouseY >= btnY && mouseY <= btnY + 10) {
                                    PacketHandler.sendToServer(
                                            new DepositDependencyPacket(menu.getBlockPos(), groupIdx, "XP", ""));
                                    return true;
                                }
                            }
                        }
                        groupIdx++;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private long lastDependencyCheck = 0;

    private void renderDependencyPanel(GuiGraphics guiGraphics, int x, int y, int mouseX, int mouseY) {
        ItemStack stack = menu.getSlot(36).getItem();
        if (stack.isEmpty() || !stack.hasTag())
            return;
        String stageId = stack.getTag().getString("StageResearch");
        if (stageId == null || stageId.isEmpty())
            return;

        DependencyResult result = ClientDependencyCache.get(stageId);

        long now = System.currentTimeMillis();
        if (now - lastDependencyCheck > 1000) { // Check once per second
            PacketHandler.sendToServer(new CheckDependencyPacket(stageId, StageManager.isIndividualStage(stageId),
                    menu.getBlockPos()));
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

        // Title (Centered)
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 100);
        String pTitle = "Requirements";
        guiGraphics.drawString(this.font, pTitle, x + (DEP_PANEL_CONTENT_WIDTH / 2) - (font.width(pTitle) / 2), y + 8,
                COLOR_PRIMARY, false);

        // Deposit Slot Area Label
        guiGraphics.drawString(this.font, "Deposit:", x + 5, y + 142, COLOR_SECONDARY, false);

        // Deposit Progress Bar (Gray)
        int dDelay = menu.data.get(5);
        if (dDelay > 0) {
            // Check if item is actually needed (simple check based on color tint in result)
            boolean isNeeded = false;
            ItemStack depositStack = menu.getSlot(37).getItem();
            if (!depositStack.isEmpty() && result != null) {
                String dId = ForgeRegistries.ITEMS.getKey(depositStack.getItem()).toString();
                isNeeded = result.getGroups().stream()
                        .flatMap(g -> g.getEntries().stream())
                        .anyMatch(e -> "item".equals(e.getType()) && dId.equals(e.getId()) && !e.isFulfilled());
            }

            if (isNeeded) {
                int barWidth = 16;
                int barHeight = 2;
                int filledWidth = (int) ((double) dDelay / 20.0 * barWidth);

                int bx = this.leftPos + 246; // Use absolute leftPos to avoid double offset
                int by = this.topPos + 142 + 18;

                guiGraphics.fill(bx, by, bx + barWidth, by + barHeight, 0xFF404040); // Background
                guiGraphics.fill(bx, by, bx + filledWidth, by + barHeight, 0xFFAAAAAA); // Progress (Gray)
            }
        }
        guiGraphics.pose().popPose();

        // Clipping area for content (Scissor uses screen coordinates)
        int clipX = x - 2;
        int clipY = y + 20;
        int clipW = DEP_PANEL_CONTENT_WIDTH + 10;
        int clipH = 115; // Space before the deposit slot

        // Correct scissor calculation: (leftPos + relX) * scale
        // For simplicity in 1.20.1 GuiGraphics, we use enableScissor with screen coords
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
                int bgColor = fulfilled ? 0x202E8B57 : 0x20AA3333; // Subtle tint
                int borderColor = fulfilled ? 0x802E8B57 : 0x80AA3333;

                int cardH = 22;
                if (!"item".equals(entry.getType()))
                    cardH = 15;

                // Card background
                guiGraphics.fill(-2, currentY - 1, DEP_PANEL_CONTENT_WIDTH + 5, currentY + cardH - 1, bgColor);
                guiGraphics.fill(-2, currentY - 1, -1, currentY + cardH - 1, borderColor); // Indicator line

                if ("item".equals(entry.getType())) {
                    ResourceLocation itemRl = ResourceLocation.tryParse(entry.getId());
                    if (itemRl != null) {
                        ItemStack itemIcon = new ItemStack(ForgeRegistries.ITEMS.getValue(itemRl));
                        // Draw slot-like box for icon
                        guiGraphics.fill(2, currentY, 20, currentY + 18, 0x40000000);
                        guiGraphics.renderItem(itemIcon, 3, currentY + 1);

                        String progressText = entry.getCurrent() + "/" + entry.getRequired();
                        guiGraphics.drawString(this.font, progressText, 25, currentY + 5,
                                fulfilled ? COLOR_ACCENT : COLOR_PRIMARY, false);

                        if (mouseX >= x && mouseX <= x + DEP_PANEL_CONTENT_WIDTH
                                && mouseY >= y + 20 + currentY - scrollAmount
                                && mouseY < y + 20 + currentY + cardH - scrollAmount
                                && mouseY >= y + 20 && mouseY <= y + 20 + clipH) {
                            pendingTooltip = Component.literal(entry.getDescription()).withStyle(ChatFormatting.GRAY);
                        }
                    }
                } else if ("xp_level".equals(entry.getType())) {
                    ItemStack icon = new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE);
                    guiGraphics.renderItem(icon, 2, currentY - 1);
                    String text = entry.getDescription();

                    if (font.width(text) > 65)
                        text = font.plainSubstrByWidth(text, 60) + "..";

                    guiGraphics.drawString(this.font, text, 22, currentY + 3, fulfilled ? COLOR_ACCENT : COLOR_PRIMARY,
                            false);

                    if (mouseX >= x && mouseX <= x + DEP_PANEL_CONTENT_WIDTH
                            && mouseY >= y + 20 + currentY - scrollAmount
                            && mouseY < y + 20 + currentY + cardH - scrollAmount
                            && mouseY >= y + 20 && mouseY <= y + 20 + clipH) {
                        pendingTooltip = Component.literal(entry.getDescription()).withStyle(ChatFormatting.GRAY);
                    }
                } else {
                    String desc = entry.getDescription();
                    if (this.font.width(desc) > DEP_PANEL_CONTENT_WIDTH - 5) {
                        desc = this.font.plainSubstrByWidth(desc, DEP_PANEL_CONTENT_WIDTH - 15) + "...";
                    }
                    guiGraphics.drawString(this.font, (fulfilled ? "§a" : "§7") + desc, 5, currentY + 2, COLOR_PRIMARY,
                            false);

                    if (mouseX >= x && mouseX <= x + DEP_PANEL_CONTENT_WIDTH
                            && mouseY >= y + 20 + currentY - scrollAmount
                            && mouseY < y + 20 + currentY + cardH - scrollAmount
                            && mouseY >= y + 20 && mouseY <= y + 20 + clipH) {
                        pendingTooltip = Component.literal(entry.getDescription()).withStyle(ChatFormatting.GRAY);
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

        // Block tooltips if over the deposit slot area (18x18 slot + small margin)
        if (mouseX >= this.leftPos + 245 && mouseX <= this.leftPos + 245 + 20 &&
                mouseY >= this.topPos + 141 && mouseY <= this.topPos + 141 + 20) {
            pendingTooltip = null;
        }

        // Scrollbar rendering
        if (totalContentHeight > clipH) {
            int barX = x + DEP_PANEL_CONTENT_WIDTH + 6;
            int barY = y + 22;
            int barH = clipH - 4;
            guiGraphics.fill(barX, barY, barX + 2, barY + barH, 0x40000000);

            float scrollPercent = scrollAmount / (float) (totalContentHeight - clipH);
            int thumbH = Math.max(10, (int) (barH * (clipH / (float) totalContentHeight)));
            int thumbY = (int) (scrollPercent * (barH - thumbH));
            guiGraphics.fill(barX, barY + thumbY, barX + 2, barY + thumbY + thumbH, 0x80FFFFFF);
        }

        // Render XP PAY button under the deposit slot if needed
        DependencyResult resultSync = ClientDependencyCache.get(stageId);
        if (resultSync != null) {
            int gIdx = 0;
            for (DependencyResult.GroupResult group : resultSync.getGroups()) {
                for (DependencyResult.EntryResult entry : group.getEntries()) {
                    if (entry.canDeposit()) {
                        int btnX = x + 5; // Under "Deposit:" label
                        int btnY = y + 152;
                        boolean hovered = mouseX >= btnX && mouseX <= btnX + 45 && mouseY >= btnY
                                && mouseY <= btnY + 10;

                        guiGraphics.fill(btnX, btnY, btnX + 45, btnY + 10, hovered ? 0xFF666666 : 0xFF333333);
                        guiGraphics.drawString(this.font, "PAY XP", btnX + 4, btnY + 1, 0xFFFFFFFF, false);
                        return; // Show only one button
                    }
                }
                gIdx++;
            }
        }
    }

}
