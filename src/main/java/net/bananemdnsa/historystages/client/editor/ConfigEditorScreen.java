package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.dialog.AbstractInputScreen;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputField;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputValues;
import net.bananemdnsa.historystages.network.CommonConfigSync;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SaveConfigPacket;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import net.bananemdnsa.historystages.client.editor.widget.list.SearchableItemList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableTagList;

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
    /**
     * The editor the incoming config sync should refresh, if one is open. Weak on purpose: nothing
     * here owns the screen's lifecycle, and clearing it reliably would mean hooking every path that
     * navigates away — including the dialogs that keep this screen as their parent and hand control
     * back to it. Letting it go stale is harmless; a refresh on a discarded instance mutates fields
     * nobody reads.
     */
    private static java.lang.ref.WeakReference<ConfigEditorScreen> active =
            new java.lang.ref.WeakReference<>(null);

    private final Screen parent;

    // Tab state: 0 = Client, 1 = Common
    private int activeTab = 1;

    // Scrolling
    private double scrollOffset = 0;
    /** Sub-pixel scroll chasing {@link #scrollOffset}; render and the click paths both read it. */
    private final Anim smoothScroll = new Anim();
    /** Per-row hover progress, keyed by row index. */
    private final Map<Integer, Anim> rowHover = new HashMap<>();
    /** Per-tab hover progress, indexed by tab position. */
    private final Map<Integer, Anim> tabHover = new HashMap<>();
    /** Hover progress per config entry, keyed by the entry's config key. */
    private final Map<String, Anim> entryHover = new HashMap<>();
    private final Anim scrollThumbHover = new Anim();
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
    private static final long TOOLTIP_DELAY_MS = Timing.TOOLTIP_DELAY_MS;

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
        // Build once per instance, not once per init(): init() also runs on every window resize,
        // and rebuilding there would throw away whatever the admin has typed but not saved yet.
        // Staying stale is instead handled by onCommonConfigSynced().
        if (clientSections == null)
            buildConfigEntries();

        active = new java.lang.ref.WeakReference<>(this);

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
        visuals.add(new ConfigEntry("showBoosterTooltips", ConfigType.BOOLEAN,
                Config.CLIENT.showBoosterTooltips.get().toString(), true, "true",
                "Show a tooltip on Research Pedestal booster blocks describing their speed/cost effect?"));
        visuals.add(new ConfigEntry("showScrollTierTooltip", ConfigType.BOOLEAN,
                Config.CLIENT.showScrollTierTooltip.get().toString(), true, "true",
                "Show the minimum required Pedestal tier on Research Scroll tooltips?"));
        visuals.add(new ConfigEntry("showLockIcons", ConfigType.BOOLEAN,
                Config.CLIENT.showLockIcons.get().toString(), true, "true",
                "Show a lock icon overlay on locked items in JEI and Inventories?"));
        clientSections.add(visuals);

        ConfigSection structureVisuals = new ConfigSection("editor.historystages.config.structure_visuals");
        structureVisuals.add(new ConfigEntry("structureBorderEnabled", ConfigType.BOOLEAN,
                Config.CLIENT.structureBorderEnabled.get().toString(), true, "true",
                "Render a red force-field overlay on the walls of locked structures as you approach them?"));
        structureVisuals.add(new ConfigEntry("structureBorderDistance", ConfigType.DOUBLE,
                Config.CLIENT.structureBorderDistance.get().toString(), true, "8.0",
                "How close (in blocks) to a locked structure wall before the border becomes visible.",
                1.0, 32.0));
        structureVisuals.add(new ConfigEntry("structureLockOverlayEnabled", ConfigType.BOOLEAN,
                Config.CLIENT.structureLockOverlayEnabled.get().toString(), true, "true",
                "While standing inside a locked structure, tint the whole screen red?"));
        structureVisuals.add(new ConfigEntry("structureLockOverlayOpacity", ConfigType.DOUBLE,
                Config.CLIENT.structureLockOverlayOpacity.get().toString(), true, "0.30",
                "Opacity of the red lock-overlay (0.0 = invisible, 1.0 = fully opaque).",
                0.0, 1.0));
        clientSections.add(structureVisuals);

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

        // JEI hiding (Issue #64)
        ConfigSection jeiHiding = new ConfigSection("editor.historystages.config.jei_hiding");
        jeiHiding.add(new ConfigEntry("hideLockedItemsInJei", ConfigType.BOOLEAN,
                Config.CLIENT.hideLockedItemsInJei.get().toString(), true, "false",
                "Remove locked items from the JEI ingredient panel entirely."));
        jeiHiding.add(new ConfigEntry("hideLockedRecipesInJei", ConfigType.BOOLEAN,
                Config.CLIENT.hideLockedRecipesInJei.get().toString(), true, "false",
                "Hide recipes whose OUTPUT is a locked item in JEI."));
        jeiHiding.add(new ConfigEntry("lockedItemMultiStagePolicy", ConfigType.MULTI_STAGE_POLICY,
                Config.CLIENT.lockedItemMultiStagePolicy.get().name(), true, "STRICT",
                "Multi-stage policy: STRICT (hide while any stage locked) or LENIENT (show when any unlocked)."));
        clientSections.add(jeiHiding);

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
        gameplay.add(new ConfigEntry("lockedBlockBreakSpeedMultiplier", ConfigType.DOUBLE,
                Config.COMMON.lockedBlockBreakSpeedMultiplier.get().toString(), false, "0.05",
                "Break speed multiplier for locked blocks (0.001-1.0). Lower = slower.",
                0.001, 1.0));
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
        individualCommon.add(new ConfigEntry("individualLockLoot", ConfigType.BOOLEAN,
                Config.COMMON.individualLockLoot.get().toString(), false, "true",
                "Handle items locked by individual stages in Lootr containers and mob loot? Mob drops are checked against the killing player."));
        individualCommon.add(new ConfigEntry("individualDropOnRevoke", ConfigType.BOOLEAN,
                Config.COMMON.individualDropOnRevoke.get().toString(), false, "true",
                "Drop locked items from a player's inventory when their individual stage is revoked?"));
        individualCommon.add(new ConfigEntry("individualLockBlockBreaking", ConfigType.BOOLEAN,
                Config.COMMON.individualLockBlockBreaking.get().toString(), false, "true",
                "Make blocks locked by individual stages much harder to break and prevent their drops?"));
        individualCommon.add(new ConfigEntry("individualLockedBlockBreakSpeedMultiplier", ConfigType.DOUBLE,
                Config.COMMON.individualLockedBlockBreakSpeedMultiplier.get().toString(), false, "0.05",
                "Break speed multiplier for blocks locked by individual stages (0.001-1.0). Lower = slower.",
                0.001, 1.0));
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
        research.add(new ConfigEntry("researchBoosters", ConfigType.BOOSTER_LIST,
                encodeBoosterList(Config.COMMON.researchBoosters.get()), false, "",
                "Booster blocks placed under a Research Pedestal. Speed/cost values are percentages 0-90."));
        research.add(new ConfigEntry("researchTimeInSeconds", ConfigType.INTEGER,
                Config.COMMON.researchTimeInSeconds.get().toString(), false, "20",
                "Default research time in seconds. Used as fallback if a stage does not define its own.",
                1, 86400));
        research.add(new ConfigEntry("showDependencyScreenInPedestal", ConfigType.BOOLEAN,
                Config.COMMON.showDependencyScreenInPedestal.get().toString(), false, "true",
                "Show dependency checklist screen when interacting with pedestal that has dependency requirements?"));
        research.add(new ConfigEntry("lockScrollWhileResearching", ConfigType.BOOLEAN,
                Config.COMMON.lockScrollWhileResearching.get().toString(), false, "false",
                "Lock the scroll in the pedestal once research has started? Prevents players and hoppers from removing it."));
        commonSections.add(research);

        ConfigSection lootReplace = new ConfigSection("editor.historystages.config.loot_replacements");
        lootReplace.add(new ConfigEntry("useReplacements", ConfigType.BOOLEAN,
                Config.COMMON.useReplacements.get().toString(), false, "false",
                "If true, locked items are replaced by specific items/tags. If false, they disappear."));
        lootReplace.add(new ConfigEntry("replacementItems", ConfigType.ITEM_LIST,
                String.join(",", Config.COMMON.replacementItems.get()), false,
                "minecraft:cobblestone,minecraft:dirt",
                "List of item IDs to pick from as replacement (Priority 1). Click to manage."));
        lootReplace.add(new ConfigEntry("replacementTags", ConfigType.TAG_LIST,
                String.join(",", Config.COMMON.replacementTags.get()), false, "",
                "List of tags to pick a random replacement from (Priority 2). Click to manage."));
        commonSections.add(lootReplace);

        ConfigSection structureLock = new ConfigSection("editor.historystages.config.structure_lock");
        structureLock.add(new ConfigEntry("structureCheckInterval", ConfigType.INTEGER,
                Config.COMMON.structureCheckInterval.get().toString(), false, "10",
                "How often (in ticks) to check if a player is inside a locked structure.",
                1, 200));
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
        structureLock.add(new ConfigEntry("structureDamageAmount", ConfigType.DOUBLE,
                Config.COMMON.structureDamageAmount.get().toString(), false, "1.0",
                "Amount of damage dealt per damage tick.",
                0.1, 100.0));
        structureLock.add(new ConfigEntry("structureDamageInterval", ConfigType.INTEGER,
                Config.COMMON.structureDamageInterval.get().toString(), false, "20",
                "How often (in ticks) to deal damage while inside a locked structure.",
                1, 600));
        structureLock.add(new ConfigEntry("structureBlockRightClick", ConfigType.BOOLEAN,
                Config.COMMON.structureBlockRightClick.get().toString(), false, "true",
                "Cancel ALL right-click interactions (blocks, items, entities) while inside a locked structure?"));
        structureLock.add(new ConfigEntry("structureBlockLeftClick", ConfigType.BOOLEAN,
                Config.COMMON.structureBlockLeftClick.get().toString(), false, "true",
                "Cancel ALL left-click interactions (attacking entities, breaking blocks) while inside a locked structure?"));
        structureLock.add(new ConfigEntry("structureBlockProjectiles", ConfigType.BOOLEAN,
                Config.COMMON.structureBlockProjectiles.get().toString(), false, "true",
                "Cancel projectiles (arrows, snowballs, ender pearls, etc.) that would impact inside a locked structure?"));
        commonSections.add(structureLock);

        ConfigSection biomeLock = new ConfigSection("editor.historystages.config.biome_lock");
        biomeLock.add(new ConfigEntry("biomeCheckInterval", ConfigType.INTEGER,
                Config.COMMON.biomeCheckInterval.get().toString(), false, "10",
                "How often (in ticks) to re-check a player's biome even when they haven't moved to a new biome cell. Moving always re-checks immediately.",
                1, 200));
        biomeLock.add(new ConfigEntry("biomeEffectsEnabled", ConfigType.BOOLEAN,
                Config.COMMON.biomeEffectsEnabled.get().toString(), false, "true",
                "Apply the potion effects below while the player is inside a locked biome?"));
        biomeLock.add(new ConfigEntry("biomeEffects", ConfigType.EFFECT_LIST,
                encodeEffectList(Config.COMMON.biomeEffects.get()), false,
                "minecraft:blindness, 30, 0",
                "Potion effects applied inside a locked biome. Refreshed while the player stays inside, so the duration is really how long it lingers after leaving. Click to manage."));
        biomeLock.add(new ConfigEntry("biomeClearEffectsOnLeave", ConfigType.BOOLEAN,
                Config.COMMON.biomeClearEffectsOnLeave.get().toString(), false, "false",
                "Remove those effects the moment the player leaves the locked biome, instead of letting them run out?"));
        biomeLock.add(new ConfigEntry("biomeMessageEnabled", ConfigType.BOOLEAN,
                Config.COMMON.biomeMessageEnabled.get().toString(), false, "true",
                "Show the player a message while they are inside a locked biome?"));
        biomeLock.add(new ConfigEntry("biomeLockMessageFormat", ConfigType.STRING,
                Config.COMMON.biomeLockMessageFormat.get(), false,
                "&cYou cannot survive in &e{biome}&c yet!",
                "Message format for biome lock. Use {biome} for the ID, {stage} for the required stage, and & for colors."));
        biomeLock.add(new ConfigEntry("biomeLockInChat", ConfigType.BOOLEAN,
                Config.COMMON.biomeLockInChat.get().toString(), false, "false",
                "Show the biome lock message in chat as well (otherwise only actionbar)?"));
        biomeLock.add(new ConfigEntry("biomeDamageEnabled", ConfigType.BOOLEAN,
                Config.COMMON.biomeDamageEnabled.get().toString(), false, "true",
                "Damage the player while they are inside a locked biome?"));
        biomeLock.add(new ConfigEntry("biomeDamageAmount", ConfigType.DOUBLE,
                Config.COMMON.biomeDamageAmount.get().toString(), false, "1.0",
                "Amount of damage dealt per damage tick (0.1-100.0).",
                0.1, 100.0));
        biomeLock.add(new ConfigEntry("biomeDamageInterval", ConfigType.INTEGER,
                Config.COMMON.biomeDamageInterval.get().toString(), false, "20",
                "How often (in ticks) to deal damage while inside a locked biome.",
                1, 600));
        biomeLock.add(new ConfigEntry("biomeBlockRightClick", ConfigType.BOOLEAN,
                Config.COMMON.biomeBlockRightClick.get().toString(), false, "true",
                "Cancel ALL right-click interactions (blocks, items, entities) while inside a locked biome?"));
        biomeLock.add(new ConfigEntry("biomeBlockLeftClick", ConfigType.BOOLEAN,
                Config.COMMON.biomeBlockLeftClick.get().toString(), false, "true",
                "Cancel ALL left-click interactions (attacking entities, breaking blocks) while inside a locked biome?"));
        biomeLock.add(new ConfigEntry("biomeBlockProjectiles", ConfigType.BOOLEAN,
                Config.COMMON.biomeBlockProjectiles.get().toString(), false, "true",
                "Cancel projectiles (arrows, snowballs, ender pearls, etc.) that would impact inside a locked biome?"));
        commonSections.add(biomeLock);

        ConfigSection lockMessages = new ConfigSection("editor.historystages.config.lock_messages");
        lockMessages.add(new ConfigEntry("msgDimensionUnknown", ConfigType.STRING,
                Config.COMMON.msgDimensionUnknown.get(), false, "",
                "Override for the dimension lock actionbar message. Empty = use language file. Use & for color codes."));
        lockMessages.add(new ConfigEntry("msgMobUnknown", ConfigType.STRING,
                Config.COMMON.msgMobUnknown.get(), false, "",
                "Override for the mob lock actionbar message. Empty = use language file. Use & for color codes."));
        lockMessages.add(new ConfigEntry("msgItemLocked", ConfigType.STRING,
                Config.COMMON.msgItemLocked.get(), false, "",
                "Override for the item lock actionbar message. Empty = use language file. Use & for color codes."));
        lockMessages.add(new ConfigEntry("msgBlockLocked", ConfigType.STRING,
                Config.COMMON.msgBlockLocked.get(), false, "",
                "Override for the block lock actionbar message. Empty = use language file. Use & for color codes."));
        lockMessages.add(new ConfigEntry("msgEntityItemLocked", ConfigType.STRING,
                Config.COMMON.msgEntityItemLocked.get(), false, "",
                "Override for the armor stand / item frame lock actionbar message. Empty = use language file. Use & for color codes."));
        lockMessages.add(new ConfigEntry("msgEnchantmentLocked", ConfigType.STRING,
                Config.COMMON.msgEnchantmentLocked.get(), false, "",
                "Override for the enchantment lock actionbar message. Empty = use language file. Use & for color codes."));
        commonSections.add(lockMessages);
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
            smoothScroll.set(0.0f);
            updateMaxScroll();
            playClick();
        }
    }

    /**
     * The editor's single UI click. Every control that changes state routes through this, so
     * a config toggle sounds like a config toggle and not like nothing at all.
     */
    private static void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
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
        syncPickerState();

        smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScroll.settle((float) scrollOffset, 0.5f);

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

            float th = Ease.outCubic(tabHover.computeIfAbsent(i, k -> new Anim())
                    .ramp(hovered && !active, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
            guiGraphics.fill(tabX[i], tabY, tabX[i] + tabW[i], tabY + TAB_HEIGHT,
                    active ? 0x40FFCC00 : Fade.mix(0x15FFFFFF, 0x25FFFFFF, th));

            if (active) {
                guiGraphics.fill(tabX[i], tabY + TAB_HEIGHT - 2, tabX[i] + tabW[i], tabY + TAB_HEIGHT, 0xFFFFCC00);
            }

            String label = Component.translatable(TAB_KEYS[i]).getString();
            int textColor = active ? 0xFFFFFF : Fade.mix(0xFF999999, 0xFFDDDDDD, th);
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

        int y = listTop - Math.round(smoothScroll.value());
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
            int barY = listTop + Math.round(smoothScroll.value() / maxScroll * (scrollAreaHeight - barHeight));
            boolean barHovered = mouseX >= contentRight && mouseX <= contentRight + 7
                    && mouseY >= listTop && mouseY <= listBottom;
            float bh = Ease.outCubic(scrollThumbHover.ramp(barHovered,
                    Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
            guiGraphics.fill(contentRight + 2, listTop, contentRight + 5, listBottom, 0x20FFFFFF);
            guiGraphics.fill(contentRight + 2, barY, contentRight + 5, barY + barHeight,
                    Fade.mix(0x80FFFFFF, 0xFFFFCC00, bh));
        }

        // Unsaved changes indicator — yellow dot + text
        if (hasChanges()) {
            int dotX = this.width / 2 + 55;
            float phase = (System.currentTimeMillis() % (long) Timing.BREATHE_PERIOD_MS)
                    / Timing.BREATHE_PERIOD_MS;
            guiGraphics.fill(dotX, this.height - 25, dotX + 6, this.height - 19,
                    Fade.rgba(0xFFCC00, 0.4f + 0.6f * Ease.breathe(phase)));
            drawSmallText(guiGraphics, Component.translatable("editor.historystages.unsaved").getString(), dotX + 9,
                    this.height - 24, 0xFFCC00);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Item picker overlay (single-item selector for ITEM entries).
        // Lifted above everything drawn so far: text is batched and flushed after the picker's
        // panel fills, so the config rows and button labels underneath would otherwise bleed
        // through it. Same treatment the stage editor gives its own pickers.
        if (itemPickerOverlay != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 200);
            itemPickerOverlay.render(guiGraphics, this.font, mouseX, mouseY);
            guiGraphics.pose().popPose();
            // A config-row tooltip drawn on top of the picker makes no sense.
            currentHovered = null;
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
        // Keyed by the config key rather than the row index: sections collapse and the active
        // tab changes, so an index would carry one row's hover state over to a different setting.
        float hp = Ease.outCubic(entryHover.computeIfAbsent(entry.key, k -> new Anim())
                .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
        if (hp > 0.001f) {
            guiGraphics.fill(left, y, right, y + ENTRY_HEIGHT, Fade.rgba(0xFFFFFF, 0.082f * hp));
            // Gold edge, the same cue the stage list and the dropdowns use for a hovered row.
            guiGraphics.fill(left, y, left + 1, y + ENTRY_HEIGHT, Fade.rgba(0xFFCC00, hp * 0.8f));
        }

        // Label
        String label = Component.translatable("editor.historystages.config." + entry.key).getString();
        guiGraphics.drawString(this.font, label, left + 8 + Math.round(hp * 2.0f), y + 8,
                Fade.mix(0xFFCCCCCC, 0xFFFFFFFF, hp), false);

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
            case INTEGER, DOUBLE -> {
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
            case MULTI_STAGE_POLICY -> {
                boolean strict = !"LENIENT".equalsIgnoreCase(entry.value);
                String toggleText = strict ? "✔ STRICT" : "✘ LENIENT";
                int toggleColor = strict ? 0x55FF55 : 0xFFAA55;
                boolean toggleHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                if (toggleHovered) toggleColor = strict ? 0x88FF88 : 0xFFCC88;
                guiGraphics.drawString(this.font, toggleText, controlX, y + 8, toggleColor, false);
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
            case BOOSTER_LIST -> {
                int count = entry.value.isEmpty() ? 0 : entry.value.split(";").length;
                String display = "[" + count + " boosters] \u00A77(click to edit)";
                boolean listHovered = mouseX >= controlX && mouseX <= right - 5
                        && mouseY >= y + 2 && mouseY < y + ENTRY_HEIGHT - 2;
                guiGraphics.drawString(this.font, display, controlX, y + 8,
                        listHovered ? 0xFFCC00 : 0xDDDDDD, false);
            }
            case EFFECT_LIST -> {
                int count = entry.value.isEmpty() ? 0 : entry.value.split(";").length;
                String display = Component.translatable(
                        "editor.historystages.config.effects_summary", count).getString();
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
        // Item picker overlay
        syncPickerState();
        if (itemPickerOverlay != null) {
            boolean consumed = itemPickerOverlay.mouseClicked(mouseX, mouseY);
            syncPickerState();
            return consumed || closePicker();
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

        int y = listTop - Math.round(smoothScroll.value());
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

    // Single-item picker overlay for ITEM config entries
    private SearchableItemList itemPickerOverlay;
    private ConfigEntry pickingItemEntry;

    /**
     * Drops the picker reference once it has hidden itself. Clicking outside the panel makes the
     * list hide and still report the click as consumed, so without this the screen would keep a
     * non-null but invisible overlay and never lift the dim layer again.
     */
    private void syncPickerState() {
        if (itemPickerOverlay != null && !itemPickerOverlay.isVisible()) closePicker();
    }

    /** Closes the picker and reports the interaction as consumed. */
    private boolean closePicker() {
        itemPickerOverlay = null;
        pickingItemEntry = null;
        return true;
    }

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
        playClick();
        switch (entry.type) {
            case BOOLEAN -> {
                boolean current = Boolean.parseBoolean(entry.value);
                entry.value = String.valueOf(!current);
            }
            case INTEGER, DOUBLE, STRING -> this.minecraft.setScreen(new ValueInputScreen(this, entry));
            case ITEM -> openItemPicker(entry);
            case ITEM_LIST -> this.minecraft.setScreen(new ItemListEditorScreen(this, entry));
            case TAG_LIST -> this.minecraft.setScreen(new TagListEditorScreen(this, entry));
            case BOOSTER_LIST -> this.minecraft.setScreen(new BoosterListEditorScreen(this, entry));
            case EFFECT_LIST -> this.minecraft.setScreen(new EffectListEditorScreen(this, entry));
            case MULTI_STAGE_POLICY -> {
                boolean strict = !"LENIENT".equalsIgnoreCase(entry.value);
                entry.value = strict ? "LENIENT" : "STRICT";
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        syncPickerState();
        if (itemPickerOverlay != null && itemPickerOverlay.mouseScrolled(mouseX, mouseY, delta))
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
        // Snapped, not eased: while the thumb is held the list must track the
        // cursor exactly, or the thumb drifts from where the pointer is.
        smoothScroll.set((float) scrollOffset);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        syncPickerState();
        if (itemPickerOverlay != null) {
            if (keyCode == 256) return closePicker();
            boolean consumed = itemPickerOverlay.keyPressed(keyCode);
            syncPickerState();
            return consumed;
        }
        if (keyCode == 256) { // ESC
            tryClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        syncPickerState();
        if (itemPickerOverlay != null)
            return itemPickerOverlay.charTyped(c);
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /**
     * The dialog's parent is this screen, not {@link #parent}: {@code AbstractModalScreen.onCancel}
     * navigates to whatever it was given, so passing the screen behind would make Cancel the
     * button that discards the edits. Confirm has to navigate itself for the same reason —
     * {@code onConfirm} only runs the callback and leaves closing to it.
     */
    private void tryClose() {
        if (hasChanges()) {
            this.minecraft.setScreen(new ConfirmDialog(
                    this,
                    Component.translatable("editor.historystages.unsaved_warning_title"),
                    Component.translatable("editor.historystages.unsaved_warning"),
                    () -> this.minecraft.setScreen(parent)
            ));
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

        // JEI hiding (Issue #64): live-apply config changes if JEI is loaded.
        if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) {
            try {
                net.bananemdnsa.historystages.compat.jei.JEIPlugin.tryApplyDiff();
            } catch (Throwable ignored) {}
        }

        // EMI: rebuild its index so booster/config changes show up immediately.
        if (net.minecraftforge.fml.ModList.get().isLoaded("emi")) {
            try {
                net.bananemdnsa.historystages.compat.emi.EmiReloadBridge.reloadIfPresent();
            } catch (Throwable ignored) {}
        }
    }

    private void applyClientConfig(Map<String, String> values) {
        ClientConfigSync.applyAll(values);
        Config.CLIENT_SPEC.save();
    }

    /**
     * Called when the server pushed new common config values, so an editor that is already open
     * does not sit on a snapshot taken before another admin's save.
     * <p>
     * Save sends every common row, not just the edited ones, so without this a second admin who
     * changes one unrelated setting would push their whole stale snapshot back and quietly undo the
     * first admin's work.
     */
    public static void onCommonConfigSynced() {
        ConfigEditorScreen screen = active.get();
        if (screen != null && screen.commonSections != null) screen.refreshCommonValues();
    }

    /**
     * Merges the freshly synced values into the common rows. Rows the admin has not touched follow
     * the server; rows they have edited keep the edit and only get a new baseline, so the change
     * still counts as unsaved and still goes out on the next Save. Overwriting those too would
     * trade one admin losing work for the other.
     */
    private void refreshCommonValues() {
        Map<String, String> fresh = CommonConfigSync.readAll();
        for (ConfigSection section : commonSections) {
            for (ConfigEntry entry : section.entries) {
                String synced = fresh.get(entry.key);
                if (synced == null) continue;
                boolean untouched = entry.value.equals(entry.initialValue);
                entry.initialValue = synced;
                if (untouched) entry.value = synced;
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
        BOOLEAN, INTEGER, DOUBLE, STRING, ITEM, ITEM_LIST, TAG_LIST, MULTI_STAGE_POLICY, BOOSTER_LIST,
        EFFECT_LIST
    }

    /** Encode the live booster config list as the editor's internal string: "block,speed,cost;block,speed,cost". */
    private static String encodeBoosterList(java.util.List<? extends String> entries) {
        return entries.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.joining(";"));
    }

    /** Encode the live biome-effect config list as the editor's internal string: "id,seconds,amp;...". */
    private static String encodeEffectList(java.util.List<? extends String> entries) {
        return entries.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.joining(";"));
    }

    static class ConfigEntry {
        final String key;
        final ConfigType type;
        String value;
        String initialValue;
        final boolean isClient;
        final String defaultValue;
        final String description;
        /** Inclusive bounds for INTEGER / DOUBLE entries, mirroring Config.java's defineInRange. */
        final double min;
        final double max;

        ConfigEntry(String key, ConfigType type, String value, boolean isClient, String defaultValue,
                String description) {
            this(key, type, value, isClient, defaultValue, description,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        }

        ConfigEntry(String key, ConfigType type, String value, boolean isClient, String defaultValue,
                String description, double min, double max) {
            this.key = key;
            this.type = type;
            this.value = value;
            this.initialValue = value;
            this.isClient = isClient;
            this.defaultValue = defaultValue;
            this.description = description;
            this.min = min;
            this.max = max;
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
        private net.minecraft.client.gui.components.Button backButton;
        private net.minecraft.client.gui.components.Button addButton;
        private double scrollOffset = 0;
        /** Sub-pixel scroll chasing {@link #scrollOffset}; render and the click paths both read it. */
        private final Anim smoothScroll = new Anim();
        /** Per-row hover progress, keyed by row index. */
        private final Map<Integer, Anim> rowHover = new HashMap<>();
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
            backButton = StyledButton.of(
                    Component.translatable("editor.historystages.back"),
                    btn -> saveAndClose(), 10, this.height - 30, 60, 20);
            this.addRenderableWidget(backButton);

            // Add button
            addButton = StyledButton.of(
                    Component.translatable("editor.historystages.add"),
                    btn -> {
                        itemOverlay = new SearchableItemList(itemId -> {
                            if (!items.contains(itemId)) {
                                items.add(itemId);
                                updateMaxScroll();
                            }
                            itemOverlay = null;
                        }, () -> items);
                        itemOverlay.setMultiSelect(true);
                        itemOverlay.show(this.width / 2, this.height / 2, this.width);
                    }, this.width / 2 - 50, this.height - 30, 100, 20);
            this.addRenderableWidget(addButton);

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

        /**
         * Drops the picker reference once it has hidden itself. Clicking outside the panel
         * makes the list hide and still report the click as consumed, so without this the
         * screen would keep a non-null but invisible overlay — leaving the dim layer up and
         * the header hidden for good.
         */
        private void syncOverlayState() {
            if (itemOverlay != null && !itemOverlay.isVisible()) itemOverlay = null;
        }

        /** Closes the picker and reports the click as consumed. */
        private boolean clearOverlay() {
            itemOverlay = null;
            return true;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            syncOverlayState();
            smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
            smoothScroll.settle((float) scrollOffset, 0.5f);
            guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);

            // Skip the header while the picker is up so it doesn't bleed through the dim
            // layer. The footer buttons need the same treatment: vanilla batches text and
            // flushes it late, so their labels would draw on top of the panel even though
            // the panel is painted after them.
            boolean overlayOpen = itemOverlay != null;
            if (backButton != null) backButton.visible = !overlayOpen;
            if (addButton != null) addButton.visible = !overlayOpen;
            if (!overlayOpen) {
                // Title
                guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

                // Subtitle
                guiGraphics.drawCenteredString(this.font, items.size() + " items", this.width / 2, 25, 0x999999);

                // Separator
                guiGraphics.fill(30, LIST_TOP - 4, this.width - 30, LIST_TOP - 3, 0xFF555555);
            }

            int listBottom = this.height - 40;
            int contentLeft = 40;
            int contentRight = this.width - 40;

            guiGraphics.enableScissor(contentLeft - 5, LIST_TOP, contentRight + 5, listBottom);

            int y = LIST_TOP - Math.round(smoothScroll.value());
            for (int i = 0; i < items.size(); i++) {
                if (y + ITEM_ROW_HEIGHT > LIST_TOP - 10 && y < listBottom + 10) {
                    String itemId = items.get(i);
                    boolean hovered = mouseX >= contentLeft && mouseX <= contentRight
                            && mouseY >= y && mouseY < y + ITEM_ROW_HEIGHT
                            && mouseY >= LIST_TOP && mouseY <= listBottom;

                    float rh = Ease.outCubic(rowHover.computeIfAbsent(i, k -> new Anim())
                            .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
                    if (rh > 0.001f) {
                        guiGraphics.fill(contentLeft, y, contentRight, y + ITEM_ROW_HEIGHT, Fade.rgba(0xFFFFFF, 0.125f * rh));
                        guiGraphics.fill(contentLeft, y, contentLeft + 1, y + ITEM_ROW_HEIGHT, Fade.rgba(0xFFCC00, rh * 0.8f));
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
                int barY = LIST_TOP + Math.round(smoothScroll.value() / maxScroll * (scrollAreaHeight - barHeight));
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
            syncOverlayState();
            if (itemOverlay != null) {
                boolean consumed = itemOverlay.mouseClicked(mouseX, mouseY);
                syncOverlayState();
                // Click outside overlay closes it
                return consumed || clearOverlay();
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

            int y = LIST_TOP - Math.round(smoothScroll.value());
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
            // Snapped, not eased: while the thumb is held the list must track the
            // cursor exactly, or the thumb drifts from where the pointer is.
            smoothScroll.set((float) scrollOffset);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            syncOverlayState();
            if (itemOverlay != null)
                return itemOverlay.mouseScrolled(mouseX, mouseY, delta);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 16));
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            syncOverlayState();
            if (itemOverlay != null) {
                if (keyCode == 256) return clearOverlay();
                boolean consumed = itemOverlay.keyPressed(keyCode);
                syncOverlayState();
                return consumed;
            }
            if (keyCode == 256) {
                saveAndClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char c, int modifiers) {
            syncOverlayState();
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
        private net.minecraft.client.gui.components.Button backButton;
        private net.minecraft.client.gui.components.Button addButton;
        private double scrollOffset = 0;
        /** Sub-pixel scroll chasing {@link #scrollOffset}; render and the click paths both read it. */
        private final Anim smoothScroll = new Anim();
        /** Per-row hover progress, keyed by row index. */
        private final Map<Integer, Anim> rowHover = new HashMap<>();
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
            backButton = StyledButton.of(
                    Component.translatable("editor.historystages.back"),
                    btn -> saveAndClose(), 10, this.height - 30, 60, 20);
            this.addRenderableWidget(backButton);

            // Add button
            addButton = StyledButton.of(
                    Component.translatable("editor.historystages.add"),
                    btn -> {
                        tagOverlay = new SearchableTagList(tagId -> {
                            if (!tags.contains(tagId)) {
                                tags.add(tagId);
                                updateMaxScroll();
                            }
                            tagOverlay = null;
                        }, () -> tags);
                        tagOverlay.setMultiSelect(true);
                        tagOverlay.show(this.width / 2, this.height / 2, this.width);
                    }, this.width / 2 - 50, this.height - 30, 100, 20);
            this.addRenderableWidget(addButton);

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

        /**
         * Drops the picker reference once it has hidden itself. Clicking outside the panel
         * makes the list hide and still report the click as consumed, so without this the
         * screen would keep a non-null but invisible overlay — leaving the dim layer up and
         * the header hidden for good.
         */
        private void syncOverlayState() {
            if (tagOverlay != null && !tagOverlay.isVisible()) tagOverlay = null;
        }

        /** Closes the picker and reports the click as consumed. */
        private boolean clearOverlay() {
            tagOverlay = null;
            return true;
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            syncOverlayState();
            smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
            smoothScroll.settle((float) scrollOffset, 0.5f);
            guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);

            // Skip the header while the picker is up so it doesn't bleed through the dim
            // layer. The footer buttons need the same treatment: vanilla batches text and
            // flushes it late, so their labels would draw on top of the panel even though
            // the panel is painted after them.
            boolean overlayOpen = tagOverlay != null;
            if (backButton != null) backButton.visible = !overlayOpen;
            if (addButton != null) addButton.visible = !overlayOpen;
            if (!overlayOpen) {
                // Title
                guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

                // Subtitle
                guiGraphics.drawCenteredString(this.font, tags.size() + " tags", this.width / 2, 25, 0x999999);

                // Separator
                guiGraphics.fill(30, LIST_TOP - 4, this.width - 30, LIST_TOP - 3, 0xFF555555);
            }

            int listBottom = this.height - 40;
            int contentLeft = 40;
            int contentRight = this.width - 40;

            guiGraphics.enableScissor(contentLeft - 5, LIST_TOP, contentRight + 5, listBottom);

            int y = LIST_TOP - Math.round(smoothScroll.value());
            for (int i = 0; i < tags.size(); i++) {
                if (y + TAG_ROW_HEIGHT > LIST_TOP - 10 && y < listBottom + 10) {
                    String tagId = tags.get(i);
                    boolean hovered = mouseX >= contentLeft && mouseX <= contentRight
                            && mouseY >= y && mouseY < y + TAG_ROW_HEIGHT
                            && mouseY >= LIST_TOP && mouseY <= listBottom;

                    float rh = Ease.outCubic(rowHover.computeIfAbsent(i, k -> new Anim())
                            .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
                    if (rh > 0.001f) {
                        guiGraphics.fill(contentLeft, y, contentRight, y + TAG_ROW_HEIGHT, Fade.rgba(0xFFFFFF, 0.125f * rh));
                        guiGraphics.fill(contentLeft, y, contentLeft + 1, y + TAG_ROW_HEIGHT, Fade.rgba(0xFFCC00, rh * 0.8f));
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
                int barY = LIST_TOP + Math.round(smoothScroll.value() / maxScroll * (scrollAreaHeight - barHeight));
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
            syncOverlayState();
            if (tagOverlay != null) {
                boolean consumed = tagOverlay.mouseClicked(mouseX, mouseY);
                syncOverlayState();
                return consumed || clearOverlay();
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

            int y = LIST_TOP - Math.round(smoothScroll.value());
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
            // Snapped, not eased: while the thumb is held the list must track the
            // cursor exactly, or the thumb drifts from where the pointer is.
            smoothScroll.set((float) scrollOffset);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            syncOverlayState();
            if (tagOverlay != null)
                return tagOverlay.mouseScrolled(mouseX, mouseY, delta);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 16));
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            syncOverlayState();
            if (tagOverlay != null) {
                if (keyCode == 256) return clearOverlay();
                boolean consumed = tagOverlay.keyPressed(keyCode);
                syncOverlayState();
                return consumed;
            }
            if (keyCode == 256) {
                saveAndClose();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char c, int modifiers) {
            syncOverlayState();
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
     * Modal dialog for editing integer, decimal or string config values. Numeric entries get
     * the range declared on the ConfigEntry, so the dialog cannot produce a value that the
     * underlying ForgeConfigSpec would reject.
     */
    static class ValueInputScreen extends AbstractInputScreen {
        private final ConfigEditorScreen parent;
        private final ConfigEntry entry;

        ValueInputScreen(ConfigEditorScreen parent, ConfigEntry entry) {
            super(parent, Component.translatable("editor.historystages.config." + entry.key));
            this.parent = parent;
            this.entry = entry;
        }

        @Override
        protected List<InputField> fields() {
            return switch (entry.type) {
                case INTEGER -> List.of(InputField.number("value")
                        .range((int) entry.min, (int) entry.max).initial(entry.value));
                case DOUBLE -> List.of(InputField.decimal("value")
                        .range(entry.min, entry.max).initial(entry.value));
                default -> List.of(InputField.text("value").maxLength(256).initial(entry.value));
            };
        }

        @Override
        protected void onConfirm(InputValues values) {
            entry.value = values.getString("value");
            this.minecraft.setScreen(parent);
        }
    }

    /**
     * List editor for the {@code biomeEffects} config value. Rows are "effect_id, seconds,
     * amplifier"; duration and amplifier are edited through {@link EffectEditDialog} rather than
     * inline EditBoxes, matching the project's dialog convention.
     */
    static class EffectListEditorScreen extends Screen {
        private final ConfigEditorScreen parent;
        private final ConfigEntry entry;
        private final List<EffectRow> rows = new ArrayList<>();
        private net.bananemdnsa.historystages.client.editor.widget.list.SearchableEffectList effectOverlay;
        private net.minecraft.client.gui.components.Button backButton;
        private net.minecraft.client.gui.components.Button addButton;
        private double scrollOffset = 0;
        /** Sub-pixel scroll chasing {@link #scrollOffset}; render and the click paths both read it. */
        private final Anim smoothScroll = new Anim();
        /** Per-row hover progress, keyed by row index. */
        private final Map<Integer, Anim> rowHover = new HashMap<>();
        private int maxScroll = 0;
        private boolean draggingScrollbar = false;
        private static final int ROW_HEIGHT = 26;
        private static final int LIST_TOP = 50;

        EffectListEditorScreen(ConfigEditorScreen parent, ConfigEntry entry) {
            super(Component.translatable("editor.historystages.config." + entry.key));
            this.parent = parent;
            this.entry = entry;
            decode(entry.value);
        }

        private void decode(String value) {
            rows.clear();
            if (value == null || value.isEmpty()) return;
            for (String part : value.split(";")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                String[] tokens = trimmed.split(",");
                if (tokens.length != 3) continue;
                rows.add(new EffectRow(tokens[0].trim(),
                        clamp(tokens[1].trim(), 1, 3600, 30),
                        clamp(tokens[2].trim(), 0, 255, 0)));
            }
        }

        private static int clamp(String s, int min, int max, int fallback) {
            try {
                return Math.max(min, Math.min(max, Integer.parseInt(s)));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        @Override
        protected void init() {
            backButton = StyledButton.of(
                    Component.translatable("editor.historystages.back"),
                    btn -> saveAndClose(), 10, this.height - 30, 60, 20);
            this.addRenderableWidget(backButton);

            addButton = StyledButton.of(
                    Component.translatable("editor.historystages.add"),
                    btn -> openPicker(),
                    this.width / 2 - 50, this.height - 30, 100, 20);
            this.addRenderableWidget(addButton);

            rebuildEditButtons();
            updateMaxScroll();
        }

        /**
         * Drops the picker reference once it has hidden itself. Clicking outside the panel makes
         * the list hide and still report the click as consumed, so without this the screen would
         * keep a non-null but invisible overlay — leaving the dim layer up and the header hidden
         * for good.
         */
        private void syncOverlayState() {
            if (effectOverlay != null && !effectOverlay.isVisible()) effectOverlay = null;
        }

        /** Closes the picker and reports the click as consumed. */
        private boolean clearOverlay() {
            effectOverlay = null;
            return true;
        }

        private void openPicker() {
            this.setFocused(null);
            for (EffectRow r : rows) {
                if (r.editButton != null) r.editButton.visible = false;
            }
            effectOverlay = new net.bananemdnsa.historystages.client.editor.widget.list.SearchableEffectList(
                    effectId -> {
                        if (rows.stream().noneMatch(r -> r.effectId.equals(effectId))) {
                            rows.add(new EffectRow(effectId, 30, 0));
                            rebuildEditButtons();
                            updateMaxScroll();
                        }
                        effectOverlay = null;
                    },
                    () -> rows.stream().map(r -> r.effectId).toList());
            effectOverlay.show(this.width / 2, this.height / 2, this.width);
        }

        private void rebuildEditButtons() {
            for (EffectRow r : rows) {
                if (r.editButton != null) this.removeWidget(r.editButton);
            }
            for (EffectRow r : rows) {
                final EffectRow rRef = r;
                r.editButton = StyledButton.of(
                        Component.translatable("editor.historystages.edit"),
                        btn -> this.minecraft.setScreen(new EffectEditDialog(this, rRef)),
                        0, 0, 50, 18);
                this.addRenderableWidget(r.editButton);
            }
        }

        private void updateMaxScroll() {
            int listBottom = this.height - 40;
            maxScroll = Math.max(0, rows.size() * ROW_HEIGHT - (listBottom - LIST_TOP));
            scrollOffset = Math.min(scrollOffset, maxScroll);
        }

        private void saveAndClose() {
            StringBuilder sb = new StringBuilder();
            for (EffectRow r : rows) {
                if (r.effectId == null || r.effectId.isEmpty()) continue;
                if (sb.length() > 0) sb.append(';');
                sb.append(r.effectId).append(',').append(r.seconds).append(',').append(r.amplifier);
            }
            entry.value = sb.toString();
            this.minecraft.setScreen(parent);
        }

        @Override
        public void renderBackground(GuiGraphics guiGraphics) {}

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
            smoothScroll.settle((float) scrollOffset, 0.5f);
            syncOverlayState();
            guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);
            boolean overlayOpen = effectOverlay != null;
            // Vanilla batches text and flushes it late, so a button drawn before the overlay
            // would still show its label on top of the panel. Hiding them outright is the only
            // reliable fix here.
            if (backButton != null) backButton.visible = !overlayOpen;
            if (addButton != null) addButton.visible = !overlayOpen;
            if (!overlayOpen) {
                guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
                guiGraphics.drawCenteredString(this.font, Component.translatable(
                        "editor.historystages.config.effects_count", rows.size()).getString(),
                        this.width / 2, 25, 0x999999);
                guiGraphics.fill(30, LIST_TOP - 4, this.width - 30, LIST_TOP - 3, 0xFF555555);
            }

            int listBottom = this.height - 40;
            int contentLeft = 40;
            int contentRight = this.width - 40;
            int removeX = contentRight - 14;
            int editButtonX = removeX - 8 - 50;
            int summaryRightX = editButtonX - 8;

            guiGraphics.enableScissor(contentLeft - 5, LIST_TOP, contentRight + 5, listBottom);

            int y = LIST_TOP - Math.round(smoothScroll.value());
            for (int i = 0; i < rows.size(); i++) {
                EffectRow r = rows.get(i);
                boolean fullyVisible = !overlayOpen && y >= LIST_TOP && y + ROW_HEIGHT <= listBottom;
                if (r.editButton != null) r.editButton.visible = fullyVisible;
                if (y + ROW_HEIGHT > LIST_TOP - 10 && y < listBottom + 10) {
                    boolean hovered = mouseX >= contentLeft && mouseX <= contentRight
                            && mouseY >= y && mouseY < y + ROW_HEIGHT
                            && mouseY >= LIST_TOP && mouseY <= listBottom;
                    float rh = Ease.outCubic(rowHover.computeIfAbsent(i, k -> new Anim())
                            .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
                    if (rh > 0.001f) {
                        guiGraphics.fill(contentLeft, y, contentRight, y + ROW_HEIGHT, Fade.rgba(0xFFFFFF, 0.125f * rh));
                        guiGraphics.fill(contentLeft, y, contentLeft + 1, y + ROW_HEIGHT, Fade.rgba(0xFFCC00, rh * 0.8f));
                    }

                    String summary = Component.translatable("editor.historystages.config.effect_summary",
                            r.seconds, r.amplifier + 1).getString();
                    int summaryX = summaryRightX - this.font.width(summary);
                    guiGraphics.drawString(this.font, summary, summaryX, y + 9, 0xAACCFF, false);

                    String name = net.bananemdnsa.historystages.client.editor.widget.list
                            .SearchableEffectList.displayName(r.effectId);
                    int idX = contentLeft + 4;
                    int maxIdWidth = summaryX - idX - 8;
                    String label = name.equals(r.effectId) ? name : name + " §8" + r.effectId;
                    if (maxIdWidth > 0 && this.font.width(label) > maxIdWidth) {
                        label = this.font.plainSubstrByWidth(label, maxIdWidth - 6) + "...";
                    }
                    guiGraphics.drawString(this.font, label, idX, y + 9,
                            hovered ? 0xFFFFFF : 0xCCCCCC, false);

                    r.editButton.setX(editButtonX);
                    r.editButton.setY(y + 4);

                    boolean removeHovered = mouseX >= removeX && mouseX <= removeX + 12
                            && mouseY >= y + 2 && mouseY < y + ROW_HEIGHT - 2
                            && mouseY >= LIST_TOP && mouseY <= listBottom;
                    guiGraphics.drawString(this.font, "×", removeX + 2, y + 8,
                            removeHovered ? 0xFF5555 : 0x888888, false);
                }
                y += ROW_HEIGHT;
            }
            guiGraphics.disableScissor();

            if (maxScroll > 0) {
                int scrollAreaHeight = listBottom - LIST_TOP;
                int barHeight = Math.max(20, (int) ((float) scrollAreaHeight / (maxScroll + scrollAreaHeight) * scrollAreaHeight));
                int barY = LIST_TOP + Math.round(smoothScroll.value() / maxScroll * (scrollAreaHeight - barHeight));
                guiGraphics.fill(contentRight + 2, LIST_TOP, contentRight + 8, listBottom, 0x40000000);
                guiGraphics.fill(contentRight + 2, barY, contentRight + 8, barY + barHeight, 0xC0FFFFFF);
            }
            guiGraphics.fill(30, listBottom + 1, this.width - 30, listBottom + 2, 0xFF555555);

            super.render(guiGraphics, mouseX, mouseY, partialTick);

            if (effectOverlay != null) {
                guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);
                effectOverlay.render(guiGraphics, this.font, mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            syncOverlayState();
            if (effectOverlay != null) {
                boolean consumed = effectOverlay.mouseClicked(mouseX, mouseY);
                syncOverlayState();
                return consumed || clearOverlay();
            }

            int listBottom = this.height - 40;
            int contentLeft = 40;
            int contentRight = this.width - 40;
            int removeX = contentRight - 14;

            if (maxScroll > 0 && mouseX >= contentRight + 1 && mouseX <= contentRight + 9
                    && mouseY >= LIST_TOP && mouseY <= listBottom) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, LIST_TOP, listBottom);
                return true;
            }

            if (super.mouseClicked(mouseX, mouseY, button)) return true;

            if (mouseX < contentLeft || mouseX > contentRight || mouseY < LIST_TOP || mouseY > listBottom)
                return false;

            int y = LIST_TOP - Math.round(smoothScroll.value());
            for (int i = 0; i < rows.size(); i++) {
                if (mouseY >= y && mouseY < y + ROW_HEIGHT
                        && mouseX >= removeX && mouseX <= removeX + 12) {
                    EffectRow removed = rows.remove(i);
                    if (removed.editButton != null) this.removeWidget(removed.editButton);
                    updateMaxScroll();
                    return true;
                }
                y += ROW_HEIGHT;
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
            if (draggingScrollbar) { draggingScrollbar = false; return true; }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        private void updateScrollFromMouse(double mouseY, int listTop, int listBottom) {
            int scrollAreaHeight = listBottom - listTop;
            float ratio = (float) Math.max(0, Math.min(1, (mouseY - listTop) / (double) scrollAreaHeight));
            scrollOffset = Math.max(0, Math.min(maxScroll, Math.round(ratio * maxScroll)));
            // Snapped, not eased: while the thumb is held the list must track the
            // cursor exactly, or the thumb drifts from where the pointer is.
            smoothScroll.set((float) scrollOffset);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            syncOverlayState();
            if (effectOverlay != null) return effectOverlay.mouseScrolled(mouseX, mouseY, delta);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 16));
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            syncOverlayState();
            if (effectOverlay != null) {
                if (keyCode == 256) return clearOverlay();
                boolean consumed = effectOverlay.keyPressed(keyCode);
                syncOverlayState();
                return consumed;
            }
            if (keyCode == 256) { saveAndClose(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char c, int modifiers) {
            syncOverlayState();
            if (effectOverlay != null) return effectOverlay.charTyped(c);
            return super.charTyped(c, modifiers);
        }

        @Override
        public void onClose() { saveAndClose(); }

        @Override
        public boolean isPauseScreen() { return true; }

        private static class EffectRow {
            final String effectId;
            int seconds;
            int amplifier;
            net.minecraft.client.gui.components.Button editButton;
            EffectRow(String effectId, int seconds, int amplifier) {
                this.effectId = effectId;
                this.seconds = seconds;
                this.amplifier = amplifier;
            }
        }
    }

    /** Duration + amplifier dialog for a single {@code EffectListEditorScreen} row. */
    static class EffectEditDialog extends AbstractInputScreen {
        private final EffectListEditorScreen.EffectRow row;

        EffectEditDialog(EffectListEditorScreen parent, EffectListEditorScreen.EffectRow row) {
            super(parent, Component.translatable("editor.historystages.effect.edit_title",
                    net.bananemdnsa.historystages.client.editor.widget.list
                            .SearchableEffectList.displayName(row.effectId)));
            this.row = row;
        }

        @Override
        protected List<InputField> fields() {
            return List.of(
                    InputField.number("seconds")
                            .label(Component.translatable("editor.historystages.effect.seconds"))
                            .hint(Component.translatable("editor.historystages.effect.seconds_hint"))
                            .range(1, 3600)
                            .initial(String.valueOf(row.seconds)),
                    InputField.number("amplifier")
                            .label(Component.translatable("editor.historystages.effect.amplifier"))
                            .hint(Component.translatable("editor.historystages.effect.amplifier_hint"))
                            .range(0, 255)
                            .initial(String.valueOf(row.amplifier)));
        }

        @Override
        protected void onConfirm(InputValues values) {
            row.seconds = values.getInt("seconds");
            row.amplifier = values.getInt("amplifier");
            this.minecraft.setScreen(parent);
        }
    }

    /**
     * Editor for the researchBoosters config list. Each row shows a block icon,
     * its ID and two EditBoxes for speed / cost percent (0-90). The "Add" button
     * opens a SearchableItemList to pick a block.
     *
     * Internal entry serialization: "block_id,speed,cost" joined by ';'.
     */
    static class BoosterListEditorScreen extends Screen {
        private final ConfigEditorScreen parent;
        private final ConfigEntry entry;
        private final List<BoosterRow> rows = new ArrayList<>();
        private SearchableItemList itemOverlay;
        private net.minecraft.client.gui.components.Button backButton;
        private net.minecraft.client.gui.components.Button addButton;
        private double scrollOffset = 0;
        /** Sub-pixel scroll chasing {@link #scrollOffset}; render and the click paths both read it. */
        private final Anim smoothScroll = new Anim();
        /** Per-row hover progress, keyed by row index. */
        private final Map<Integer, Anim> rowHover = new HashMap<>();
        private int maxScroll = 0;
        private boolean draggingScrollbar = false;
        private static final int ROW_HEIGHT = 26;
        private static final int LIST_TOP = 50;
        private static final int FIELD_W = 30;
        private static final long MARQUEE_DELAY_MS = Timing.MARQUEE_DELAY_MS;
        private static final float MARQUEE_SPEED = Timing.MARQUEE_SPEED;
        private int hoveredRow = -1;
        private long hoverStartTime = 0L;

        BoosterListEditorScreen(ConfigEditorScreen parent, ConfigEntry entry) {
            super(Component.translatable("editor.historystages.config." + entry.key));
            this.parent = parent;
            this.entry = entry;
            decode(entry.value);
        }

        private void decode(String value) {
            rows.clear();
            if (value == null || value.isEmpty()) return;
            for (String part : value.split(";")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                String[] tokens = trimmed.split(",");
                if (tokens.length != 3 && tokens.length != 5) continue;
                String blockId = tokens[0].trim();
                int speed = parseClampedPercent(tokens[1].trim());
                int cost = parseClampedPercent(tokens[2].trim());
                int tier = 1;
                net.bananemdnsa.historystages.research.TierMode mode =
                        net.bananemdnsa.historystages.research.TierMode.MIN;
                if (tokens.length == 5) {
                    try {
                        tier = Math.max(1, Math.min(4, Integer.parseInt(tokens[3].trim())));
                    } catch (NumberFormatException ignored) {}
                    mode = net.bananemdnsa.historystages.research.TierMode.parse(
                            tokens[4].trim(), net.bananemdnsa.historystages.research.TierMode.MIN);
                }
                rows.add(new BoosterRow(blockId, speed, cost, tier, mode));
            }
        }

        private static int parseClampedPercent(String s) {
            try {
                int v = Integer.parseInt(s);
                return Math.max(0, Math.min(90, v));
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }

        @Override
        protected void init() {
            backButton = StyledButton.of(
                    Component.translatable("editor.historystages.back"),
                    btn -> saveAndClose(), 10, this.height - 30, 60, 20);
            this.addRenderableWidget(backButton);

            addButton = StyledButton.of(
                    Component.translatable("editor.historystages.add"),
                    btn -> openPicker(),
                    this.width / 2 - 50, this.height - 30, 100, 20);
            this.addRenderableWidget(addButton);

            rebuildEditBoxes();
            updateMaxScroll();
        }

        /**
         * Drops the picker reference once it has hidden itself. Clicking outside the panel
         * makes the list hide and still report the click as consumed, so without this the
         * screen would keep a non-null but invisible overlay — leaving the dim layer up and
         * the header hidden for good.
         */
        private void syncOverlayState() {
            if (itemOverlay != null && !itemOverlay.isVisible()) itemOverlay = null;
        }

        /** Closes the picker and reports the click as consumed. */
        private boolean clearOverlay() {
            itemOverlay = null;
            return true;
        }

        /**
         * Open the block picker, after clearing any focus and hiding the EditBoxes
         * so they don't visually leak through (or accept input behind) the modal overlay.
         */
        private void openPicker() {
            this.setFocused(null);
            for (BoosterRow r : rows) {
                if (r.editButton != null) r.editButton.visible = false;
            }
            itemOverlay = new SearchableItemList(blockId -> {
                if (rows.stream().noneMatch(r -> r.blockId.equals(blockId))) {
                    BoosterRow nr = new BoosterRow(blockId, 5, 0, 1,
                            net.bananemdnsa.historystages.research.TierMode.MIN);
                    rows.add(nr);
                    rebuildEditBoxes();
                    updateMaxScroll();
                }
                itemOverlay = null;
            });
            itemOverlay.show(this.width / 2, this.height / 2, this.width);
        }

        private void rebuildEditBoxes() {
            for (BoosterRow r : rows) {
                if (r.editButton != null) this.removeWidget(r.editButton);
            }
            for (BoosterRow r : rows) {
                final BoosterRow rRef = r;
                r.editButton = StyledButton.of(
                        Component.translatable("editor.historystages.edit"),
                        btn -> this.minecraft.setScreen(new BoosterEditScreen(this, rRef)),
                        0, 0, 50, 18);
                this.addRenderableWidget(r.editButton);
            }
        }

        /**
         * Truncate-with-"..." until the row is hovered for {@link #MARQUEE_DELAY_MS}, then
         * scroll the full ID horizontally.
         */
        private void drawIdMaybeMarquee(GuiGraphics g, String text, int x, int y,
                                        int w, int h, boolean hovered, int rowIndex) {
            int textW = this.font.width(text);
            int textColor = hovered ? 0xFFFFFF : 0xCCCCCC;
            if (hovered) {
                if (hoveredRow != rowIndex) {
                    hoveredRow = rowIndex;
                    hoverStartTime = System.currentTimeMillis();
                }
            }
            if (textW <= w) {
                g.drawString(this.font, text, x, y, textColor, false);
                return;
            }
            if (hovered && hoveredRow == rowIndex) {
                long elapsed = System.currentTimeMillis() - hoverStartTime;
                if (elapsed > MARQUEE_DELAY_MS) {
                    float scrollProg = (elapsed - MARQUEE_DELAY_MS) / 1000.0f * MARQUEE_SPEED;
                    int maxMarquee = textW - w + 10;
                    float cycle = maxMarquee * 2f;
                    float pos = scrollProg % cycle;
                    int scrollOff = pos <= maxMarquee ? (int) pos : (int) (cycle - pos);
                    g.enableScissor(x, y - 4, x + w, y + h);
                    g.drawString(this.font, text, x - scrollOff, y, textColor, false);
                    g.disableScissor();
                    return;
                }
            }
            g.drawString(this.font, this.font.plainSubstrByWidth(text, w - 6) + "...",
                    x, y, textColor, false);
        }

        /** "Speed +5% · Cost +0% · Tier II+" */
        private static String formatSummary(BoosterRow r) {
            String roman = net.bananemdnsa.historystages.research.TierMatcher.roman(r.tier);
            String tierStr = r.mode == net.bananemdnsa.historystages.research.TierMode.EXACT
                    ? roman
                    : roman + "+";
            return "Speed +" + r.speed + "%  ·  Cost +" + r.cost + "%  ·  Tier " + tierStr;
        }

        private void updateMaxScroll() {
            int listBottom = this.height - 40;
            int contentHeight = rows.size() * ROW_HEIGHT;
            int visibleHeight = listBottom - LIST_TOP;
            maxScroll = Math.max(0, contentHeight - visibleHeight);
            scrollOffset = Math.min(scrollOffset, maxScroll);
        }

        private void saveAndClose() {
            StringBuilder sb = new StringBuilder();
            for (BoosterRow r : rows) {
                if (r.blockId == null || r.blockId.isEmpty()) continue;
                if (sb.length() > 0) sb.append(';');
                sb.append(r.blockId).append(',').append(r.speed).append(',').append(r.cost)
                        .append(',').append(r.tier).append(',').append(r.mode.serialize());
            }
            entry.value = sb.toString();
            this.minecraft.setScreen(parent);
        }

        @Override
        public void renderBackground(GuiGraphics guiGraphics) {}

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            syncOverlayState();
            smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
            smoothScroll.settle((float) scrollOffset, 0.5f);
            guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);
            boolean overlayOpen = itemOverlay != null;
            // Skip the title/subtitle/separators while the picker is up so they don't bleed
            // through the dim layer above the picker panel. The footer buttons need the same
            // treatment: vanilla batches text and flushes it late, so their labels would draw
            // on top of the panel even though the panel is painted after them.
            if (backButton != null) backButton.visible = !overlayOpen;
            if (addButton != null) addButton.visible = !overlayOpen;
            if (!overlayOpen) {
                guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
                guiGraphics.drawCenteredString(this.font, rows.size() + " boosters", this.width / 2, 25, 0x999999);
                guiGraphics.fill(30, LIST_TOP - 4, this.width - 30, LIST_TOP - 3, 0xFF555555);
            }

            int listBottom = this.height - 40;
            int contentLeft = 40;
            int contentRight = this.width - 40;

            int removeX = contentRight - 14;
            int editButtonX = removeX - 8 - 50; // edit button width 50
            int summaryRightX = editButtonX - 8;

            // Buttons render via super.render() outside the scissor — toggle visible based on row clipping.
            guiGraphics.enableScissor(contentLeft - 5, LIST_TOP, contentRight + 5, listBottom);

            int y = LIST_TOP - Math.round(smoothScroll.value());
            boolean anyHover = false;
            for (int i = 0; i < rows.size(); i++) {
                BoosterRow r = rows.get(i);
                boolean fullyVisible = !overlayOpen && y >= LIST_TOP && y + ROW_HEIGHT <= listBottom;
                if (r.editButton != null) r.editButton.visible = fullyVisible;
                if (y + ROW_HEIGHT > LIST_TOP - 10 && y < listBottom + 10) {
                    boolean hovered = mouseX >= contentLeft && mouseX <= contentRight
                            && mouseY >= y && mouseY < y + ROW_HEIGHT
                            && mouseY >= LIST_TOP && mouseY <= listBottom;
                    if (hovered) {
                        anyHover = true;
                    }
                    // Advanced unconditionally — running it only while hovered would leave the
                    // highlight stuck on the last row the cursor touched.
                    float rh = Ease.outCubic(rowHover.computeIfAbsent(i, k -> new Anim())
                            .ramp(hovered, Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));
                    if (rh > 0.001f) {
                        guiGraphics.fill(contentLeft, y, contentRight, y + ROW_HEIGHT, Fade.rgba(0xFFFFFF, 0.125f * rh));
                        guiGraphics.fill(contentLeft, y, contentLeft + 1, y + ROW_HEIGHT, Fade.rgba(0xFFCC00, rh * 0.8f));
                    }

                    // Block icon
                    ResourceLocation rl = ResourceLocation.tryParse(r.blockId);
                    if (rl != null) {
                        Item item = ForgeRegistries.ITEMS.getValue(rl);
                        if (item != null) {
                            guiGraphics.renderItem(new ItemStack(item), contentLeft + 2, y + 5);
                        }
                    }

                    // Right-aligned summary, then ID gets the remaining space.
                    String summary = formatSummary(r);
                    int summaryW = this.font.width(summary);
                    int summaryX = summaryRightX - summaryW;
                    guiGraphics.drawString(this.font, summary, summaryX, y + 9, 0xAACCFF, false);

                    int idX = contentLeft + 22;
                    int maxIdWidth = summaryX - idX - 8;
                    drawIdMaybeMarquee(guiGraphics, r.blockId, idX, y + 9,
                            maxIdWidth, ROW_HEIGHT - 2, hovered, i);

                    // Position Edit button
                    r.editButton.setX(editButtonX); r.editButton.setY(y + 4);

                    // Remove ×
                    boolean removeHovered = mouseX >= removeX && mouseX <= removeX + 12
                            && mouseY >= y + 2 && mouseY < y + ROW_HEIGHT - 2
                            && mouseY >= LIST_TOP && mouseY <= listBottom;
                    guiGraphics.drawString(this.font, "×", removeX + 2, y + 8,
                            removeHovered ? 0xFF5555 : 0x888888, false);
                }
                y += ROW_HEIGHT;
            }
            if (!anyHover) hoveredRow = -1;
            guiGraphics.disableScissor();

            if (maxScroll > 0) {
                int scrollAreaHeight = listBottom - LIST_TOP;
                int barHeight = Math.max(20, (int) ((float) scrollAreaHeight / (maxScroll + scrollAreaHeight) * scrollAreaHeight));
                int barY = LIST_TOP + Math.round(smoothScroll.value() / maxScroll * (scrollAreaHeight - barHeight));
                guiGraphics.fill(contentRight + 2, LIST_TOP, contentRight + 8, listBottom, 0x40000000);
                guiGraphics.fill(contentRight + 2, barY, contentRight + 8, barY + barHeight, 0xC0FFFFFF);
            }
            guiGraphics.fill(30, listBottom + 1, this.width - 30, listBottom + 2, 0xFF555555);

            super.render(guiGraphics, mouseX, mouseY, partialTick);

            if (itemOverlay != null) {
                guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);
                itemOverlay.render(guiGraphics, this.font, mouseX, mouseY);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            syncOverlayState();
            if (itemOverlay != null) {
                boolean consumed = itemOverlay.mouseClicked(mouseX, mouseY);
                syncOverlayState();
                return consumed || clearOverlay();
            }

            int listBottom = this.height - 40;
            int contentLeft = 40;
            int contentRight = this.width - 40;
            int removeX = contentRight - 14;

            if (maxScroll > 0 && mouseX >= contentRight + 1 && mouseX <= contentRight + 9
                    && mouseY >= LIST_TOP && mouseY <= listBottom) {
                draggingScrollbar = true;
                updateScrollFromMouse(mouseY, LIST_TOP, listBottom);
                return true;
            }

            if (super.mouseClicked(mouseX, mouseY, button)) return true;

            if (mouseX < contentLeft || mouseX > contentRight || mouseY < LIST_TOP || mouseY > listBottom)
                return false;

            int y = LIST_TOP - Math.round(smoothScroll.value());
            for (int i = 0; i < rows.size(); i++) {
                if (mouseY >= y && mouseY < y + ROW_HEIGHT) {
                    if (mouseX >= removeX && mouseX <= removeX + 12) {
                        BoosterRow removed = rows.remove(i);
                        if (removed.editButton != null) this.removeWidget(removed.editButton);
                        updateMaxScroll();
                        return true;
                    }
                }
                y += ROW_HEIGHT;
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
            if (draggingScrollbar) { draggingScrollbar = false; return true; }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        private void updateScrollFromMouse(double mouseY, int listTop, int listBottom) {
            int scrollAreaHeight = listBottom - listTop;
            float ratio = (float) Math.max(0, Math.min(1, (mouseY - listTop) / (double) scrollAreaHeight));
            scrollOffset = Math.round(ratio * maxScroll);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            // Snapped, not eased: while the thumb is held the list must track the
            // cursor exactly, or the thumb drifts from where the pointer is.
            smoothScroll.set((float) scrollOffset);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            syncOverlayState();
            if (itemOverlay != null) return itemOverlay.mouseScrolled(mouseX, mouseY, delta);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 16));
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            syncOverlayState();
            if (itemOverlay != null) {
                if (keyCode == 256) return clearOverlay();
                boolean consumed = itemOverlay.keyPressed(keyCode);
                syncOverlayState();
                return consumed;
            }
            if (keyCode == 256) { saveAndClose(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char c, int modifiers) {
            syncOverlayState();
            if (itemOverlay != null) return itemOverlay.charTyped(c);
            return super.charTyped(c, modifiers);
        }

        @Override
        public void onClose() { saveAndClose(); }

        @Override
        public boolean isPauseScreen() { return true; }

        static class BoosterRow {
            String blockId;
            int speed;
            int cost;
            int tier;
            net.bananemdnsa.historystages.research.TierMode mode;
            net.minecraft.client.gui.components.Button editButton;
            BoosterRow(String blockId, int speed, int cost, int tier,
                       net.bananemdnsa.historystages.research.TierMode mode) {
                this.blockId = blockId;
                this.speed = speed;
                this.cost = cost;
                this.tier = tier;
                this.mode = mode;
            }
        }
    }

    /**
     * Detail screen for a single BoosterRow: speed/cost EditBoxes, tier dropdown,
     * mode toggle. Mutates the row in place on Save and returns to the list.
     */
    static class BoosterEditScreen extends Screen {
        private final BoosterListEditorScreen parent;
        private final BoosterListEditorScreen.BoosterRow row;

        private EditBox speedField;
        private EditBox costField;
        private net.bananemdnsa.historystages.client.editor.widget.dropdown.PedestalTierDropdown tierDropdown;
        private net.minecraft.client.gui.components.Button modeButton;

        // Working copies — only written back on Save.
        private int editSpeed;
        private int editCost;
        private int editTier;
        private net.bananemdnsa.historystages.research.TierMode editMode;

        BoosterEditScreen(BoosterListEditorScreen parent, BoosterListEditorScreen.BoosterRow row) {
            super(Component.translatable("editor.historystages.booster.edit_title"));
            this.parent = parent;
            this.row = row;
            this.editSpeed = row.speed;
            this.editCost = row.cost;
            this.editTier = row.tier;
            this.editMode = row.mode;
        }

        @Override
        protected void init() {
            int labelX = 30;
            String labelSpeed = "Speed %";
            String labelCost  = "Cost %";
            String labelTier  = Component.translatable("editor.historystages.field.min_pedestal_tier").getString();
            String labelMode  = Component.translatable("editor.historystages.field.pedestal_tier_mode").getString();
            int maxLabelW = Math.max(Math.max(this.font.width(labelSpeed), this.font.width(labelCost)),
                    Math.max(this.font.width(labelTier), this.font.width(labelMode)));
            int fieldX = labelX + maxLabelW + 10;

            speedField = new EditBox(this.font, fieldX, 44, 60, 18, Component.literal("speed"));
            speedField.setMaxLength(2);
            speedField.setValue(String.valueOf(editSpeed));
            speedField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
            speedField.setResponder(v -> {
                try { editSpeed = Math.max(0, Math.min(90, Integer.parseInt(v))); }
                catch (NumberFormatException ignored) { editSpeed = 0; }
            });
            this.addRenderableWidget(speedField);

            costField = new EditBox(this.font, fieldX, 66, 60, 18, Component.literal("cost"));
            costField.setMaxLength(2);
            costField.setValue(String.valueOf(editCost));
            costField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
            costField.setResponder(v -> {
                try { editCost = Math.max(0, Math.min(90, Integer.parseInt(v))); }
                catch (NumberFormatException ignored) { editCost = 0; }
            });
            this.addRenderableWidget(costField);

            tierDropdown = new net.bananemdnsa.historystages.client.editor.widget.dropdown.PedestalTierDropdown(
                    editTier, 160, picked -> editTier = picked);
            tierDropdown.setPosition(fieldX, 88);

            modeButton = StyledButton.of(
                    Component.translatable(modeKey(editMode)),
                    btn -> {
                        editMode = (editMode == net.bananemdnsa.historystages.research.TierMode.MIN)
                                ? net.bananemdnsa.historystages.research.TierMode.EXACT
                                : net.bananemdnsa.historystages.research.TierMode.MIN;
                        btn.setMessage(Component.translatable(modeKey(editMode)));
                    },
                    fieldX, 110, 160, 18);
            this.addRenderableWidget(modeButton);

            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.back"),
                    btn -> this.minecraft.setScreen(parent),
                    10, this.height - 25, 50, 18));
            this.addRenderableWidget(StyledButton.of(
                    Component.translatable("editor.historystages.save"),
                    btn -> save(), this.width - 60, this.height - 25, 50, 18));
        }

        private void save() {
            row.speed = editSpeed;
            row.cost = editCost;
            row.tier = editTier;
            row.mode = editMode;
            this.minecraft.setScreen(parent);
        }

        private static String modeKey(net.bananemdnsa.historystages.research.TierMode m) {
            return m == net.bananemdnsa.historystages.research.TierMode.EXACT
                    ? "editor.historystages.tier_mode.exact"
                    : "editor.historystages.tier_mode.min";
        }

        @Override
        public void renderBackground(GuiGraphics g) {}

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
            g.fill(0, 0, this.width, this.height, 0xC0000000);
            super.render(g, mouseX, mouseY, pt);

            g.drawCenteredString(this.font,
                    Component.translatable("editor.historystages.booster.edit_title"),
                    this.width / 2, 8, 0xFFFFFF);

            // Block icon + ID header
            ResourceLocation rl = ResourceLocation.tryParse(row.blockId);
            if (rl != null) {
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item != null) g.renderItem(new ItemStack(item), 30, 22);
            }
            g.drawString(this.font, row.blockId, 52, 26, 0xCCCCCC, false);

            int labelX = 30;
            g.drawString(this.font, "Speed %", labelX, 49, 0xAAAAAA, false);
            g.drawString(this.font, "Cost %",  labelX, 71, 0xAAAAAA, false);
            g.drawString(this.font,
                    Component.translatable("editor.historystages.field.min_pedestal_tier").getString(),
                    labelX, 93, 0xAAAAAA, false);
            g.drawString(this.font,
                    Component.translatable("editor.historystages.field.pedestal_tier_mode").getString(),
                    labelX, 115, 0xAAAAAA, false);

            tierDropdown.renderButton(g, this.font, mouseX, mouseY);
            tierDropdown.renderPopup(g, this.font, mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (tierDropdown != null && tierDropdown.mouseClicked(mouseX, mouseY)) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean isPauseScreen() { return true; }
    }
}
