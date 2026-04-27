package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SaveConfigPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import net.bananemdnsa.historystages.client.editor.widget.SearchableItemList;
import net.bananemdnsa.historystages.client.editor.widget.SearchableTagList;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigEditorScreen extends Screen {
    private final Screen parent;

    // Tab state: 0 = Client, 1 = Common
    private int activeTab = 1;

    // Scrolling
    private double scrollOffset = 0;
    private int maxScroll = 0;
    private boolean draggingScrollbar = false;

    // Unsaved changes tracking - computed by comparing current values to initial
    // values

    // Config entries grouped by section
    private List<ConfigSection> clientSections;
    private List<ConfigSection> commonSections;

    // Tooltip hover tracking
    private String hoveredEntryKey = null;
    private long hoverStartTime = 0;
    private static final long TOOLTIP_DELAY_MS = 500;

    // Tab layout
    private static final String[] TAB_KEYS = {
            "editor.historystages.tab.client",
            "editor.historystages.tab.common"
    };
    private int[] tabX;
    private int[] tabW;
    private int tabY;

    // Layout constants
    private static final int HEADER_HEIGHT = 50;
    private static final int SECTION_HEADER_HEIGHT = 22;
    private static final int ENTRY_HEIGHT = 24;
    private static final int SECTION_GAP = 12;
    private static final int TAB_HEIGHT = 16;
    private static final int TAB_PAD = 8;
    private static final float SMALL_SCALE = 0.85f;

    public ConfigEditorScreen(Screen parent) {
        super(Component.translatable("editor.historystages.config_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (clientSections == null)
            buildConfigEntries();

        // Compute tab positions
        tabY = 30;
        tabX = new int[TAB_KEYS.length];
        tabW = new int[TAB_KEYS.length];
        int tabTotalWidth = 200;
        int gap = 2;
        int tabStartX = this.width / 2 - tabTotalWidth / 2;
        int tabWidthEach = (tabTotalWidth - gap) / TAB_KEYS.length;
        int x = tabStartX;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            tabX[i] = x;
            tabW[i] = tabWidthEach;
            x += tabWidthEach + gap;
        }

        // Back button
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> tryClose(), 10, this.height - 30, 60, 20));

        // Save button
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.save"),
                btn -> saveConfig(), this.width / 2 - 50, this.height - 30, 100, 20));

        // Reset button
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.reset"),
                btn -> {
                    this.minecraft.setScreen(new ConfirmDialog(
                            this,
                            Component.translatable("editor.historystages.reset_warning_title"),
                            Component.translatable("editor.historystages.reset_warning"),
                            () -> {
                                resetToDefaults();
                                this.minecraft.setScreen(this);
                            }));
                }, this.width - 70, this.height - 30, 60, 20));

        updateMaxScroll();
    }

    private void buildConfigEntries() {
        // --- CLIENT CONFIG ---
        clientSections = new ArrayList<>();

        ConfigSection visuals = new ConfigSection("editor.historystages.config.visuals");
        visuals.add(new ConfigEntry("showTooltips", ConfigType.BOOLEAN,
                Config.CLIENT.showTooltips.get().toString(), true, "true",
                "Show information tooltips on locked items?"));
        visuals.add(new ConfigEntry("showStageName", ConfigType.BOOLEAN,
                Config.CLIENT.showStageName.get().toString(), true, "true",
                "If tooltips are enabled, show the name of the required stage?"));
        visuals.add(new ConfigEntry("showAllUntilComplete", ConfigType.BOOLEAN,
                Config.CLIENT.showAllUntilComplete.get().toString(), true, "true",
                "If an item is in multiple stages, show all of them until all are unlocked?"));
        visuals.add(new ConfigEntry("showLockIcons", ConfigType.BOOLEAN,
                Config.CLIENT.showLockIcons.get().toString(), true, "true",
                "Show a lock icon overlay on locked items in JEI and Inventories?"));
        clientSections.add(visuals);

        ConfigSection jade = new ConfigSection("editor.historystages.config.jade");
        jade.add(new ConfigEntry("jadeShowInfo", ConfigType.BOOLEAN,
                Config.CLIENT.jadeShowInfo.get().toString(), true, "true",
                "Show stage information on locked blocks in the Jade overlay?"));
        jade.add(new ConfigEntry("jadeStageName", ConfigType.BOOLEAN,
                Config.CLIENT.jadeStageName.get().toString(), true, "true",
                "If Jade info is enabled, show the name of the required stage?"));
        jade.add(new ConfigEntry("jadeShowAllUntilComplete", ConfigType.BOOLEAN,
                Config.CLIENT.jadeShowAllUntilComplete.get().toString(), true, "true",
                "If a block is in multiple stages, show all of them until all are unlocked?"));
        clientSections.add(jade);

        ConfigSection individualClient = new ConfigSection("editor.historystages.config.individual_stages");
        individualClient.add(new ConfigEntry("showSilverLockIcons", ConfigType.BOOLEAN,
                Config.CLIENT.showSilverLockIcons.get().toString(), true, "true",
                "Show a silver lock icon on items locked by individual stages?"));
        individualClient.add(new ConfigEntry("showIndividualTooltips", ConfigType.BOOLEAN,
                Config.CLIENT.showIndividualTooltips.get().toString(), true, "true",
                "Show tooltip information for items locked by individual stages?"));
        clientSections.add(individualClient);

        ConfigSection dependenciesClient = new ConfigSection("editor.historystages.config.dependencies");
        dependenciesClient.add(new ConfigEntry("showDependenciesOnScroll", ConfigType.BOOLEAN,
                Config.CLIENT.showDependenciesOnScroll.get().toString(), true, "true",
                "Show dependency requirements in research scroll tooltips?"));
        dependenciesClient.add(new ConfigEntry("hideFulfilledDependencies", ConfigType.BOOLEAN,
                Config.CLIENT.hideFulfilledDependencies.get().toString(), true, "false",
                "Hide already fulfilled dependencies in scroll tooltips?"));
        clientSections.add(dependenciesClient);

        ConfigSection dimLock = new ConfigSection("editor.historystages.config.dimension_lock");
        dimLock.add(new ConfigEntry("dimUseActionbar", ConfigType.BOOLEAN,
                Config.CLIENT.dimUseActionbar.get().toString(), true, "true",
                "Show a simple 'Dimension Locked' message in the actionbar?"));
        dimLock.add(new ConfigEntry("dimShowChat", ConfigType.BOOLEAN,
                Config.CLIENT.dimShowChat.get().toString(), true, "false",
                "Show the dimension lock message in the chat?"));
        dimLock.add(new ConfigEntry("dimShowStagesInChat", ConfigType.BOOLEAN,
                Config.CLIENT.dimShowStagesInChat.get().toString(), true, "true",
                "If dimShowChat is true, should the required stages also be listed?"));
        clientSections.add(dimLock);

        ConfigSection mobLock = new ConfigSection("editor.historystages.config.mob_lock");
        mobLock.add(new ConfigEntry("mobUseActionbar", ConfigType.BOOLEAN,
                Config.CLIENT.mobUseActionbar.get().toString(), true, "true",
                "Show a 'Mob Protected' message in the actionbar?"));
        mobLock.add(new ConfigEntry("mobShowChat", ConfigType.BOOLEAN,
                Config.CLIENT.mobShowChat.get().toString(), true, "false",
                "Show the mob lock message in the chat?"));
        mobLock.add(new ConfigEntry("mobShowStagesInChat", ConfigType.BOOLEAN,
                Config.CLIENT.mobShowStagesInChat.get().toString(), true, "true",
                "If mobShowChat is true, should the required stages also be listed?"));
        clientSections.add(mobLock);

        // --- COMMON CONFIG ---
        commonSections = new ArrayList<>();

        ConfigSection messages = new ConfigSection("editor.historystages.config.messages");
        messages.add(new ConfigEntry("showWelcomeMessage", ConfigType.BOOLEAN,
                Config.COMMON.showWelcomeMessage.get().toString(), false, "true",
                "Show a welcome message in chat when a player joins the world?"));
        messages.add(new ConfigEntry("showDebugErrors", ConfigType.BOOLEAN,
                Config.COMMON.showDebugErrors.get().toString(), false, "true",
                "Show debug messages in chat if a JSON stage has errors or missing items?"));
        messages.add(new ConfigEntry("enableRuntimeLogging", ConfigType.BOOLEAN,
                Config.COMMON.enableRuntimeLogging.get().toString(), false, "false",
                "Log runtime events (unlock/lock, blocked actions, loot replacements) to config/historystages/logs/?"));
        commonSections.add(messages);

        ConfigSection gameplay = new ConfigSection("editor.historystages.config.gameplay");
        gameplay.add(new ConfigEntry("lockMobLoot", ConfigType.BOOLEAN,
                Config.COMMON.lockMobLoot.get().toString(), false, "true",
                "Handle locked items in mob loot tables?"));
        gameplay.add(new ConfigEntry("lockBlockBreaking", ConfigType.BOOLEAN,
                Config.COMMON.lockBlockBreaking.get().toString(), false, "true",
                "Make locked blocks much harder to break and prevent their drops?"));
        gameplay.add(new ConfigEntry("lockedBlockBreakSpeedMultiplier", ConfigType.STRING,
                Config.COMMON.lockedBlockBreakSpeedMultiplier.get().toString(), false, "0.05",
                "Break speed multiplier for locked blocks (0.001-1.0). Lower = slower."));
        gameplay.add(new ConfigEntry("lockItemUsage", ConfigType.BOOLEAN,
                Config.COMMON.lockItemUsage.get().toString(), false, "true",
                "Prevent using locked items? (equipping armor, weapons, food, etc.)"));
        gameplay.add(new ConfigEntry("lockEntityItems", ConfigType.BOOLEAN,
                Config.COMMON.lockEntityItems.get().toString(), false, "true",
                "Prevent interacting with or breaking armor stands and item frames that contain locked items?"));
        gameplay.add(new ConfigEntry("lockBlockInteraction", ConfigType.BOOLEAN,
                Config.COMMON.lockBlockInteraction.get().toString(), false, "true",
                "Prevent opening the GUI of locked blocks? (Chests, furnaces, crafting tables, etc.)"));
        commonSections.add(gameplay);

        ConfigSection notifications = new ConfigSection("editor.historystages.config.notifications");
        notifications.add(new ConfigEntry("broadcastChat", ConfigType.BOOLEAN,
                Config.COMMON.broadcastChat.get().toString(), false, "true",
                "Show unlock/lock messages in the chat for everyone?"));
        notifications.add(new ConfigEntry("unlockMessageFormat", ConfigType.STRING,
                Config.COMMON.unlockMessageFormat.get(), false,
                "&fThe world has entered the &b{stage}&f!",
                "Message format for unlocks. Use {stage} for the name and & for colors."));
        notifications.add(new ConfigEntry("useActionbar", ConfigType.BOOLEAN,
                Config.COMMON.useActionbar.get().toString(), false, "false",
                "Show messages in the actionbar for everyone?"));
        notifications.add(new ConfigEntry("useSounds", ConfigType.BOOLEAN,
                Config.COMMON.useSounds.get().toString(), false, "true",
                "Play notification sounds for everyone?"));
        notifications.add(new ConfigEntry("useToasts", ConfigType.BOOLEAN,
                Config.COMMON.useToasts.get().toString(), false, "true",
                "Show an advancement-style toast popup when a stage is unlocked?"));
        notifications.add(new ConfigEntry("defaultStageIcon", ConfigType.ITEM,
                Config.COMMON.defaultStageIcon.get(), false, "historystages:research_scroll",
                "Default icon used in unlock toasts for stages that don't define their own icon."));
        commonSections.add(notifications);

        ConfigSection individualCommon = new ConfigSection("editor.historystages.config.individual_stages");
        individualCommon.add(new ConfigEntry("individualLockItemPickup", ConfigType.BOOLEAN,
                Config.COMMON.individualLockItemPickup.get().toString(), false, "true",
                "Prevent players from picking up items locked by individual stages?"));
        individualCommon.add(new ConfigEntry("individualDropOnRevoke", ConfigType.BOOLEAN,
                Config.COMMON.individualDropOnRevoke.get().toString(), false, "true",
                "Drop locked items from a player's inventory when their individual stage is revoked?"));
        individualCommon.add(new ConfigEntry("individualLockBlockBreaking", ConfigType.BOOLEAN,
                Config.COMMON.individualLockBlockBreaking.get().toString(), false, "true",
                "Make blocks locked by individual stages much harder to break and prevent their drops?"));
        individualCommon.add(new ConfigEntry("individualLockedBlockBreakSpeedMultiplier", ConfigType.STRING,
                Config.COMMON.individualLockedBlockBreakSpeedMultiplier.get().toString(), false, "0.05",
                "Break speed multiplier for blocks locked by individual stages (0.001-1.0). Lower = slower."));
        individualCommon.add(new ConfigEntry("individualLockItemUsage", ConfigType.BOOLEAN,
                Config.COMMON.individualLockItemUsage.get().toString(), false, "true",
                "Prevent using items locked by individual stages? (Blocks equipping armor, using weapons, eating food, etc.)"));
        individualCommon.add(new ConfigEntry("individualLockBlockInteraction", ConfigType.BOOLEAN,
                Config.COMMON.individualLockBlockInteraction.get().toString(), false, "true",
                "Prevent opening the GUI of blocks locked by individual stages? (Chests, furnaces, crafting tables, etc.)"));
        individualCommon.add(new ConfigEntry("individualBroadcastChat", ConfigType.BOOLEAN,
                Config.COMMON.individualBroadcastChat.get().toString(), false, "true",
                "Show individual stage unlock/lock messages in the chat for the player?"));
        individualCommon.add(new ConfigEntry("individualUnlockMessageFormat", ConfigType.STRING,
                Config.COMMON.individualUnlockMessageFormat.get(), false,
                "&fYou have unlocked &b{stage}&f!",
                "Message format for individual unlocks. Use {stage} for the name, {player} for the player, and & for colors."));
        individualCommon.add(new ConfigEntry("individualUseActionbar", ConfigType.BOOLEAN,
                Config.COMMON.individualUseActionbar.get().toString(), false, "false",
                "Show individual stage messages in the actionbar?"));
        individualCommon.add(new ConfigEntry("individualUseSounds", ConfigType.BOOLEAN,
                Config.COMMON.individualUseSounds.get().toString(), false, "true",
                "Play notification sounds for individual stage unlocks?"));
        individualCommon.add(new ConfigEntry("individualUseToasts", ConfigType.BOOLEAN,
                Config.COMMON.individualUseToasts.get().toString(), false, "true",
                "Show an advancement-style toast popup when an individual stage is unlocked?"));
        commonSections.add(individualCommon);

        ConfigSection research = new ConfigSection("editor.historystages.config.research");
        research.add(new ConfigEntry("researchTimeInSeconds", ConfigType.INTEGER,
                Config.COMMON.researchTimeInSeconds.get().toString(), false, "20",
                "Default research time in seconds. Used as fallback if a stage does not define its own."));
        research.add(new ConfigEntry("showDependencyScreenInPedestal", ConfigType.BOOLEAN,
                Config.COMMON.showDependencyScreenInPedestal.get().toString(), false, "true",
                "Show dependency checklist screen when interacting with pedestal that has dependency requirements?"));
        commonSections.add(research);

        ConfigSection lootReplace = new ConfigSection("editor.historystages.config.loot_replacements");
        lootReplace.add(new ConfigEntry("useReplacements", ConfigType.BOOLEAN,
                Config.COMMON.useReplacements.get().toString(), false, "false",
                "If true, locked items are replaced by specific items/tags. If false, they disappear."));
        lootReplace.add(new ConfigEntry("replacementItems", ConfigType.ITEM_LIST,
                String.join(",", Config.COMMON.replacementItems.get()), false,
                "minecraft:cobblestone,minecraft:dirt",
                "List of item IDs to pick from as replacement (Priority 1). Click to manage."));
        lootReplace.add(new ConfigEntry("replacementTag", ConfigType.TAG_LIST,
                String.join(",", Config.COMMON.replacementTag.get()), false, "",
                "List of tags to pick a random replacement from (Priority 2). Click to manage."));
        commonSections.add(lootReplace);

        ConfigSection structureLock = new ConfigSection("editor.historystages.config.structure_lock");
        structureLock.add(new ConfigEntry("structureCheckInterval", ConfigType.INTEGER,
                Config.COMMON.structureCheckInterval.get().toString(), false, "10",
                "How often (in ticks) to check if a player is inside a locked structure."));
        structureLock.add(new ConfigEntry("structureMessageEnabled", ConfigType.BOOLEAN,
                Config.COMMON.structureMessageEnabled.get().toString(), false, "true",
                "Show the player a message when they are inside a locked structure?"));
        structureLock.add(new ConfigEntry("structureLockMessageFormat", ConfigType.STRING,
                Config.COMMON.structureLockMessageFormat.get(), false,
                "&cYou cannot enter &e{structure}&c yet!",
                "Message format for structure lock. Use {structure}, {stage} and & for colors."));
        structureLock.add(new ConfigEntry("structureLockInChat", ConfigType.BOOLEAN,
                Config.COMMON.structureLockInChat.get().toString(), false, "false",
                "Show the structure lock message in chat as well (otherwise only actionbar)?"));
        structureLock.add(new ConfigEntry("structureDamageEnabled", ConfigType.BOOLEAN,
                Config.COMMON.structureDamageEnabled.get().toString(), false, "false",
                "Damage the player while they are inside a locked structure?"));
        structureLock.add(new ConfigEntry("structureDamageAmount", ConfigType.STRING,
                Config.COMMON.structureDamageAmount.get().toString(), false, "1.0",
                "Amount of damage dealt per damage tick."));
        structureLock.add(new ConfigEntry("structureDamageInterval", ConfigType.INTEGER,
                Config.COMMON.structureDamageInterval.get().toString(), false, "20",
                "How often (in ticks) to deal damage while inside a locked structure."));
        commonSections.add(structureLock);
    }

    private void resetToDefaults() {
        List<ConfigSection> allSections = new ArrayList<>();
        allSections.addAll(clientSections);
        allSections.addAll(commonSections);
        for (ConfigSection section : allSections) {
            for (ConfigEntry entry : section.entries) {
                entry.value = entry.defaultValue;
            }
        }
    }

    private boolean hasChanges() {
        List<ConfigSection> allSections = new ArrayList<>();
        if (clientSections != null)
            allSections.addAll(clientSections);
        if (commonSections != null)
            allSections.addAll(commonSections);
        for (ConfigSection section : allSections) {
            for (ConfigEntry entry : section.entries) {
                if (!entry.value.equals(entry.initialValue))
                    return true;
            }
        }
        return false;
    }

    private List<ConfigSection> getActiveSections() {
        return activeTab == 0 ? clientSections : commonSections;
    }

    private void switchTab(int tab) {
        if (activeTab != tab) {
            activeTab = tab;
            scrollOffset = 0;
            updateMaxScroll();
        }
    }

    private void updateMaxScroll() {
        int contentHeight = calculateContentHeight();
        int visibleHeight = this.height - HEADER_HEIGHT - 50;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private int calculateContentHeight() {
        int height = 0;
        for (ConfigSection section : getActiveSections()) {
            height += SECTION_HEADER_HEIGHT;
            height += section.entries.size() * ENTRY_HEIGHT;
            height += SECTION_GAP;
        }
        return height;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Background
        guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);

        // Title
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        // Separator above tabs
        guiGraphics.fill(10, tabY - 2, this.width - 10, tabY - 1, 0xFF555555);

        // Render custom tabs (styled like stage tabs)
        String hoveredTabTooltip = null;
        for (int i = 0; i < TAB_KEYS.length; i++) {
            boolean active = (i == activeTab);
            boolean hovered = mouseX >= tabX[i] && mouseX < tabX[i] + tabW[i]
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            int bg = active ? 0x40FFCC00 : (hovered ? 0x25FFFFFF : 0x15FFFFFF);
            guiGraphics.fill(tabX[i], tabY, tabX[i] + tabW[i], tabY + TAB_HEIGHT, bg);

            if (active) {
                guiGraphics.fill(tabX[i], tabY + TAB_HEIGHT - 2, tabX[i] + tabW[i], tabY + TAB_HEIGHT, 0xFFFFCC00);
            }

            String label = Component.translatable(TAB_KEYS[i]).getString();
            int textColor = active ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x999999);
            drawSmallText(guiGraphics, label, tabX[i] + TAB_PAD, tabY + 4, textColor);

            if (hovered) {
                hoveredTabTooltip = Component.translatable(TAB_KEYS[i] + ".tooltip").getString();
            }
        }

        // Separator below tabs
        guiGraphics.fill(10, HEADER_HEIGHT - 2, this.width - 10, HEADER_HEIGHT - 1, 0xFF555555);

        // Scrollable content area
        int listTop = HEADER_HEIGHT;
        int listBottom = this.height - 40;
        int contentLeft = 30;
        int contentRight = this.width - 30;

        guiGraphics.enableScissor(contentLeft - 10, listTop, contentRight + 10, listBottom);

        int y = listTop - (int) scrollOffset;
        List<ConfigSection> sections = getActiveSections();

        // Track hover for tooltip
        String currentHovered = null;
        String currentDescription = null;
        int tooltipMouseX = mouseX;
        int tooltipMouseY = mouseY;

        for (ConfigSection section : sections) {
            // Section header
            guiGraphics.fill(contentLeft, y, contentRight, y + SECTION_HEADER_HEIGHT, 0x30FFFFFF);
            guiGraphics.drawString(this.font,
                    Component.translatable(section.titleKey).getString(),
                    contentLeft + 5, y + 7, 0xFFCC00, false);
            y += SECTION_HEADER_HEIGHT;

            // Entries
            for (ConfigEntry entry : section.entries) {
                if (y + ENTRY_HEIGHT > listTop - 20 && y < listBottom + 20) {
                    renderConfigEntry(guiGraphics, entry, contentLeft, y, contentRight, mouseX, mouseY);

                    // Check hover for tooltip
                    boolean entryHovered = mouseX >= contentLeft && mouseX <= contentRight
                            && mouseY >= y && mouseY < y + ENTRY_HEIGHT
                            && mouseY >= listTop && mouseY <= listBottom;
                    if (entryHovered && entry.description != null && !entry.description.isEmpty()) {
                        currentHovered = entry.key;
                        currentDescription = entry.description;
                    }
                }
                y += ENTRY_HEIGHT;
            }

            y += SECTION_GAP;
        }

        guiGraphics.disableScissor();

        // Tab hover tooltip — overrides any entry tooltip when hovering a tab
        if (hoveredTabTooltip != null) {
            currentHovered = "__tab__" + activeTab;
            currentDescription = hoveredTabTooltip;
        }

        // Scrollbar
        if (maxScroll > 0) {
            int scrollAreaHeight = listBottom - listTop;
            int barHeight = Math.max(20,
                    (int) ((float) scrollAreaHeight / (maxScroll + scrollAreaHeight) * scrollAreaHeight));
            int barY = listTop + (int) ((float) scrollOffset / maxScroll * (scrollAreaHeight - barHeight));
            guiGraphics.fill(contentRight + 2, barY, contentRight + 5, barY + barHeight, 0x80FFFFFF);
        }

        // Unsaved changes indicator — yellow dot + text
        if (hasChanges()) {
            int dotX = this.width / 2 + 55;
            guiGraphics.fill(dotX, this.height - 25, dotX + 6, this.height - 19, 0xFFFFCC00);
            drawSmallText(guiGraphics, Component.translatable("editor.historystages.unsaved").getString(), dotX + 9,
                    this.height - 24, 0xFFCC00);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (itemPickerOverlay != null && itemPickerOverlay.isVisible()) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 200);
            itemPickerOverlay.render(guiGraphics, this.font, mouseX, mouseY);
            guiGraphics.pose().popPose();
        }

        // Tooltip rendering (after everything else, including super)
        if (currentHovered != null) {
            if (!currentHovered.equals(hoveredEntryKey)) {
                hoveredEntryKey = currentHovered;
                hoverStartTime = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - hoverStartTime >= TOOLTIP_DELAY_MS) {
                renderTooltip(guiGraphics, currentDescription, tooltipMouseX, tooltipMouseY);
            }
        } else {
            hoveredEntryKey = null;
        }
    }

    private void renderTooltip(GuiGraphics guiGraphics, String text, int mouseX, int mouseY) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400);

        // Word-wrap long descriptions
        List<String> lines = new ArrayList<>();
        int maxWidth = 200;
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() > 0 && this.font.width(line + " " + word) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                if (line.length() > 0)
                    line.append(" ");
                line.append(word);
            }
        }
        if (line.length() > 0)
            lines.add(line.toString());

        int tooltipW = 0;
        for (String l : lines) {
            tooltipW = Math.max(tooltipW, this.font.width(l));
        }
        tooltipW += 8;
        int tooltipH = lines.size() * 10 + 6;

        int tooltipX = mouseX + 12;
        int tooltipY = mouseY - 4;

        // Keep on screen
        if (tooltipX + tooltipW + 2 > this.width - 4)
            tooltipX = mouseX - tooltipW - 4;
        if (tooltipY + tooltipH + 2 > this.height - 4)
            tooltipY = this.height - tooltipH - 6;
        if (tooltipX < 4)
            tooltipX = 4;
        if (tooltipY < 4)
            tooltipY = 4;

        guiGraphics.fill(tooltipX - 2, tooltipY - 2, tooltipX + tooltipW + 2, tooltipY + tooltipH + 2, 0xFF3D3D3D);
        guiGraphics.fill(tooltipX, tooltipY, tooltipX + tooltipW, tooltipY + tooltipH, 0xFF0D0D0D);

        int ty = tooltipY + 3;
        for (String l : lines) {
            guiGraphics.drawString(this.font, l, tooltipX + 4, ty, 0xCCCCCC, false);
            ty += 10;
        }

        guiGraphics.pose().popPose();
    }

    private void renderConfigEntry(GuiGraphics guiGraphics, ConfigEntry entry, int left, int y, int right, int mouseX,
            int mouseY) {
        boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + ENTRY_HEIGHT;
        if (hovered) {
            guiGraphics.fill(left, y, right, y + ENTRY_HEIGHT, 0x15FFFFFF);
        }

        // Label
        String label = Component.translatable("editor.historystages.config." + entry.key).getString();
        guiGraphics.drawString(this.font, label, left + 8, y + 8, 0xCCCCCC, false);

        // Value control — positioned further left for better readability
        int labelWidth = this.font.width(label);
        int controlX = left + Math.max(labelWidth + 20, 180);

        switch (entry.type) {
            case BOOLEAN -> {
                boolean val = Boolean.parseBoolean(entry.value);
                String toggleText = val ? "\u2714 ON" : "\u2718 OFF";
                int toggleColor = val ? 0x55FF55 : 0xFF5555;
                boolean toggleHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                if (toggleHovered)
                    toggleColor = val ? 0x88FF88 : 0xFF8888;
                guiGraphics.drawString(this.font, toggleText, controlX, y + 8, toggleColor, false);
            }
            case INTEGER -> {
                guiGraphics.drawString(this.font, entry.value, controlX, y + 8, 0xDDDDDD, false);
            }
            case STRING -> {
                String display = entry.value;
                int availWidth = right - controlX - 5;
                if (availWidth > 0 && this.font.width(display) > availWidth) {
                    display = this.font.plainSubstrByWidth(display, availWidth - 10) + "...";
                }
                guiGraphics.drawString(this.font, display, controlX, y + 8, 0xDDDDDD, false);
            }
            case ITEM -> {
                ItemStack stack = resolveItemStack(entry.value);
                guiGraphics.renderItem(stack, controlX, y + 3);
                String idText = entry.value.isEmpty() ? "\u00A77(click to pick)" : entry.value;
                int availWidth = right - (controlX + 20) - 5;
                if (availWidth > 0 && this.font.width(idText) > availWidth) {
                    idText = this.font.plainSubstrByWidth(idText, availWidth - 10) + "...";
                }
                boolean itemHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                guiGraphics.drawString(this.font, idText, controlX + 22, y + 8,
                        itemHovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
            case ITEM_LIST -> {
                int count = entry.value.isEmpty() ? 0 : entry.value.split(",").length;
                String display = "[" + count + " items] \u00A77(click to edit)";
                boolean listHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                guiGraphics.drawString(this.font, display, controlX, y + 8,
                        listHovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
            case TAG_LIST -> {
                int count = entry.value.isEmpty() ? 0 : entry.value.split(",").length;
                String display = "[" + count + " tags] \u00A77(click to edit)";
                boolean listHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                guiGraphics.drawString(this.font, display, controlX, y + 8,
                        listHovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
        }
    }

    private void drawSmallText(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(SMALL_SCALE, SMALL_SCALE, 1.0f);
        guiGraphics.drawString(this.font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (itemPickerOverlay != null && itemPickerOverlay.isVisible()) {
            if (itemPickerOverlay.mouseClicked(mouseX, mouseY))
                return true;
        }
        // Check tab clicks
        if (mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
            for (int i = 0; i < TAB_KEYS.length; i++) {
                if (mouseX >= tabX[i] && mouseX < tabX[i] + tabW[i]) {
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    switchTab(i);
                    return true;
                }
            }
        }

        if (super.mouseClicked(mouseX, mouseY, button))
            return true;

        int listTop = HEADER_HEIGHT;
        int listBottom = this.height - 40;
        int contentLeft = 30;
        int contentRight = this.width - 30;

        // Scrollbar click
        if (maxScroll > 0 && mouseX >= contentRight + 1 && mouseX <= contentRight + 6
                && mouseY >= listTop && mouseY <= listBottom) {
            draggingScrollbar = true;
            updateScrollFromMouse(mouseY, listTop, listBottom);
            return true;
        }

        if (mouseX < contentLeft - 10 || mouseX > contentRight + 10 || mouseY < listTop || mouseY > listBottom)
            return false;

        int y = listTop - (int) scrollOffset;
        List<ConfigSection> sections = getActiveSections();

        for (ConfigSection section : sections) {
            y += SECTION_HEADER_HEIGHT;

            for (ConfigEntry entry : section.entries) {
                if (mouseY >= y && mouseY < y + ENTRY_HEIGHT) {
                    // Calculate controlX the same way as render
                    String label = Component.translatable("editor.historystages.config." + entry.key).getString();
                    int labelWidth = this.font.width(label);
                    int controlX = contentLeft + Math.max(labelWidth + 20, 180);
                    if (mouseX >= controlX && mouseX <= contentRight - 5) {
                        Minecraft.getInstance().getSoundManager()
                                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                        handleEntryClick(entry);
                        return true;
                    }
                }
                y += ENTRY_HEIGHT;
            }
            y += SECTION_GAP;
        }

        return false;
    }

    // Item list overlay for ITEM_LIST config entries
    private SearchableItemList itemListOverlay;
    private ConfigEntry editingItemListEntry;
    private List<String> editingItemList;

    // Single-item picker overlay for ITEM config entries
    private SearchableItemList itemPickerOverlay;
    private ConfigEntry pickingItemEntry;

    private ItemStack resolveItemStack(String id) {
        if (id != null && !id.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null) {
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    return new ItemStack(item);
                }
            }
        }
        return new ItemStack(net.bananemdnsa.historystages.init.ModItems.RESEARCH_SCROLL.get());
    }

    private void openItemPicker(ConfigEntry entry) {
        pickingItemEntry = entry;
        itemPickerOverlay = new SearchableItemList(itemId -> {
            if (pickingItemEntry != null) {
                pickingItemEntry.value = itemId;
            }
        });
        itemPickerOverlay.setFilter("");
        itemPickerOverlay.show(this.width / 2, this.height / 2, this.width);
    }

    private void handleEntryClick(ConfigEntry entry) {
        switch (entry.type) {
            case BOOLEAN -> {
                boolean current = Boolean.parseBoolean(entry.value);
                entry.value = String.valueOf(!current);
            }
            case INTEGER -> this.minecraft.setScreen(new ValueInputScreen(this, entry, true));
            case STRING -> this.minecraft.setScreen(new ValueInputScreen(this, entry, false));
            case ITEM -> openItemPicker(entry);
            case ITEM_LIST -> this.minecraft.setScreen(new ItemListEditorScreen(this, entry));
            case TAG_LIST -> this.minecraft.setScreen(new TagListEditorScreen(this, entry));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (itemPickerOverlay != null && itemPickerOverlay.isVisible()
                && itemPickerOverlay.mouseScrolled(mouseX, mouseY, delta))
            return true;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 16));
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (itemPickerOverlay != null && itemPickerOverlay.isVisible()
                && itemPickerOverlay.mouseDragged(mouseX, mouseY))
            return true;
        if (draggingScrollbar) {
            int listTop = HEADER_HEIGHT;
            int listBottom = this.height - 40;
            updateScrollFromMouse(mouseY, listTop, listBottom);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (itemPickerOverlay != null && itemPickerOverlay.isVisible() && itemPickerOverlay.mouseReleased())
            return true;
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateScrollFromMouse(double mouseY, int listTop, int listBottom) {
        int scrollAreaHeight = listBottom - listTop;
        float ratio = (float) Math.max(0, Math.min(1, (mouseY - listTop) / (double) scrollAreaHeight));
        scrollOffset = Math.round(ratio * maxScroll);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (itemPickerOverlay != null && itemPickerOverlay.isVisible() && itemPickerOverlay.keyPressed(keyCode))
            return true;
        if (keyCode == 256) { // ESC
            tryClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (itemPickerOverlay != null && itemPickerOverlay.isVisible() && itemPickerOverlay.charTyped(c))
            return true;
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private void tryClose() {
        if (hasChanges()) {
            this.minecraft.setScreen(new ConfirmDialog(
                    parent,
                    Component.translatable("editor.historystages.unsaved_warning_title"),
                    Component.translatable("editor.historystages.unsaved_warning"),
                    () -> {
                    }));
        } else {
            this.minecraft.setScreen(parent);
        }
    }

    private void saveConfig() {
        // Save client config locally
        Map<String, String> clientValues = new HashMap<>();
        for (ConfigSection section : clientSections) {
            for (ConfigEntry entry : section.entries) {
                clientValues.put(entry.key, entry.value);
            }
        }
        applyClientConfig(clientValues);

        // Send common config to server
        Map<String, String> commonValues = new HashMap<>();
        for (ConfigSection section : commonSections) {
            for (ConfigEntry entry : section.entries) {
                commonValues.put(entry.key, entry.value);
            }
        }
        PacketHandler.sendToServer(new SaveConfigPacket(commonValues, false));

        // Update initial values so hasChanges() returns false
        List<ConfigSection> allSections = new ArrayList<>();
        allSections.addAll(clientSections);
        allSections.addAll(commonSections);
        for (ConfigSection section : allSections) {
            for (ConfigEntry entry : section.entries) {
                entry.initialValue = entry.value;
            }
        }
    }

    private void applyClientConfig(Map<String, String> values) {
        for (Map.Entry<String, String> e : values.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            switch (key) {
                case "showTooltips" -> Config.CLIENT.showTooltips.set(Boolean.parseBoolean(value));
                case "showStageName" -> Config.CLIENT.showStageName.set(Boolean.parseBoolean(value));
                case "showAllUntilComplete" -> Config.CLIENT.showAllUntilComplete.set(Boolean.parseBoolean(value));
                case "showLockIcons" -> Config.CLIENT.showLockIcons.set(Boolean.parseBoolean(value));
                case "dimUseActionbar" -> Config.CLIENT.dimUseActionbar.set(Boolean.parseBoolean(value));
                case "dimShowChat" -> Config.CLIENT.dimShowChat.set(Boolean.parseBoolean(value));
                case "dimShowStagesInChat" -> Config.CLIENT.dimShowStagesInChat.set(Boolean.parseBoolean(value));
                case "jadeShowInfo" -> Config.CLIENT.jadeShowInfo.set(Boolean.parseBoolean(value));
                case "jadeStageName" -> Config.CLIENT.jadeStageName.set(Boolean.parseBoolean(value));
                case "jadeShowAllUntilComplete" ->
                    Config.CLIENT.jadeShowAllUntilComplete.set(Boolean.parseBoolean(value));
                case "mobUseActionbar" -> Config.CLIENT.mobUseActionbar.set(Boolean.parseBoolean(value));
                case "mobShowChat" -> Config.CLIENT.mobShowChat.set(Boolean.parseBoolean(value));
                case "mobShowStagesInChat" -> Config.CLIENT.mobShowStagesInChat.set(Boolean.parseBoolean(value));
                case "showSilverLockIcons" -> Config.CLIENT.showSilverLockIcons.set(Boolean.parseBoolean(value));
                case "showIndividualTooltips" -> Config.CLIENT.showIndividualTooltips.set(Boolean.parseBoolean(value));
                case "showDependenciesOnScroll" ->
                    Config.CLIENT.showDependenciesOnScroll.set(Boolean.parseBoolean(value));
                case "hideFulfilledDependencies" ->
                    Config.CLIENT.hideFulfilledDependencies.set(Boolean.parseBoolean(value));
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // --- Inner data classes ---

    enum ConfigType {
        BOOLEAN, INTEGER, STRING, ITEM, ITEM_LIST, TAG_LIST
    }

    static class ConfigEntry {
        final String key;
        final ConfigType type;
        String value;
        String initialValue;
        final boolean isClient;
        final String defaultValue;
        final String description;

        ConfigEntry(String key, ConfigType type, String value, boolean isClient, String defaultValue,
                String description) {
            this.key = key;
            this.type = type;
            this.value = value;
            this.initialValue = value;
            this.isClient = isClient;
            this.defaultValue = defaultValue;
            this.description = description;
        }
    }

    static class ConfigSection {
        final String titleKey;
        final List<ConfigEntry> entries = new ArrayList<>();

        ConfigSection(String titleKey) {
            this.titleKey = titleKey;
        }

        void add(ConfigEntry entry) {
            entries.add(entry);
        }
    }

    /**
     * Screen for editing an ITEM_LIST config entry (e.g. replacementItems).
     * Shows a list of current items with remove buttons and an add button using
     * SearchableItemList overlay.
     */
    static class ItemListEditorScreen extends Screen {
        private final ConfigEditorScreen parent;
        private final ConfigEntry entry;
        private final List<String> items;
        private SearchableItemList itemOverlay;
        private double scrollOffset = 0;
        private int maxScroll = 0;
        private boolean draggingScrollbar = false;
        private static final int ITEM_ROW_HEIGHT = 22;
        private static final int LIST_TOP = 50;

        ItemListEditorScreen(ConfigEditorScreen parent, ConfigEntry entry) {
            super(Component.translatable("editor.historystages.config." + entry.key));
            this.parent = parent;
            this.entry = entry;
            this.items = new ArrayList<>();
            if (!entry.value.isEmpty()) {
                for (String s : entry.value.split(",")) {
                    String trimmed = s.trim();
                    if (!trimmed.isEmpty())
                        items.add(trimmed);
                }
            }
        }

        @Override
        protected void init() {
            // Back button
            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.back"),
                    btn -> saveAndClose(), 10, this.height - 30, 60, 20));

            // Add button
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
                    }, this.width / 2 - 50, this.height - 30, 100, 20));

            updateMaxScroll();
        }

        private void updateMaxScroll() {
            int listBottom = this.height - 40;
            int contentHeight = items.size() * ITEM_ROW_HEIGHT;
            int visibleHeight = listBottom - LIST_TOP;
            maxScroll = Math.max(0, contentHeight - visibleHeight);
            scrollOffset = Math.min(scrollOffset, maxScroll);
        }

        private void saveAndClose() {
            entry.value = String.join(",", items);

            this.minecraft.setScreen(parent);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);

            // Title
            guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

            // Subtitle
            guiGraphics.drawCenteredString(this.font, items.size() + " items", this.width / 2, 25, 0x999999);

            // Separator
            guiGraphics.fill(30, LIST_TOP - 4, this.width - 30, LIST_TOP - 3, 0xFF555555);

            int listBottom = this.height - 40;
            int contentLeft = 40;
            int contentRight = this.width - 40;

            guiGraphics.enableScissor(contentLeft - 5, LIST_TOP, contentRight + 5, listBottom);

            int y = LIST_TOP - (int) scrollOffset;
            for (int i = 0; i < items.size(); i++) {
                if (y + ITEM_ROW_HEIGHT > LIST_TOP - 10 && y < listBottom + 10) {
                    String itemId = items.get(i);
                    boolean hovered = mouseX >= contentLeft && mouseX <= contentRight
                            && mouseY >= y && mouseY < y + ITEM_ROW_HEIGHT
                            && mouseY >= LIST_TOP && mouseY <= listBottom;

                    if (hovered) {
                        guiGraphics.fill(contentLeft, y, contentRight, y + ITEM_ROW_HEIGHT, 0x20FFFFFF);
                    }

                    // Item icon
                    ResourceLocation rl = ResourceLocation.tryParse(itemId);
                    if (rl != null) {
                        Item item = ForgeRegistries.ITEMS.getValue(rl);
                        if (item != null) {
                            guiGraphics.renderItem(new ItemStack(item), contentLeft + 2, y + 2);
                        }
                    }

                    // Item ID text
                    guiGraphics.drawString(this.font, itemId, contentLeft + 22, y + 7, 0xCCCCCC, false);

                    // Remove × button
                    int removeX = contentRight - 14;
                    boolean removeHovered = mouseX >= removeX && mouseX <= removeX + 12
                            && mouseY >= y + 2 && mouseY < y + ITEM_ROW_HEIGHT - 2
                            && mouseY >= LIST_TOP && mouseY <= listBottom;
                    guiGraphics.drawString(this.font, "×", removeX + 2, y + 6,
                            removeHovered ? 0xFF5555 : 0x888888, false);
                }
                y += ITEM_ROW_HEIGHT;
            }

            guiGraphics.disableScissor();

            // Scrollbar
            if (maxScroll > 0) {
                int scrollAreaHeight = listBottom - LIST_TOP;
                int barHeight = Math.max(20,
                        (int) ((float) scrollAreaHeight / (maxScroll + scrollAreaHeight) * scrollAreaHeight));
                int barY = LIST_TOP + (int) ((float) scrollOffset / maxScroll * (scrollAreaHeight - barHeight));
                guiGraphics.fill(contentRight + 2, barY, contentRight + 5, barY + barHeight, 0x80FFFFFF);
            }

            // Separator above buttons
            guiGraphics.fill(30, listBottom + 1, this.width - 30, listBottom + 2, 0xFF555555);

            super.render(guiGraphics, mouseX, mouseY, partialTick);

            // Overlay
            if (itemOverlay != null) {
                guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);
                itemOverlay.render(guiGraphics, this.font, mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (itemOverlay != null) {
                if (itemOverlay.mouseClicked(mouseX, mouseY))
                    return true;
                // Click outside overlay closes it
                itemOverlay = null;
                return true;
            }

            if (super.mouseClicked(mouseX, mouseY, button))
                return true;

            int listBottom = this.height - 40;
            int contentLeft = 40;
            int contentRight = this.width - 40;

            // Scrollbar click
            if (maxScroll > 0 && mouseX >= contentRight + 1 && mouseX <= contentRight + 6
                    && mouseY >= LIST_TOP && mouseY <= listBottom) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, LIST_TOP, listBottom);
                return true;
            }

            // Check remove button clicks
            if (mouseX < contentLeft || mouseX > contentRight || mouseY < LIST_TOP || mouseY > listBottom)
                return false;

            int y = LIST_TOP - (int) scrollOffset;
            for (int i = 0; i < items.size(); i++) {
                if (mouseY >= y && mouseY < y + ITEM_ROW_HEIGHT) {
                    int removeX = contentRight - 14;
                    if (mouseX >= removeX && mouseX <= removeX + 12) {
                        items.remove(i);
                        updateMaxScroll();
                        return true;
                    }
                }
                y += ITEM_ROW_HEIGHT;
            }

            return false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (draggingScrollbar) {
                updateScrollFromMouse(mouseY, LIST_TOP, this.height - 40);
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

        private void updateScrollFromMouse(double mouseY, int listTop, int listBottom) {
            int scrollAreaHeight = listBottom - listTop;
            float ratio = (float) Math.max(0, Math.min(1, (mouseY - listTop) / (double) scrollAreaHeight));
            scrollOffset = Math.round(ratio * maxScroll);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (itemOverlay != null)
                return itemOverlay.mouseScrolled(mouseX, mouseY, delta);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 16));
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
            if (itemOverlay != null)
                return itemOverlay.charTyped(c);
            return super.charTyped(c, modifiers);
        }

        @Override
        public void onClose() {
            saveAndClose();
        }

        @Override
        public boolean isPauseScreen() {
            return true;
        }
    }

    /**
     * Screen for editing a TAG_LIST config entry (e.g. replacementTags).
     * Shows a list of current tags with remove buttons and an add button using
     * SearchableTagList overlay.
     */
    static class TagListEditorScreen extends Screen {
        private final ConfigEditorScreen parent;
        private final ConfigEntry entry;
        private final List<String> tags;
        private SearchableTagList tagOverlay;
        private double scrollOffset = 0;
        private int maxScroll = 0;
        private boolean draggingScrollbar = false;
        private static final int TAG_ROW_HEIGHT = 22;
        private static final int LIST_TOP = 50;

        TagListEditorScreen(ConfigEditorScreen parent, ConfigEntry entry) {
            super(Component.translatable("editor.historystages.config." + entry.key));
            this.parent = parent;
            this.entry = entry;
            this.tags = new ArrayList<>();
            if (!entry.value.isEmpty()) {
                for (String s : entry.value.split(",")) {
                    String trimmed = s.trim();
                    if (!trimmed.isEmpty())
                        tags.add(trimmed);
                }
            }
        }

        @Override
        protected void init() {
            // Back button
            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.back"),
                    btn -> saveAndClose(), 10, this.height - 30, 60, 20));

            // Add button
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
                    }, this.width / 2 - 50, this.height - 30, 100, 20));

            updateMaxScroll();
        }

        private void updateMaxScroll() {
            int listBottom = this.height - 40;
            int contentHeight = tags.size() * TAG_ROW_HEIGHT;
            int visibleHeight = listBottom - LIST_TOP;
            maxScroll = Math.max(0, contentHeight - visibleHeight);
            scrollOffset = Math.min(scrollOffset, maxScroll);
        }

        private void saveAndClose() {
            entry.value = String.join(",", tags);

            this.minecraft.setScreen(parent);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);

            // Title
            guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

            // Subtitle
            guiGraphics.drawCenteredString(this.font, tags.size() + " tags", this.width / 2, 25, 0x999999);

            // Separator
            guiGraphics.fill(30, LIST_TOP - 4, this.width - 30, LIST_TOP - 3, 0xFF555555);

            int listBottom = this.height - 40;
            int contentLeft = 40;
            int contentRight = this.width - 40;

            guiGraphics.enableScissor(contentLeft - 5, LIST_TOP, contentRight + 5, listBottom);

            int y = LIST_TOP - (int) scrollOffset;
            for (int i = 0; i < tags.size(); i++) {
                if (y + TAG_ROW_HEIGHT > LIST_TOP - 10 && y < listBottom + 10) {
                    String tagId = tags.get(i);
                    boolean hovered = mouseX >= contentLeft && mouseX <= contentRight
                            && mouseY >= y && mouseY < y + TAG_ROW_HEIGHT
                            && mouseY >= LIST_TOP && mouseY <= listBottom;

                    if (hovered) {
                        guiGraphics.fill(contentLeft, y, contentRight, y + TAG_ROW_HEIGHT, 0x20FFFFFF);
                    }

                    // Tag icon (#)
                    guiGraphics.drawString(this.font, "\u00A7e#", contentLeft + 4, y + 7, 0xFFCC00, false);

                    // Tag ID text
                    guiGraphics.drawString(this.font, tagId, contentLeft + 16, y + 7, 0xCCCCCC, false);

                    // Remove × button
                    int removeX = contentRight - 14;
                    boolean removeHovered = mouseX >= removeX && mouseX <= removeX + 12
                            && mouseY >= y + 2 && mouseY < y + TAG_ROW_HEIGHT - 2
                            && mouseY >= LIST_TOP && mouseY <= listBottom;
                    guiGraphics.drawString(this.font, "×", removeX + 2, y + 6,
                            removeHovered ? 0xFF5555 : 0x888888, false);
                }
                y += TAG_ROW_HEIGHT;
            }

            guiGraphics.disableScissor();

            // Scrollbar
            if (maxScroll > 0) {
                int scrollAreaHeight = listBottom - LIST_TOP;
                int barHeight = Math.max(20,
                        (int) ((float) scrollAreaHeight / (maxScroll + scrollAreaHeight) * scrollAreaHeight));
                int barY = LIST_TOP + (int) ((float) scrollOffset / maxScroll * (scrollAreaHeight - barHeight));
                guiGraphics.fill(contentRight + 2, barY, contentRight + 5, barY + barHeight, 0x80FFFFFF);
            }

            // Separator above buttons
            guiGraphics.fill(30, listBottom + 1, this.width - 30, listBottom + 2, 0xFF555555);

            super.render(guiGraphics, mouseX, mouseY, partialTick);

            // Overlay
            if (tagOverlay != null) {
                guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);
                tagOverlay.render(guiGraphics, this.font, mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (tagOverlay != null) {
                if (tagOverlay.mouseClicked(mouseX, mouseY))
                    return true;
                tagOverlay = null;
                return true;
            }

            if (super.mouseClicked(mouseX, mouseY, button))
                return true;

            int listBottom = this.height - 40;
            int contentLeft = 40;
            int contentRight = this.width - 40;

            // Scrollbar click
            if (maxScroll > 0 && mouseX >= contentRight + 1 && mouseX <= contentRight + 6
                    && mouseY >= LIST_TOP && mouseY <= listBottom) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, LIST_TOP, listBottom);
                return true;
            }

            if (mouseX < contentLeft || mouseX > contentRight || mouseY < LIST_TOP || mouseY > listBottom)
                return false;

            int y = LIST_TOP - (int) scrollOffset;
            for (int i = 0; i < tags.size(); i++) {
                if (mouseY >= y && mouseY < y + TAG_ROW_HEIGHT) {
                    int removeX = contentRight - 14;
                    if (mouseX >= removeX && mouseX <= removeX + 12) {
                        tags.remove(i);
                        updateMaxScroll();
                        return true;
                    }
                }
                y += TAG_ROW_HEIGHT;
            }

            return false;
        }

        @Override
        public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
            if (draggingScrollbar) {
                updateScrollFromMouse(mouseY, LIST_TOP, this.height - 40);
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

        private void updateScrollFromMouse(double mouseY, int listTop, int listBottom) {
            int scrollAreaHeight = listBottom - listTop;
            float ratio = (float) Math.max(0, Math.min(1, (mouseY - listTop) / (double) scrollAreaHeight));
            scrollOffset = Math.round(ratio * maxScroll);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (tagOverlay != null)
                return tagOverlay.mouseScrolled(mouseX, mouseY, delta);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 16));
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
            if (tagOverlay != null)
                return tagOverlay.charTyped(c);
            return super.charTyped(c, modifiers);
        }

        @Override
        public void onClose() {
            saveAndClose();
        }

        @Override
        public boolean isPauseScreen() {
            return true;
        }
    }

    /**
     * Modal dialog for editing integer or string config values.
     */
    static class ValueInputScreen extends Screen {
        private final ConfigEditorScreen parent;
        private final ConfigEntry entry;
        private final boolean numericOnly;
        private EditBox inputField;

        ValueInputScreen(ConfigEditorScreen parent, ConfigEntry entry, boolean numericOnly) {
            super(Component.translatable("editor.historystages.config." + entry.key));
            this.parent = parent;
            this.entry = entry;
            this.numericOnly = numericOnly;
        }

        @Override
        protected void init() {
            int centerX = this.width / 2;
            int centerY = this.height / 2;

            inputField = new EditBox(this.font, centerX - 100, centerY - 10, 200, 20,
                    Component.translatable("editor.historystages.config." + entry.key));
            inputField.setMaxLength(256);
            inputField.setValue(entry.value);
            inputField.setFocused(true);
            if (numericOnly) {
                inputField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
            }
            this.addRenderableWidget(inputField);
            this.setFocused(inputField);

            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.confirm"),
                    btn -> applyAndClose(), centerX - 105, centerY + 20, 100, 20));

            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.cancel"),
                    btn -> this.minecraft.setScreen(parent), centerX + 5, centerY + 20, 100, 20));
        }

        private void applyAndClose() {
            String value = inputField.getValue();
            if (numericOnly) {
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return;
                }
            }
            entry.value = value;

            this.minecraft.setScreen(parent);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == 257) { // ENTER
                applyAndClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            guiGraphics.fill(0, 0, this.width, this.height, 0xC0000000);

            int boxW = 260;
            int boxH = 100;
            int boxX = (this.width - boxW) / 2;
            int boxY = (this.height - boxH) / 2 - 10;
            guiGraphics.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xFF2D2D2D);
            guiGraphics.fill(boxX + 1, boxY + 1, boxX + boxW - 1, boxY + boxH - 1, 0xFF1A1A1A);

            String label = Component.translatable("editor.historystages.config." + entry.key).getString();
            guiGraphics.drawCenteredString(this.font, label, this.width / 2, boxY + 8, 0xFFFFFF);

            super.render(guiGraphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(parent);
        }
    }
}
