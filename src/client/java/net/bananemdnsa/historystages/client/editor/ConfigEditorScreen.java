package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.client.OptionalRecipeViewHooks;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.SearchableItemList;
import net.bananemdnsa.historystages.client.editor.widget.SearchableTagList;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ConfigEditorScreen extends Screen {
    private static final int HEADER_HEIGHT = 50;
    private static final int SECTION_HEADER_HEIGHT = 22;
    private static final int ENTRY_HEIGHT = 24;
    private static final int SECTION_GAP = 12;
    private static final int TAB_HEIGHT = 16;
    private static final long TOOLTIP_DELAY_MS = 500L;

    private final Screen parent;
    private final Config.Client initialClient = copyClient(Config.CLIENT);
    private final Config.Common initialCommon = copyCommon(Config.COMMON);
    private Config.Client draftClient = copyClient(Config.CLIENT);
    private Config.Common draftCommon = copyCommon(Config.COMMON);

    private int activeTab = 0;
    private double scrollOffset = 0;
    private int maxScroll = 0;
    private boolean draggingScrollbar = false;
    private int[] tabX;
    private int[] tabW;
    private int tabY;

    private List<ConfigSection> clientSections = List.of();
    private List<ConfigSection> commonSections = List.of();

    private String hoveredEntryKey;
    private long hoverStartTime;

    public ConfigEditorScreen(Screen parent) {
        super(Component.translatable("editor.historystages.config_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        buildSections();

        tabY = 30;
        tabX = new int[] { this.width / 2 - 100, this.width / 2 + 2 };
        tabW = new int[] { 98, 98 };

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> tryClose(),
                10, this.height - 30, 60, 20));
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.save"),
                btn -> saveConfig(),
                this.width / 2 - 50, this.height - 30, 100, 20));
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.reset"),
                btn -> {
                    draftClient = copyClient(initialClient);
                    draftCommon = copyCommon(initialCommon);
                    buildSections();
                },
                this.width - 70, this.height - 30, 60, 20));

        updateMaxScroll();
    }

    private void buildSections() {
        List<ConfigSection> client = new ArrayList<>();
        List<ConfigSection> common = new ArrayList<>();

        ConfigSection visuals = new ConfigSection("Visuals");
        visuals.add(boolEntry("Hide In JEI",
                "Hide locked items in JEI/EMI when supported?",
                () -> draftClient.hideInJei, value -> draftClient.hideInJei = value));
        visuals.add(boolEntry("Show Tooltips",
                "Show information tooltips on locked items?",
                () -> draftClient.showTooltips, value -> draftClient.showTooltips = value));
        visuals.add(boolEntry("Show Stage Name",
                "If tooltips are enabled, show the name of the required stage?",
                () -> draftClient.showStageName, value -> draftClient.showStageName = value));
        visuals.add(boolEntry("Show All Until Complete",
                "If an item is in multiple stages, show all of them until all are unlocked?",
                () -> draftClient.showAllUntilComplete, value -> draftClient.showAllUntilComplete = value));
        visuals.add(boolEntry("Show Lock Icons",
                "Show a lock icon overlay on locked items in JEI and Inventories?",
                () -> draftClient.showLockIcons, value -> draftClient.showLockIcons = value));
        client.add(visuals);

        ConfigSection jade = new ConfigSection("Jade");
        jade.add(boolEntry("Show Jade Info",
                "Show stage information on locked blocks in the Jade overlay?",
                () -> draftClient.jadeShowInfo, value -> draftClient.jadeShowInfo = value));
        jade.add(boolEntry("Show Stage Name (Jade)",
                "If Jade info is enabled, show the name of the required stage?",
                () -> draftClient.jadeStageName, value -> draftClient.jadeStageName = value));
        jade.add(boolEntry("Show All Until Complete (Jade)",
                "If a block is in multiple stages, show all of them until all are unlocked?",
                () -> draftClient.jadeShowAllUntilComplete, value -> draftClient.jadeShowAllUntilComplete = value));
        client.add(jade);

        ConfigSection individualClient = new ConfigSection("Individual Stages");
        individualClient.add(boolEntry("Show Silver Lock Icons",
                "Show a silver lock icon on items locked by individual stages?",
                () -> draftClient.showSilverLockIcons, value -> draftClient.showSilverLockIcons = value));
        individualClient.add(boolEntry("Show Individual Tooltips",
                "Show tooltip information for items locked by individual stages?",
                () -> draftClient.showIndividualTooltips, value -> draftClient.showIndividualTooltips = value));
        client.add(individualClient);

        ConfigSection dimLock = new ConfigSection("Dimension Lock");
        dimLock.add(boolEntry("Use Actionbar",
                "Show a simple 'Dimension Locked' message in the actionbar?",
                () -> draftClient.dimUseActionbar, value -> draftClient.dimUseActionbar = value));
        dimLock.add(boolEntry("Show In Chat",
                "Show the dimension lock message in the chat?",
                () -> draftClient.dimShowChat, value -> draftClient.dimShowChat = value));
        dimLock.add(boolEntry("Show Stages In Chat",
                "If dimShowChat is true, should the required stages also be listed?",
                () -> draftClient.dimShowStagesInChat, value -> draftClient.dimShowStagesInChat = value));
        client.add(dimLock);

        ConfigSection mobLock = new ConfigSection("Mob Lock");
        mobLock.add(boolEntry("Use Actionbar",
                "Show a 'Mob Protected' message in the actionbar?",
                () -> draftClient.mobUseActionbar, value -> draftClient.mobUseActionbar = value));
        mobLock.add(boolEntry("Show In Chat",
                "Show the mob lock message in the chat?",
                () -> draftClient.mobShowChat, value -> draftClient.mobShowChat = value));
        mobLock.add(boolEntry("Show Stages In Chat",
                "If mobShowChat is true, should the required stages also be listed?",
                () -> draftClient.mobShowStagesInChat, value -> draftClient.mobShowStagesInChat = value));
        client.add(mobLock);

        ConfigSection messages = new ConfigSection("Messages");
        messages.add(boolEntry("Show Welcome Message",
                "Show a welcome message in chat when a player joins the world?",
                () -> draftCommon.showWelcomeMessage, value -> draftCommon.showWelcomeMessage = value));
        messages.add(boolEntry("Show Debug Errors",
                "Show debug messages in chat if a JSON stage has errors or missing items?",
                () -> draftCommon.showDebugErrors, value -> draftCommon.showDebugErrors = value));
        messages.add(boolEntry("Enable Runtime Logging",
                "Log runtime events (unlock/lock, blocked actions, loot replacements) to config/historystages/logs/?",
                () -> draftCommon.enableRuntimeLogging, value -> draftCommon.enableRuntimeLogging = value));
        common.add(messages);

        ConfigSection gameplay = new ConfigSection("Gameplay");
        gameplay.add(boolEntry("Lock Mob Loot",
                "Handle locked items in mob loot tables?",
                () -> draftCommon.lockMobLoot, value -> draftCommon.lockMobLoot = value));
        gameplay.add(boolEntry("Lock Block Breaking",
                "Make locked blocks much harder to break and prevent their drops?",
                () -> draftCommon.lockBlockBreaking, value -> draftCommon.lockBlockBreaking = value));
        gameplay.add(floatEntry("Locked Block Break Speed Multiplier",
                "Break speed multiplier for locked blocks (0.001-1.0). Lower = slower.",
                () -> draftCommon.lockedBlockBreakSpeedMultiplier,
                value -> draftCommon.lockedBlockBreakSpeedMultiplier = value));
        gameplay.add(boolEntry("Lock Item Usage",
                "Prevent using locked items? (equipping armor, weapons, food, etc.)",
                () -> draftCommon.lockItemUsage, value -> draftCommon.lockItemUsage = value));
        gameplay.add(boolEntry("Lock Entity Items",
                "Prevent interacting with or breaking armor stands and item frames that contain locked items?",
                () -> draftCommon.lockEntityItems, value -> draftCommon.lockEntityItems = value));
        gameplay.add(boolEntry("Lock Block Interaction",
                "Prevent opening the GUI of locked blocks? (Chests, furnaces, crafting tables, etc.)",
                () -> draftCommon.lockBlockInteraction, value -> draftCommon.lockBlockInteraction = value));
        common.add(gameplay);

        ConfigSection notifications = new ConfigSection("Notifications");
        notifications.add(boolEntry("Broadcast Chat",
                "Show unlock/lock messages in the chat for everyone?",
                () -> draftCommon.broadcastChat, value -> draftCommon.broadcastChat = value));
        notifications.add(stringEntry("Unlock Message Format",
                "Message format for unlocks. Use {stage} for the name and & for colors.",
                () -> draftCommon.unlockMessageFormat, value -> draftCommon.unlockMessageFormat = value));
        notifications.add(boolEntry("Use Actionbar",
                "Show messages in the actionbar for everyone?",
                () -> draftCommon.useActionbar, value -> draftCommon.useActionbar = value));
        notifications.add(boolEntry("Use Sounds",
                "Play notification sounds for everyone?",
                () -> draftCommon.useSounds, value -> draftCommon.useSounds = value));
        notifications.add(boolEntry("Use Toasts",
                "Show an advancement-style toast popup when a stage is unlocked?",
                () -> draftCommon.useToasts, value -> draftCommon.useToasts = value));
        common.add(notifications);

        ConfigSection researchPedestal = new ConfigSection("Research Pedestal");
        researchPedestal.add(intEntry("Research Time In Seconds",
                "Default research time in seconds. Used as fallback if a stage does not define its own.",
                () -> draftCommon.researchTimeInSeconds, value -> draftCommon.researchTimeInSeconds = value));
        researchPedestal.add(boolEntry("Show Dependency Screen In Pedestal",
                "Show dependency checklist screen when interacting with pedestal that has dependency requirements?",
                () -> draftCommon.showDependencyScreenInPedestal,
                value -> draftCommon.showDependencyScreenInPedestal = value));
        common.add(researchPedestal);

        ConfigSection individualCommon = new ConfigSection("Individual Stages");
        individualCommon.add(boolEntry("Lock Item Pickup",
                "Prevent players from picking up items locked by individual stages?",
                () -> draftCommon.individualLockItemPickup, value -> draftCommon.individualLockItemPickup = value));
        individualCommon.add(boolEntry("Drop On Revoke",
                "Drop locked items from a player's inventory when their individual stage is revoked?",
                () -> draftCommon.individualDropOnRevoke, value -> draftCommon.individualDropOnRevoke = value));
        individualCommon.add(boolEntry("Lock Block Breaking",
                "Make blocks locked by individual stages much harder to break and prevent their drops?",
                () -> draftCommon.individualLockBlockBreaking,
                value -> draftCommon.individualLockBlockBreaking = value));
        individualCommon.add(floatEntry("Locked Block Break Speed Multiplier",
                "Break speed multiplier for blocks locked by individual stages (0.001-1.0). Lower = slower.",
                () -> draftCommon.individualLockedBlockBreakSpeedMultiplier,
                value -> draftCommon.individualLockedBlockBreakSpeedMultiplier = value));
        individualCommon.add(boolEntry("Lock Item Usage",
                "Prevent using items locked by individual stages? (Blocks equipping armor, using weapons, eating food, etc.)",
                () -> draftCommon.individualLockItemUsage,
                value -> draftCommon.individualLockItemUsage = value));
        individualCommon.add(boolEntry("Lock Block Interaction",
                "Prevent opening the GUI of blocks locked by individual stages? (Chests, furnaces, crafting tables, etc.)",
                () -> draftCommon.individualLockBlockInteraction,
                value -> draftCommon.individualLockBlockInteraction = value));
        individualCommon.add(boolEntry("Broadcast Chat",
                "Show individual stage unlock/lock messages in the chat for the player?",
                () -> draftCommon.individualBroadcastChat,
                value -> draftCommon.individualBroadcastChat = value));
        individualCommon.add(stringEntry("Unlock Message Format",
                "Message format for individual unlocks. Use {stage} for the name, {player} for the player, and & for colors.",
                () -> draftCommon.individualUnlockMessageFormat,
                value -> draftCommon.individualUnlockMessageFormat = value));
        individualCommon.add(boolEntry("Use Actionbar",
                "Show individual stage messages in the actionbar?",
                () -> draftCommon.individualUseActionbar,
                value -> draftCommon.individualUseActionbar = value));
        individualCommon.add(boolEntry("Use Sounds",
                "Play notification sounds for individual stage unlocks?",
                () -> draftCommon.individualUseSounds,
                value -> draftCommon.individualUseSounds = value));
        individualCommon.add(boolEntry("Use Toasts",
                "Show an advancement-style toast popup when an individual stage is unlocked?",
                () -> draftCommon.individualUseToasts,
                value -> draftCommon.individualUseToasts = value));
        common.add(individualCommon);

        ConfigSection lootReplacements = new ConfigSection("Loot Replacements");
        lootReplacements.add(boolEntry("Use Replacements",
                "If true, locked items are replaced by specific items/tags. If false, they disappear.",
                () -> draftCommon.useReplacements, value -> draftCommon.useReplacements = value));
        lootReplacements.add(itemListEntry("Replacement Items",
                "List of item IDs to pick from as replacement (Priority 1). Click to manage.",
                () -> draftCommon.replacementItems,
                value -> draftCommon.replacementItems = new ArrayList<>(value)));
        lootReplacements.add(tagListEntry("Replacement Tags",
                "List of tags to pick a random replacement from (Priority 2). Click to manage.",
                () -> draftCommon.replacementTag,
                value -> draftCommon.replacementTag = new ArrayList<>(value)));
        common.add(lootReplacements);

        clientSections = client;
        commonSections = common;
        updateMaxScroll();
    }

    private void saveConfig() {
        copyClient(draftClient, Config.CLIENT);
        copyCommon(draftCommon, Config.COMMON);
        Config.save();
        OptionalRecipeViewHooks.refreshAll();

        copyClient(Config.CLIENT, initialClient);
        copyCommon(Config.COMMON, initialCommon);
        buildSections();
    }

    private boolean hasChanges() {
        return !clientsEqual(initialClient, draftClient) || !commonsEqual(initialCommon, draftCommon);
    }

    private void tryClose() {
        if (!hasChanges()) {
            this.minecraft.setScreen(parent);
            return;
        }

        this.minecraft.setScreen(new ConfirmDialog(
                this,
                Component.translatable("editor.historystages.unsaved_warning_title"),
                Component.translatable("editor.historystages.unsaved_warning"),
                () -> this.minecraft.setScreen(parent)));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        drawTab(guiGraphics, tabX[0], tabY, tabW[0], TAB_HEIGHT, activeTab == 0, "Client");
        drawTab(guiGraphics, tabX[1], tabY, tabW[1], TAB_HEIGHT, activeTab == 1, "Common");

        int listTop = HEADER_HEIGHT;
        int listBottom = this.height - 44;
        int contentLeft = 30;
        int contentRight = this.width - 30;

        guiGraphics.fill(10, HEADER_HEIGHT, this.width - 10, HEADER_HEIGHT + 1, 0xFF555555);

        guiGraphics.enableScissor(contentLeft - 10, listTop, contentRight + 10, listBottom);

        int y = listTop - (int) scrollOffset;
        String currentHovered = null;
        String currentDescription = null;

        for (ConfigSection section : currentSections()) {
            if (y + SECTION_HEADER_HEIGHT > listTop - 20 && y < listBottom + 20) {
                guiGraphics.fill(contentLeft, y, contentRight, y + SECTION_HEADER_HEIGHT, 0x30FFFFFF);
                guiGraphics.drawString(this.font, section.label, contentLeft + 5, y + 7, 0xFFCC00, false);
            }
            y += SECTION_HEADER_HEIGHT;

            for (ConfigEntry entry : section.entries) {
                if (y + ENTRY_HEIGHT > listTop - 20 && y < listBottom + 20) {
                    renderConfigEntry(guiGraphics, entry, contentLeft, y, contentRight, mouseX, mouseY);

                    boolean hovered = mouseX >= contentLeft && mouseX <= contentRight
                            && mouseY >= y && mouseY < y + ENTRY_HEIGHT
                            && mouseY >= listTop && mouseY <= listBottom;
                    if (hovered) {
                        currentHovered = entry.key;
                        currentDescription = entry.tooltip;
                    }
                }
                y += ENTRY_HEIGHT;
            }

            y += SECTION_GAP;
        }

        guiGraphics.disableScissor();

        if (maxScroll > 0) {
            int scrollAreaHeight = listBottom - listTop;
            int thumbHeight = Math.max(20,
                    (int) ((float) scrollAreaHeight / (maxScroll + scrollAreaHeight) * scrollAreaHeight));
            int thumbY = listTop + (int) ((float) scrollOffset / maxScroll * (scrollAreaHeight - thumbHeight));
            guiGraphics.fill(contentRight + 2, thumbY, contentRight + 5, thumbY + thumbHeight, 0x80FFFFFF);
        }

        if (hasChanges()) {
            int dotX = this.width / 2 + 55;
            guiGraphics.fill(dotX, this.height - 25, dotX + 6, this.height - 19, 0xFFFFCC00);
            guiGraphics.drawString(this.font,
                    Component.translatable("editor.historystages.unsaved").getString(),
                    dotX + 9, this.height - 24, 0xFFCC00, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (currentHovered != null) {
            if (!currentHovered.equals(hoveredEntryKey)) {
                hoveredEntryKey = currentHovered;
                hoverStartTime = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - hoverStartTime >= TOOLTIP_DELAY_MS && currentDescription != null) {
                renderTooltip(guiGraphics, currentDescription, mouseX, mouseY);
            }
        } else {
            hoveredEntryKey = null;
        }
    }

    private void drawTab(GuiGraphics guiGraphics, int x, int y, int width, int height, boolean active, String label) {
        guiGraphics.fill(x, y, x + width, y + height, active ? 0x50FFCC00 : 0x25FFFFFF);
        guiGraphics.fill(x, y + height - 2, x + width, y + height, active ? 0xFFFFCC00 : 0xFF555555);
        guiGraphics.drawCenteredString(this.font, label, x + width / 2, y + 4, active ? 0xFFFFFF : 0xBBBBBB);
    }

    private void renderTooltip(GuiGraphics guiGraphics, String text, int mouseX, int mouseY) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400);

        List<String> lines = new ArrayList<>();
        int maxWidth = 200;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && this.font.width(candidate) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }

        int tooltipW = 0;
        for (String value : lines) {
            tooltipW = Math.max(tooltipW, this.font.width(value));
        }
        tooltipW += 8;
        int tooltipH = lines.size() * 10 + 6;

        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 4;
        if (tooltipX + tooltipW + 2 > this.width - 4) {
            tooltipX = mouseX - tooltipW - 4;
        }
        if (tooltipY + tooltipH + 2 > this.height - 4) {
            tooltipY = this.height - tooltipH - 6;
        }
        if (tooltipX < 4) {
            tooltipX = 4;
        }
        if (tooltipY < 4) {
            tooltipY = 4;
        }

        guiGraphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipW + 2, tooltipY + tooltipH + 2, 0xFF3D3D3D);
        guiGraphics.fill(tooltipX, tooltipY, tooltipX + tooltipW, tooltipY + tooltipH, 0xFF0D0D0D);

        int textY = tooltipY + 3;
        for (String value : lines) {
            guiGraphics.drawString(this.font, value, tooltipX + 4, textY, 0xCCCCCC, false);
            textY += 10;
        }

        guiGraphics.pose().popPose();
    }

    private void renderConfigEntry(GuiGraphics guiGraphics, ConfigEntry entry, int left, int y, int right, int mouseX,
            int mouseY) {
        boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
        if (hovered) {
            guiGraphics.fill(left, y, right, y + ENTRY_HEIGHT, 0x15FFFFFF);
        }

        guiGraphics.drawString(this.font, entry.label, left + 8, y + 8, 0xCCCCCC, false);

        int labelWidth = this.font.width(entry.label);
        int controlX = left + Math.max(labelWidth + 20, 180);
        int availWidth = right - controlX - 5;

        switch (entry.type) {
            case BOOLEAN -> {
                boolean value = entry.boolValue.get();
                String text = value ? "✔ ON" : "✘ OFF";
                int color = value ? 0x55FF55 : 0xFF5555;
                guiGraphics.drawString(this.font, text, controlX, y + 8, color, false);
            }
            case INTEGER, FLOAT, STRING -> {
                String text = entry.displayValue();
                if (availWidth > 0 && this.font.width(text) > availWidth) {
                    text = this.font.plainSubstrByWidth(text, availWidth - 10) + "...";
                }
                guiGraphics.drawString(this.font, text, controlX, y + 8, 0xDDDDDD, false);
            }
            case ITEM_LIST -> {
                int count = entry.listGetter.get().size();
                String text = "[" + count + " items] §7(click to edit)";
                guiGraphics.drawString(this.font, text, controlX, y + 8, hovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
            case TAG_LIST -> {
                int count = entry.listGetter.get().size();
                String text = "[" + count + " tags] §7(click to edit)";
                guiGraphics.drawString(this.font, text, controlX, y + 8, hovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
            for (int i = 0; i < tabX.length; i++) {
                if (mouseX >= tabX[i] && mouseX < tabX[i] + tabW[i]) {
                    activeTab = i;
                    scrollOffset = 0;
                    updateMaxScroll();
                    return true;
                }
            }
        }

        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        int listTop = HEADER_HEIGHT;
        int listBottom = this.height - 44;
        int contentLeft = 30;
        int contentRight = this.width - 30;

        if (maxScroll > 0 && mouseX >= contentRight + 1 && mouseX <= contentRight + 6
                && mouseY >= listTop && mouseY <= listBottom) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY, listTop, listBottom);
            return true;
        }

        if (mouseX < contentLeft - 10 || mouseX > contentRight + 10 || mouseY < listTop || mouseY > listBottom) {
            return false;
        }

        int y = listTop - (int) scrollOffset;
        for (ConfigSection section : currentSections()) {
            y += SECTION_HEADER_HEIGHT;
            for (ConfigEntry entry : section.entries) {
                int rowTop = y;
                int rowBottom = y + ENTRY_HEIGHT;
                if (mouseY >= rowTop && mouseY < rowBottom) {
                    handleEntryClick(entry);
                    return true;
                }
                y += ENTRY_HEIGHT;
            }
            y += SECTION_GAP;
        }

        return false;
    }

    private void handleEntryClick(ConfigEntry entry) {
        switch (entry.type) {
            case BOOLEAN -> entry.boolSetter.accept(!entry.boolValue.get());
            case INTEGER -> this.minecraft.setScreen(new ValueInputScreen(this, entry, true, false));
            case FLOAT -> this.minecraft.setScreen(new ValueInputScreen(this, entry, false, true));
            case STRING -> this.minecraft.setScreen(new ValueInputScreen(this, entry, false, false));
            case ITEM_LIST -> this.minecraft.setScreen(new ItemListEditorScreen(this, entry));
            case TAG_LIST -> this.minecraft.setScreen(new TagListEditorScreen(this, entry));
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            updateScrollFromMouse(mouseY, HEADER_HEIGHT, this.height - 44);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * 16));
        return true;
    }

    private void updateScrollFromMouse(double mouseY, int listTop, int listBottom) {
        int scrollAreaHeight = listBottom - listTop;
        float ratio = (float) Math.max(0, Math.min(1, (mouseY - listTop) / (double) scrollAreaHeight));
        scrollOffset = Math.round(ratio * maxScroll);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
    }

    private void updateMaxScroll() {
        int totalHeight = 0;
        for (ConfigSection section : currentSections()) {
            totalHeight += SECTION_HEADER_HEIGHT + section.entries.size() * ENTRY_HEIGHT + SECTION_GAP;
        }
        maxScroll = Math.max(0, totalHeight - (this.height - HEADER_HEIGHT - 50));
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            tryClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
        tryClose();
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private List<ConfigSection> currentSections() {
        return activeTab == 0 ? clientSections : commonSections;
    }

    private static Config.Client copyClient(Config.Client from) {
        Config.Client to = new Config.Client();
        copyClient(from, to);
        return to;
    }

    private static void copyClient(Config.Client from, Config.Client to) {
        to.hideInJei = from.hideInJei;
        to.showTooltips = from.showTooltips;
        to.showIndividualTooltips = from.showIndividualTooltips;
        to.showStageName = from.showStageName;
        to.showAllUntilComplete = from.showAllUntilComplete;
        to.jadeShowInfo = from.jadeShowInfo;
        to.jadeStageName = from.jadeStageName;
        to.jadeShowAllUntilComplete = from.jadeShowAllUntilComplete;
        to.showDependenciesOnScroll = from.showDependenciesOnScroll;
        to.hideFulfilledDependencies = from.hideFulfilledDependencies;
        to.showLockIcons = from.showLockIcons;
        to.showSilverLockIcons = from.showSilverLockIcons;
        to.dimUseActionbar = from.dimUseActionbar;
        to.dimShowChat = from.dimShowChat;
        to.dimShowStagesInChat = from.dimShowStagesInChat;
        to.mobUseActionbar = from.mobUseActionbar;
        to.mobShowChat = from.mobShowChat;
        to.mobShowStagesInChat = from.mobShowStagesInChat;
    }

    private static Config.Common copyCommon(Config.Common from) {
        Config.Common to = new Config.Common();
        copyCommon(from, to);
        return to;
    }

    private static void copyCommon(Config.Common from, Config.Common to) {
        to.showWelcomeMessage = from.showWelcomeMessage;
        to.showDebugErrors = from.showDebugErrors;
        to.enableRuntimeLogging = from.enableRuntimeLogging;
        to.lockMobLoot = from.lockMobLoot;
        to.lockItemUsage = from.lockItemUsage;
        to.lockEntityItems = from.lockEntityItems;
        to.individualLockItemUsage = from.individualLockItemUsage;
        to.individualLockItemPickup = from.individualLockItemPickup;
        to.individualDropOnRevoke = from.individualDropOnRevoke;
        to.lockBlockInteraction = from.lockBlockInteraction;
        to.individualLockBlockInteraction = from.individualLockBlockInteraction;
        to.lockBlockBreaking = from.lockBlockBreaking;
        to.individualLockBlockBreaking = from.individualLockBlockBreaking;
        to.lockedBlockBreakSpeedMultiplier = from.lockedBlockBreakSpeedMultiplier;
        to.individualLockedBlockBreakSpeedMultiplier = from.individualLockedBlockBreakSpeedMultiplier;
        to.researchTimeInSeconds = from.researchTimeInSeconds;
        to.showDependencyScreenInPedestal = from.showDependencyScreenInPedestal;
        to.broadcastChat = from.broadcastChat;
        to.unlockMessageFormat = from.unlockMessageFormat;
        to.useActionbar = from.useActionbar;
        to.useSounds = from.useSounds;
        to.useToasts = from.useToasts;
        to.defaultStageIcon = from.defaultStageIcon;
        to.useReplacements = from.useReplacements;
        to.replacementItems = new ArrayList<>(from.replacementItems);
        to.replacementTag = new ArrayList<>(from.replacementTag);
        to.lockContainerInteraction = from.lockContainerInteraction;
        to.lockEnchanting = from.lockEnchanting;
        to.individualLockEnchanting = from.individualLockEnchanting;
        to.individualBroadcastChat = from.individualBroadcastChat;
        to.individualUnlockMessageFormat = from.individualUnlockMessageFormat;
        to.individualUseActionbar = from.individualUseActionbar;
        to.individualUseSounds = from.individualUseSounds;
        to.individualUseToasts = from.individualUseToasts;
        to.structureMessageEnabled = from.structureMessageEnabled;
        to.structureLockInChat = from.structureLockInChat;
        to.structureDamageEnabled = from.structureDamageEnabled;
        to.structureDamageAmount = from.structureDamageAmount;
        to.structureDamageInterval = from.structureDamageInterval;
        to.structureCheckInterval = from.structureCheckInterval;
        to.structureLockMessageFormat = from.structureLockMessageFormat;
    }

    private static boolean clientsEqual(Config.Client a, Config.Client b) {
        return a.hideInJei == b.hideInJei
                && a.showTooltips == b.showTooltips
                && a.showIndividualTooltips == b.showIndividualTooltips
                && a.showStageName == b.showStageName
                && a.showAllUntilComplete == b.showAllUntilComplete
                && a.jadeShowInfo == b.jadeShowInfo
                && a.jadeStageName == b.jadeStageName
                && a.jadeShowAllUntilComplete == b.jadeShowAllUntilComplete
                && a.showDependenciesOnScroll == b.showDependenciesOnScroll
                && a.hideFulfilledDependencies == b.hideFulfilledDependencies
                && a.showLockIcons == b.showLockIcons
                && a.showSilverLockIcons == b.showSilverLockIcons
                && a.dimUseActionbar == b.dimUseActionbar
                && a.dimShowChat == b.dimShowChat
                && a.dimShowStagesInChat == b.dimShowStagesInChat
                && a.mobUseActionbar == b.mobUseActionbar
                && a.mobShowChat == b.mobShowChat
                && a.mobShowStagesInChat == b.mobShowStagesInChat;
    }

    private static boolean commonsEqual(Config.Common a, Config.Common b) {
        return a.showWelcomeMessage == b.showWelcomeMessage
                && a.showDebugErrors == b.showDebugErrors
                && a.enableRuntimeLogging == b.enableRuntimeLogging
                && a.lockMobLoot == b.lockMobLoot
                && a.lockItemUsage == b.lockItemUsage
                && a.lockEntityItems == b.lockEntityItems
                && a.individualLockItemUsage == b.individualLockItemUsage
                && a.individualLockItemPickup == b.individualLockItemPickup
                && a.individualDropOnRevoke == b.individualDropOnRevoke
                && a.lockBlockInteraction == b.lockBlockInteraction
                && a.individualLockBlockInteraction == b.individualLockBlockInteraction
                && a.lockBlockBreaking == b.lockBlockBreaking
                && a.individualLockBlockBreaking == b.individualLockBlockBreaking
                && Float.compare(a.lockedBlockBreakSpeedMultiplier, b.lockedBlockBreakSpeedMultiplier) == 0
                && Float.compare(a.individualLockedBlockBreakSpeedMultiplier, b.individualLockedBlockBreakSpeedMultiplier) == 0
                && a.researchTimeInSeconds == b.researchTimeInSeconds
                && a.showDependencyScreenInPedestal == b.showDependencyScreenInPedestal
                && a.broadcastChat == b.broadcastChat
                && Objects.equals(a.unlockMessageFormat, b.unlockMessageFormat)
                && a.useActionbar == b.useActionbar
                && a.useSounds == b.useSounds
                && a.useToasts == b.useToasts
                && Objects.equals(a.defaultStageIcon, b.defaultStageIcon)
                && a.useReplacements == b.useReplacements
                && Objects.equals(a.replacementItems, b.replacementItems)
                && Objects.equals(a.replacementTag, b.replacementTag)
                && a.lockContainerInteraction == b.lockContainerInteraction
                && a.lockEnchanting == b.lockEnchanting
                && a.individualLockEnchanting == b.individualLockEnchanting
                && a.individualBroadcastChat == b.individualBroadcastChat
                && Objects.equals(a.individualUnlockMessageFormat, b.individualUnlockMessageFormat)
                && a.individualUseActionbar == b.individualUseActionbar
                && a.individualUseSounds == b.individualUseSounds
                && a.individualUseToasts == b.individualUseToasts
                && a.structureMessageEnabled == b.structureMessageEnabled
                && a.structureLockInChat == b.structureLockInChat
                && a.structureDamageEnabled == b.structureDamageEnabled
                && Float.compare(a.structureDamageAmount, b.structureDamageAmount) == 0
                && a.structureDamageInterval == b.structureDamageInterval
                && a.structureCheckInterval == b.structureCheckInterval
                && Objects.equals(a.structureLockMessageFormat, b.structureLockMessageFormat);
    }

    private ConfigEntry boolEntry(String label, String tooltip, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        return new ConfigEntry(label, tooltip, ConfigType.BOOLEAN, getter, setter, null, null, null, null, null);
    }

    private ConfigEntry intEntry(String label, String tooltip, Supplier<Integer> getter, Consumer<Integer> setter) {
        return new ConfigEntry(label, tooltip, ConfigType.INTEGER, null, null,
                () -> Integer.toString(getter.get()),
                value -> setter.accept(Integer.parseInt(value.trim())),
                null, null, null);
    }

    private ConfigEntry floatEntry(String label, String tooltip, Supplier<Float> getter, Consumer<Float> setter) {
        return new ConfigEntry(label, tooltip, ConfigType.FLOAT, null, null,
                () -> Float.toString(getter.get()),
                value -> setter.accept(Float.parseFloat(value.trim())),
                null, null, null);
    }

    private ConfigEntry stringEntry(String label, String tooltip, Supplier<String> getter, Consumer<String> setter) {
        return new ConfigEntry(label, tooltip, ConfigType.STRING, null, null,
                getter::get, setter, null, null, null);
    }

    private ConfigEntry itemListEntry(String label, String tooltip, Supplier<List<String>> getter, Consumer<List<String>> setter) {
        return new ConfigEntry(label, tooltip, ConfigType.ITEM_LIST, null, null, null, null, getter, setter, null);
    }

    private ConfigEntry tagListEntry(String label, String tooltip, Supplier<List<String>> getter, Consumer<List<String>> setter) {
        return new ConfigEntry(label, tooltip, ConfigType.TAG_LIST, null, null, null, null, getter, setter, null);
    }

    private enum ConfigType {
        BOOLEAN,
        INTEGER,
        FLOAT,
        STRING,
        ITEM_LIST,
        TAG_LIST
    }

    private static final class ConfigSection {
        private final String label;
        private final List<ConfigEntry> entries = new ArrayList<>();

        private ConfigSection(String label) {
            this.label = label;
        }

        private void add(ConfigEntry entry) {
            entries.add(entry);
        }
    }

    private static final class ConfigEntry {
        private final String key;
        private final String label;
        private final String tooltip;
        private final ConfigType type;
        private final Supplier<Boolean> boolValue;
        private final Consumer<Boolean> boolSetter;
        private final Supplier<String> stringValue;
        private final Consumer<String> stringSetter;
        private final Supplier<List<String>> listGetter;
        private final Consumer<List<String>> listSetter;
        private final Supplier<ItemStack> itemGetter;

        private ConfigEntry(String label, String tooltip, ConfigType type, Supplier<Boolean> boolValue,
                Consumer<Boolean> boolSetter, Supplier<String> stringValue, Consumer<String> stringSetter,
                Supplier<List<String>> listGetter, Consumer<List<String>> listSetter, Supplier<ItemStack> itemGetter) {
            this.key = label;
            this.label = label;
            this.tooltip = tooltip;
            this.type = type;
            this.boolValue = boolValue;
            this.boolSetter = boolSetter;
            this.stringValue = stringValue;
            this.stringSetter = stringSetter;
            this.listGetter = listGetter;
            this.listSetter = listSetter;
            this.itemGetter = itemGetter;
        }

        private String displayValue() {
            return stringValue == null ? "" : stringValue.get();
        }
    }

    private static final class ValueInputScreen extends Screen {
        private final ConfigEditorScreen parent;
        private final ConfigEntry entry;
        private final boolean integerOnly;
        private final boolean floatOnly;
        private EditBox inputField;

        private ValueInputScreen(ConfigEditorScreen parent, ConfigEntry entry, boolean integerOnly, boolean floatOnly) {
            super(Component.literal(entry.label));
            this.parent = parent;
            this.entry = entry;
            this.integerOnly = integerOnly;
            this.floatOnly = floatOnly;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            inputField = new EditBox(this.font, centerX - 100, centerY - 8, 200, 20, Component.literal(entry.label));
            inputField.setMaxLength(512);
            inputField.setValue(entry.displayValue());
            if (integerOnly) {
                inputField.setFilter(value -> value.isEmpty() || value.matches("\\d+"));
            } else if (floatOnly) {
                inputField.setFilter(value -> value.isEmpty() || value.matches("\\d*(\\.\\d*)?"));
            }

            this.addRenderableWidget(inputField);
            this.setFocused(inputField);

            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.confirm"),
                    btn -> applyAndClose(),
                    centerX - 105, centerY + 20, 100, 20));
            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.cancel"),
                    btn -> this.minecraft.setScreen(parent),
                    centerX + 5, centerY + 20, 100, 20));
        }

        private void applyAndClose() {
            try {
                entry.stringSetter.accept(inputField.getValue());
                this.minecraft.setScreen(parent);
            } catch (RuntimeException ignored) {
            }
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 257) {
                applyAndClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            parent.render(guiGraphics, -1, -1, partialTick);
            guiGraphics.fill(0, 0, this.width, this.height, 0xA0000000);

            int boxW = 260;
            int boxH = 100;
            int boxX = (this.width - boxW) / 2;
            int boxY = (this.height - boxH) / 2 - 10;
            guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF2D2D2D);
            guiGraphics.fill(boxX + 1, boxY + 1, boxX + boxW - 1, boxY + boxH - 1, 0xFF1A1A1A);
            guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, boxY + 8, 0xFFFFFF);

            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean isPauseScreen() {
            return true;
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(parent);
        }
    }

    private static final class ItemListEditorScreen extends Screen {
        private final ConfigEditorScreen parent;
        private final ConfigEntry entry;
        private final List<String> items;
        private SearchableItemList itemOverlay;
        private double scrollOffset;
        private int maxScroll;
        private boolean draggingScrollbar;

        private static final int ROW_HEIGHT = 22;
        private static final int LIST_TOP = 50;

        private ItemListEditorScreen(ConfigEditorScreen parent, ConfigEntry entry) {
            super(Component.literal(entry.label));
            this.parent = parent;
            this.entry = entry;
            this.items = new ArrayList<>(entry.listGetter.get());
        }

        @Override
        protected void init() {
            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.back"),
                    btn -> saveAndClose(),
                    10, this.height - 30, 60, 20));
            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.add"),
                    btn -> {
                        itemOverlay = new SearchableItemList(itemId -> {
                            if (!items.contains(itemId)) {
                                items.add(itemId);
                                updateMaxScroll();
                            }
                            itemOverlay = null;
                        });
                        itemOverlay.show(this.width / 2, this.height / 2, this.width);
                    },
                    this.width / 2 - 50, this.height - 30, 100, 20));
            updateMaxScroll();
        }

        private void updateMaxScroll() {
            int visibleHeight = this.height - 40 - LIST_TOP;
            maxScroll = Math.max(0, items.size() * ROW_HEIGHT - visibleHeight);
            scrollOffset = Math.min(scrollOffset, maxScroll);
        }

        private void saveAndClose() {
            entry.listSetter.accept(new ArrayList<>(items));
            this.minecraft.setScreen(parent);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);
            guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
            guiGraphics.drawCenteredString(this.font, items.size() + " items", this.width / 2, 25, 0x999999);
            guiGraphics.fill(30, LIST_TOP - 4, this.width - 30, LIST_TOP - 3, 0xFF555555);

            int listBottom = this.height - 40;
            int contentLeft = 40;
            int contentRight = this.width - 40;
            guiGraphics.enableScissor(contentLeft - 5, LIST_TOP, contentRight + 5, listBottom);

            int y = LIST_TOP - (int) scrollOffset;
            for (int i = 0; i < items.size(); i++) {
                if (y + ROW_HEIGHT > LIST_TOP - 10 && y < listBottom + 10) {
                    String itemId = items.get(i);
                    boolean hovered = mouseX >= contentLeft && mouseX <= contentRight
                            && mouseY >= y && mouseY < y + ROW_HEIGHT
                            && mouseY >= LIST_TOP && mouseY <= listBottom;
                    if (hovered) {
                        guiGraphics.fill(contentLeft, y, contentRight, y + ROW_HEIGHT, 0x20FFFFFF);
                    }
                    guiGraphics.drawString(this.font, itemId, contentLeft + 8, y + 7, 0xCCCCCC, false);
                    int removeX = contentRight - 14;
                    guiGraphics.drawString(this.font, "×", removeX + 2, y + 6,
                            hovered ? 0xFF5555 : 0x888888, false);
                }
                y += ROW_HEIGHT;
            }

            guiGraphics.disableScissor();
            super.render(guiGraphics, mouseX, mouseY, partialTick);

            if (itemOverlay != null) {
                guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);
                itemOverlay.render(guiGraphics, this.font, mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (itemOverlay != null) {
                if (itemOverlay.mouseClicked(mouseX, mouseY)) {
                    return true;
                }
                itemOverlay = null;
                return true;
            }

            if (super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            int listBottom = this.height - 40;
            int contentLeft = 40;
            int contentRight = this.width - 40;
            if (mouseX < contentLeft || mouseX > contentRight || mouseY < LIST_TOP || mouseY > listBottom) {
                return false;
            }

            int y = LIST_TOP - (int) scrollOffset;
            for (int i = 0; i < items.size(); i++) {
                if (mouseY >= y && mouseY < y + ROW_HEIGHT) {
                    int removeX = contentRight - 14;
                    if (mouseX >= removeX && mouseX <= removeX + 12) {
                        items.remove(i);
                        updateMaxScroll();
                        return true;
                    }
                }
                y += ROW_HEIGHT;
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (itemOverlay != null) {
                return itemOverlay.mouseScrolled(mouseX, mouseY, verticalAmount);
            }
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * 16));
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (itemOverlay != null) {
                if (keyCode == 256) {
                    itemOverlay = null;
                    return true;
                }
                return itemOverlay.keyPressed(keyCode);
            }
            if (keyCode == 256) {
                saveAndClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char c, int modifiers) {
            if (itemOverlay != null) {
                return itemOverlay.charTyped(c);
            }
            return super.charTyped(c, modifiers);
        }

        @Override
        public boolean isPauseScreen() {
            return true;
        }

        @Override
        public void onClose() {
            saveAndClose();
        }
    }

    private static final class TagListEditorScreen extends Screen {
        private final ConfigEditorScreen parent;
        private final ConfigEntry entry;
        private final List<String> tags;
        private SearchableTagList tagOverlay;
        private double scrollOffset;
        private int maxScroll;

        private static final int ROW_HEIGHT = 22;
        private static final int LIST_TOP = 50;

        private TagListEditorScreen(ConfigEditorScreen parent, ConfigEntry entry) {
            super(Component.literal(entry.label));
            this.parent = parent;
            this.entry = entry;
            this.tags = new ArrayList<>(entry.listGetter.get());
        }

        @Override
        protected void init() {
            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.back"),
                    btn -> saveAndClose(),
                    10, this.height - 30, 60, 20));
            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.add"),
                    btn -> {
                        tagOverlay = new SearchableTagList(tagId -> {
                            if (!tags.contains(tagId)) {
                                tags.add(tagId);
                                updateMaxScroll();
                            }
                            tagOverlay = null;
                        });
                        tagOverlay.show(this.width / 2, this.height / 2, this.width);
                    },
                    this.width / 2 - 50, this.height - 30, 100, 20));
            updateMaxScroll();
        }

        private void updateMaxScroll() {
            int visibleHeight = this.height - 40 - LIST_TOP;
            maxScroll = Math.max(0, tags.size() * ROW_HEIGHT - visibleHeight);
            scrollOffset = Math.min(scrollOffset, maxScroll);
        }

        private void saveAndClose() {
            entry.listSetter.accept(new ArrayList<>(tags));
            this.minecraft.setScreen(parent);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);
            guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
            guiGraphics.drawCenteredString(this.font, tags.size() + " tags", this.width / 2, 25, 0x999999);
            guiGraphics.fill(30, LIST_TOP - 4, this.width - 30, LIST_TOP - 3, 0xFF555555);

            int listBottom = this.height - 40;
            int contentLeft = 40;
            int contentRight = this.width - 40;
            guiGraphics.enableScissor(contentLeft - 5, LIST_TOP, contentRight + 5, listBottom);

            int y = LIST_TOP - (int) scrollOffset;
            for (int i = 0; i < tags.size(); i++) {
                if (y + ROW_HEIGHT > LIST_TOP - 10 && y < listBottom + 10) {
                    String tagId = tags.get(i);
                    boolean hovered = mouseX >= contentLeft && mouseX <= contentRight
                            && mouseY >= y && mouseY < y + ROW_HEIGHT
                            && mouseY >= LIST_TOP && mouseY <= listBottom;
                    if (hovered) {
                        guiGraphics.fill(contentLeft, y, contentRight, y + ROW_HEIGHT, 0x20FFFFFF);
                    }
                    guiGraphics.drawString(this.font, "#" + tagId, contentLeft + 8, y + 7, 0xCCCCCC, false);
                    int removeX = contentRight - 14;
                    guiGraphics.drawString(this.font, "×", removeX + 2, y + 6,
                            hovered ? 0xFF5555 : 0x888888, false);
                }
                y += ROW_HEIGHT;
            }

            guiGraphics.disableScissor();
            super.render(guiGraphics, mouseX, mouseY, partialTick);

            if (tagOverlay != null) {
                guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);
                tagOverlay.render(guiGraphics, this.font, mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (tagOverlay != null) {
                if (tagOverlay.mouseClicked(mouseX, mouseY)) {
                    return true;
                }
                tagOverlay = null;
                return true;
            }

            if (super.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }

            int listBottom = this.height - 40;
            int contentLeft = 40;
            int contentRight = this.width - 40;
            if (mouseX < contentLeft || mouseX > contentRight || mouseY < LIST_TOP || mouseY > listBottom) {
                return false;
            }

            int y = LIST_TOP - (int) scrollOffset;
            for (int i = 0; i < tags.size(); i++) {
                if (mouseY >= y && mouseY < y + ROW_HEIGHT) {
                    int removeX = contentRight - 14;
                    if (mouseX >= removeX && mouseX <= removeX + 12) {
                        tags.remove(i);
                        updateMaxScroll();
                        return true;
                    }
                }
                y += ROW_HEIGHT;
            }
            return false;
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (tagOverlay != null) {
                return tagOverlay.mouseScrolled(mouseX, mouseY, verticalAmount);
            }
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - verticalAmount * 16));
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (tagOverlay != null) {
                if (keyCode == 256) {
                    tagOverlay = null;
                    return true;
                }
                return tagOverlay.keyPressed(keyCode);
            }
            if (keyCode == 256) {
                saveAndClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char c, int modifiers) {
            if (tagOverlay != null) {
                return tagOverlay.charTyped(c);
            }
            return super.charTyped(c, modifiers);
        }

        @Override
        public boolean isPauseScreen() {
            return true;
        }

        @Override
        public void onClose() {
            saveAndClose();
        }
    }
}
