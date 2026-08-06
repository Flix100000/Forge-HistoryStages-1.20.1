package net.bananemdnsa.historystages.client.editor;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.graph.GraphConfigCodec;
import net.bananemdnsa.historystages.data.graph.GraphConfigEntries;
import net.bananemdnsa.historystages.data.graph.GraphKey;
import net.bananemdnsa.historystages.network.serverbound.SaveGraphConfigPacket;
import net.bananemdnsa.historystages.client.editor.widget.dialog.AbstractInputScreen;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputField;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputValues;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;
import net.bananemdnsa.historystages.network.CommonConfigSync;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.serverbound.SaveConfigPacket;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import net.bananemdnsa.historystages.client.editor.dialog.ColorInputScreen;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.EnumDropdown;
import net.bananemdnsa.historystages.client.editor.widget.list.PickerOverlay;
import net.bananemdnsa.historystages.client.editor.widget.list.ConfigRowList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableItemList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableTagList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableTextureList;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigEditorScreen extends Screen {

    private static final Logger LOGGER = LogUtils.getLogger();

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
    /** Draws and hit-tests the config rows; owns their hover state. */
    private final ConfigRowList configRows = new ConfigRowList();
    private final Anim scrollThumbHover = new Anim();
    private int maxScroll = 0;
    private boolean draggingScrollbar = false;

    // Unsaved changes tracking - computed by comparing current values to initial values

    // Config entries grouped by section
    private List<ConfigSection> clientSections;
    private List<ConfigSection> commonSections;
    /** graph.toml's five non-style tables, generated from the spec. */
    private List<ConfigSection> graphSections;
    /**
     * The six node-style blocks, keyed {@code "global.unlocked"} and so on. Edited by
     * {@link GraphStyleScreen} but owned here, so one Save covers them and the unsaved-changes
     * marker stays honest across both screens.
     */
    private final Map<String, List<ConfigEntry>> styleEntries = new LinkedHashMap<>();

    /** Hover tooltip, including its own appear-delay bookkeeping. */
    private final EditorTooltip tooltip = new EditorTooltip();

    // Tab layout
    private static final String[] TAB_KEYS = {
            "editor.historystages.tab.client",
            "editor.historystages.tab.common",
            "editor.historystages.tab.graph"
    };
    private int[] tabX;
    private int[] tabW;
    private int tabY;

    // Layout constants
    private static final int HEADER_HEIGHT = 50;
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
        if (clientSections == null) buildConfigEntries();
        if (graphSections == null) buildGraphEntries();

        active = new java.lang.ref.WeakReference<>(this);

        // Compute tab positions
        tabY = 30;
        tabX = new int[TAB_KEYS.length];
        tabW = new int[TAB_KEYS.length];
        // Three tabs now: 200 split three ways leaves 66px each, too narrow for the labels.
        int tabTotalWidth = 300;
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
                            }
                    ));
                }, this.width - 70, this.height - 30, 60, 20));

        updateMaxScroll();
    }

    private void buildConfigEntries() {
        // --- CLIENT CONFIG ---
        clientSections = new ArrayList<>();

        ConfigSection visuals = new ConfigSection("editor.historystages.config.visuals");
        visuals.add(new ConfigEntry("showTooltips", ConfigType.BOOLEAN,
                Config.CLIENT.showTooltips.get().toString(), true, "true"));
        visuals.add(new ConfigEntry("showStageName", ConfigType.BOOLEAN,
                Config.CLIENT.showStageName.get().toString(), true, "true"));
        visuals.add(new ConfigEntry("showAllUntilComplete", ConfigType.BOOLEAN,
                Config.CLIENT.showAllUntilComplete.get().toString(), true, "true"));
        visuals.add(new ConfigEntry("showLockIcons", ConfigType.BOOLEAN,
                Config.CLIENT.showLockIcons.get().toString(), true, "true"));
        visuals.add(new ConfigEntry("showBoosterTooltips", ConfigType.BOOLEAN,
                Config.CLIENT.showBoosterTooltips.get().toString(), true, "true"));
        visuals.add(new ConfigEntry("showScrollTierTooltip", ConfigType.BOOLEAN,
                Config.CLIENT.showScrollTierTooltip.get().toString(), true, "true"));
        clientSections.add(visuals);

        ConfigSection jade = new ConfigSection("editor.historystages.config.jade");
        jade.add(new ConfigEntry("jadeShowInfo", ConfigType.BOOLEAN,
                Config.CLIENT.jadeShowInfo.get().toString(), true, "true"));
        jade.add(new ConfigEntry("jadeStageName", ConfigType.BOOLEAN,
                Config.CLIENT.jadeStageName.get().toString(), true, "true"));
        jade.add(new ConfigEntry("jadeShowAllUntilComplete", ConfigType.BOOLEAN,
                Config.CLIENT.jadeShowAllUntilComplete.get().toString(), true, "true"));
        clientSections.add(jade);

        ConfigSection individualClient = new ConfigSection("editor.historystages.config.individual_stages");
        individualClient.add(new ConfigEntry("showSilverLockIcons", ConfigType.BOOLEAN,
                Config.CLIENT.showSilverLockIcons.get().toString(), true, "true"));
        individualClient.add(new ConfigEntry("showIndividualTooltips", ConfigType.BOOLEAN,
                Config.CLIENT.showIndividualTooltips.get().toString(), true, "true"));
        clientSections.add(individualClient);

        ConfigSection dimLock = new ConfigSection("editor.historystages.config.dimension_lock");
        dimLock.add(new ConfigEntry("dimUseActionbar", ConfigType.BOOLEAN,
                Config.CLIENT.dimUseActionbar.get().toString(), true, "true"));
        dimLock.add(new ConfigEntry("dimShowChat", ConfigType.BOOLEAN,
                Config.CLIENT.dimShowChat.get().toString(), true, "false"));
        dimLock.add(new ConfigEntry("dimShowStagesInChat", ConfigType.BOOLEAN,
                Config.CLIENT.dimShowStagesInChat.get().toString(), true, "true"));
        clientSections.add(dimLock);

        ConfigSection mobLock = new ConfigSection("editor.historystages.config.mob_lock");
        mobLock.add(new ConfigEntry("mobUseActionbar", ConfigType.BOOLEAN,
                Config.CLIENT.mobUseActionbar.get().toString(), true, "true"));
        mobLock.add(new ConfigEntry("mobShowChat", ConfigType.BOOLEAN,
                Config.CLIENT.mobShowChat.get().toString(), true, "false"));
        mobLock.add(new ConfigEntry("mobShowStagesInChat", ConfigType.BOOLEAN,
                Config.CLIENT.mobShowStagesInChat.get().toString(), true, "true"));
        clientSections.add(mobLock);

        ConfigSection structureVisuals = new ConfigSection("editor.historystages.config.structure_visuals");
        structureVisuals.add(new ConfigEntry("structureBorderEnabled", ConfigType.BOOLEAN,
                Config.CLIENT.structureBorderEnabled.get().toString(), true, "true"));
        structureVisuals.add(new ConfigEntry("structureBorderDistance", ConfigType.DOUBLE,
                Config.CLIENT.structureBorderDistance.get().toString(), true, "8.0", 1.0, 32.0));
        structureVisuals.add(new ConfigEntry("structureLockOverlayEnabled", ConfigType.BOOLEAN,
                Config.CLIENT.structureLockOverlayEnabled.get().toString(), true, "true"));
        structureVisuals.add(new ConfigEntry("structureLockOverlayOpacity", ConfigType.DOUBLE,
                Config.CLIENT.structureLockOverlayOpacity.get().toString(), true, "0.3", 0.0, 1.0));
        clientSections.add(structureVisuals);

        ConfigSection dependenciesClient = new ConfigSection("editor.historystages.config.dependencies");
        dependenciesClient.add(new ConfigEntry("showDependenciesOnScroll", ConfigType.BOOLEAN,
                Config.CLIENT.showDependenciesOnScroll.get().toString(), true, "true"));
        dependenciesClient.add(new ConfigEntry("hideFulfilledDependencies", ConfigType.BOOLEAN,
                Config.CLIENT.hideFulfilledDependencies.get().toString(), true, "false"));
        clientSections.add(dependenciesClient);

        // JEI hiding (Issue #64)
        ConfigSection jeiHiding = new ConfigSection("editor.historystages.config.jei_hiding");
        jeiHiding.add(new ConfigEntry("hideLockedItemsInJei", ConfigType.BOOLEAN,
                Config.CLIENT.hideLockedItemsInJei.get().toString(), true, "false"));
        jeiHiding.add(new ConfigEntry("hideLockedRecipesInJei", ConfigType.BOOLEAN,
                Config.CLIENT.hideLockedRecipesInJei.get().toString(), true, "false"));
        jeiHiding.add(new ConfigEntry("lockedItemMultiStagePolicy", ConfigType.MULTI_STAGE_POLICY,
                Config.CLIENT.lockedItemMultiStagePolicy.get().name(), true, "STRICT"));
        clientSections.add(jeiHiding);

        // --- COMMON CONFIG ---
        commonSections = new ArrayList<>();

        ConfigSection messages = new ConfigSection("editor.historystages.config.messages");
        messages.add(new ConfigEntry("showWelcomeMessage", ConfigType.BOOLEAN,
                Config.COMMON.showWelcomeMessage.get().toString(), false, "true"));
        messages.add(new ConfigEntry("showDebugErrors", ConfigType.BOOLEAN,
                Config.COMMON.showDebugErrors.get().toString(), false, "true"));
        messages.add(new ConfigEntry("enableRuntimeLogging", ConfigType.BOOLEAN,
                Config.COMMON.enableRuntimeLogging.get().toString(), false, "false"));
        commonSections.add(messages);

        ConfigSection gameplay = new ConfigSection("editor.historystages.config.gameplay");
        gameplay.add(new ConfigEntry("lockMobLoot", ConfigType.BOOLEAN,
                Config.COMMON.lockMobLoot.get().toString(), false, "true"));
        gameplay.add(new ConfigEntry("lockBlockBreaking", ConfigType.BOOLEAN,
                Config.COMMON.lockBlockBreaking.get().toString(), false, "true"));
        gameplay.add(new ConfigEntry("lockedBlockBreakSpeedMultiplier", ConfigType.DOUBLE,
                Config.COMMON.lockedBlockBreakSpeedMultiplier.get().toString(), false, "0.05",
                0.001, 1.0));
        gameplay.add(new ConfigEntry("lockItemUsage", ConfigType.BOOLEAN,
                Config.COMMON.lockItemUsage.get().toString(), false, "true"));
        gameplay.add(new ConfigEntry("lockEntityItems", ConfigType.BOOLEAN,
                Config.COMMON.lockEntityItems.get().toString(), false, "true"));
        gameplay.add(new ConfigEntry("lockBlockInteraction", ConfigType.BOOLEAN,
                Config.COMMON.lockBlockInteraction.get().toString(), false, "true"));
        commonSections.add(gameplay);

        ConfigSection notifications = new ConfigSection("editor.historystages.config.notifications");
        notifications.add(new ConfigEntry("broadcastChat", ConfigType.BOOLEAN,
                Config.COMMON.broadcastChat.get().toString(), false, "true"));
        notifications.add(new ConfigEntry("unlockMessageFormat", ConfigType.STRING,
                Config.COMMON.unlockMessageFormat.get(), false,
                "&fThe world has entered the &b{stage}&f!"));
        notifications.add(new ConfigEntry("useActionbar", ConfigType.BOOLEAN,
                Config.COMMON.useActionbar.get().toString(), false, "false"));
        notifications.add(new ConfigEntry("useSounds", ConfigType.BOOLEAN,
                Config.COMMON.useSounds.get().toString(), false, "true"));
        notifications.add(new ConfigEntry("useToasts", ConfigType.BOOLEAN,
                Config.COMMON.useToasts.get().toString(), false, "true"));
        notifications.add(new ConfigEntry("defaultStageIcon", ConfigType.ITEM,
                Config.COMMON.defaultStageIcon.get(), false, "historystages:research_scroll"));
        commonSections.add(notifications);

        ConfigSection individualCommon = new ConfigSection("editor.historystages.config.individual_stages");
        individualCommon.add(new ConfigEntry("individualLockItemPickup", ConfigType.BOOLEAN,
                Config.COMMON.individualLockItemPickup.get().toString(), false, "true"));
        individualCommon.add(new ConfigEntry("individualLockLoot", ConfigType.BOOLEAN,
                Config.COMMON.individualLockLoot.get().toString(), false, "true"));
        individualCommon.add(new ConfigEntry("individualDropOnRevoke", ConfigType.BOOLEAN,
                Config.COMMON.individualDropOnRevoke.get().toString(), false, "true"));
        individualCommon.add(new ConfigEntry("individualLockBlockBreaking", ConfigType.BOOLEAN,
                Config.COMMON.individualLockBlockBreaking.get().toString(), false, "true"));
        individualCommon.add(new ConfigEntry("individualLockedBlockBreakSpeedMultiplier",
                ConfigType.DOUBLE,
                Config.COMMON.individualLockedBlockBreakSpeedMultiplier.get().toString(), false,
                "0.05", 0.001, 1.0));
        individualCommon.add(new ConfigEntry("individualLockItemUsage", ConfigType.BOOLEAN,
                Config.COMMON.individualLockItemUsage.get().toString(), false, "true"));
        individualCommon.add(new ConfigEntry("individualLockBlockInteraction", ConfigType.BOOLEAN,
                Config.COMMON.individualLockBlockInteraction.get().toString(), false, "true"));
        individualCommon.add(new ConfigEntry("individualBroadcastChat", ConfigType.BOOLEAN,
                Config.COMMON.individualBroadcastChat.get().toString(), false, "true"));
        individualCommon.add(new ConfigEntry("individualUnlockMessageFormat", ConfigType.STRING,
                Config.COMMON.individualUnlockMessageFormat.get(), false,
                "&fYou have unlocked &b{stage}&f!"));
        individualCommon.add(new ConfigEntry("individualUseActionbar", ConfigType.BOOLEAN,
                Config.COMMON.individualUseActionbar.get().toString(), false, "false"));
        individualCommon.add(new ConfigEntry("individualUseSounds", ConfigType.BOOLEAN,
                Config.COMMON.individualUseSounds.get().toString(), false, "true"));
        individualCommon.add(new ConfigEntry("individualUseToasts", ConfigType.BOOLEAN,
                Config.COMMON.individualUseToasts.get().toString(), false, "true"));
        commonSections.add(individualCommon);

        ConfigSection research = new ConfigSection("editor.historystages.config.research");
        research.add(new ConfigEntry("researchTimeInSeconds", ConfigType.INTEGER,
                Config.COMMON.researchTimeInSeconds.get().toString(), false, "20", 1, 86400));
        research.add(new ConfigEntry("showDependencyScreenInPedestal", ConfigType.BOOLEAN,
                Config.COMMON.showDependencyScreenInPedestal.get().toString(), false, "true"));
        research.add(new ConfigEntry("lockScrollWhileResearching", ConfigType.BOOLEAN,
                Config.COMMON.lockScrollWhileResearching.get().toString(), false, "false"));
        research.add(new ConfigEntry("researchBoosters", ConfigType.BOOSTER_LIST,
                encodeBoosterList(Config.COMMON.researchBoosters.get()), false, ""));
        commonSections.add(research);

        ConfigSection lootReplace = new ConfigSection("editor.historystages.config.loot_replacements");
        lootReplace.add(new ConfigEntry("useReplacements", ConfigType.BOOLEAN,
                Config.COMMON.useReplacements.get().toString(), false, "false"));
        lootReplace.add(new ConfigEntry("replacementItems", ConfigType.ITEM_LIST,
                String.join(",", Config.COMMON.replacementItems.get()), false,
                "minecraft:cobblestone,minecraft:dirt"));
        lootReplace.add(new ConfigEntry("replacementTags", ConfigType.TAG_LIST,
                String.join(",", Config.COMMON.replacementTags.get()), false, ""));
        commonSections.add(lootReplace);

        ConfigSection structureLock = new ConfigSection("editor.historystages.config.structure_lock");
        structureLock.add(new ConfigEntry("structureCheckInterval", ConfigType.INTEGER,
                Config.COMMON.structureCheckInterval.get().toString(), false, "10", 1, 200));
        structureLock.add(new ConfigEntry("structureMessageEnabled", ConfigType.BOOLEAN,
                Config.COMMON.structureMessageEnabled.get().toString(), false, "true"));
        structureLock.add(new ConfigEntry("structureLockMessageFormat", ConfigType.STRING,
                Config.COMMON.structureLockMessageFormat.get(), false,
                "&cYou cannot enter &e{structure}&c yet!"));
        structureLock.add(new ConfigEntry("structureLockInChat", ConfigType.BOOLEAN,
                Config.COMMON.structureLockInChat.get().toString(), false, "false"));
        structureLock.add(new ConfigEntry("structureDamageEnabled", ConfigType.BOOLEAN,
                Config.COMMON.structureDamageEnabled.get().toString(), false, "false"));
        structureLock.add(new ConfigEntry("structureDamageAmount", ConfigType.DOUBLE,
                Config.COMMON.structureDamageAmount.get().toString(), false, "1.0", 0.1, 100.0));
        structureLock.add(new ConfigEntry("structureDamageInterval", ConfigType.INTEGER,
                Config.COMMON.structureDamageInterval.get().toString(), false, "20", 1, 600));
        structureLock.add(new ConfigEntry("structureBlockRightClick", ConfigType.BOOLEAN,
                Config.COMMON.structureBlockRightClick.get().toString(), false, "true"));
        structureLock.add(new ConfigEntry("structureBlockLeftClick", ConfigType.BOOLEAN,
                Config.COMMON.structureBlockLeftClick.get().toString(), false, "true"));
        structureLock.add(new ConfigEntry("structureBlockProjectiles", ConfigType.BOOLEAN,
                Config.COMMON.structureBlockProjectiles.get().toString(), false, "true"));
        commonSections.add(structureLock);

        ConfigSection biomeLock = new ConfigSection("editor.historystages.config.biome_lock");
        biomeLock.add(new ConfigEntry("biomeCheckInterval", ConfigType.INTEGER,
                Config.COMMON.biomeCheckInterval.get().toString(), false, "10", 1, 200));
        biomeLock.add(new ConfigEntry("biomeEffectsEnabled", ConfigType.BOOLEAN,
                Config.COMMON.biomeEffectsEnabled.get().toString(), false, "true"));
        biomeLock.add(new ConfigEntry("biomeEffects", ConfigType.EFFECT_LIST,
                encodeEffectList(Config.COMMON.biomeEffects.get()), false,
                "minecraft:blindness, 30, 0"));
        biomeLock.add(new ConfigEntry("biomeClearEffectsOnLeave", ConfigType.BOOLEAN,
                Config.COMMON.biomeClearEffectsOnLeave.get().toString(), false, "false"));
        biomeLock.add(new ConfigEntry("biomeMessageEnabled", ConfigType.BOOLEAN,
                Config.COMMON.biomeMessageEnabled.get().toString(), false, "true"));
        biomeLock.add(new ConfigEntry("biomeLockMessageFormat", ConfigType.STRING,
                Config.COMMON.biomeLockMessageFormat.get(), false,
                "&cYou cannot survive in &e{biome}&c yet!"));
        biomeLock.add(new ConfigEntry("biomeLockInChat", ConfigType.BOOLEAN,
                Config.COMMON.biomeLockInChat.get().toString(), false, "false"));
        biomeLock.add(new ConfigEntry("biomeDamageEnabled", ConfigType.BOOLEAN,
                Config.COMMON.biomeDamageEnabled.get().toString(), false, "true"));
        biomeLock.add(new ConfigEntry("biomeDamageAmount", ConfigType.DOUBLE,
                Config.COMMON.biomeDamageAmount.get().toString(), false, "1.0", 0.1, 100.0));
        biomeLock.add(new ConfigEntry("biomeDamageInterval", ConfigType.INTEGER,
                Config.COMMON.biomeDamageInterval.get().toString(), false, "20", 1, 600));
        biomeLock.add(new ConfigEntry("biomeBlockRightClick", ConfigType.BOOLEAN,
                Config.COMMON.biomeBlockRightClick.get().toString(), false, "true"));
        biomeLock.add(new ConfigEntry("biomeBlockLeftClick", ConfigType.BOOLEAN,
                Config.COMMON.biomeBlockLeftClick.get().toString(), false, "true"));
        biomeLock.add(new ConfigEntry("biomeBlockProjectiles", ConfigType.BOOLEAN,
                Config.COMMON.biomeBlockProjectiles.get().toString(), false, "true"));
        commonSections.add(biomeLock);

        ConfigSection lockMessages = new ConfigSection("editor.historystages.config.lock_messages");
        lockMessages.add(new ConfigEntry("msgDimensionUnknown", ConfigType.STRING,
                Config.COMMON.msgDimensionUnknown.get(), false, ""));
        lockMessages.add(new ConfigEntry("msgMobUnknown", ConfigType.STRING,
                Config.COMMON.msgMobUnknown.get(), false, ""));
        lockMessages.add(new ConfigEntry("msgItemLocked", ConfigType.STRING,
                Config.COMMON.msgItemLocked.get(), false, ""));
        lockMessages.add(new ConfigEntry("msgBlockLocked", ConfigType.STRING,
                Config.COMMON.msgBlockLocked.get(), false, ""));
        lockMessages.add(new ConfigEntry("msgEntityItemLocked", ConfigType.STRING,
                Config.COMMON.msgEntityItemLocked.get(), false, ""));
        lockMessages.add(new ConfigEntry("msgEnchantmentLocked", ConfigType.STRING,
                Config.COMMON.msgEnchantmentLocked.get(), false, ""));
        commonSections.add(lockMessages);
    }

    /**
     * The rows of graph.toml, generated by walking its spec rather than listed by hand. Hand
     * listing 93 entries would be a second place where the graph's defaults live, and a key
     * added to {@link GraphConfig} later would silently never appear here.
     *
     * <p>The five non-style tables become sections; the sixty style keys are held aside in
     * {@link #styleEntries} behind a single row that opens {@link GraphStyleScreen}.
     */
    private void buildGraphEntries() {
        graphSections = new ArrayList<>();
        styleEntries.clear();
        Map<String, String> current = GraphConfigCodec.collect();

        for (Map.Entry<String, List<GraphKey>> table : GraphConfigEntries.sections().entrySet()) {
            ConfigSection section = new ConfigSection(
                    "editor.historystages.config.graph.section." + table.getKey());
            for (GraphKey gk : table.getValue()) {
                section.add(toEntry(gk, current, "editor.historystages.config.graph." + gk.path()));
            }
            graphSections.add(section);
        }

        for (String collection : List.of("global", "individual")) {
            for (String state : List.of("unlocked", "reachable", "locked")) {
                List<ConfigEntry> block = new ArrayList<>();
                for (GraphKey gk : GraphConfigEntries.styleKeys(collection, state)) {
                    // Keyed by leaf, so all six blocks share ten labels instead of sixty.
                    block.add(toEntry(gk, current,
                            "editor.historystages.config.graph.style." + gk.leaf()));
                }
                styleEntries.put(collection + "." + state, block);
            }
        }

        ConfigSection styles = new ConfigSection("editor.historystages.config.graph.section.style");
        styles.add(new ConfigEntry("graph.style", ConfigType.SUBSCREEN, "", false, "",
                "editor.historystages.config.graph.style",
                "editor.historystages.config.graph.style.desc",
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, null, List.of(), null));
        graphSections.add(styles);

        warnAboutMissingLangKeys();
    }

    /** One generated row. The path is what goes on the wire; the key is the editor-side identity. */
    private ConfigEntry toEntry(GraphKey gk, Map<String, String> current, String labelKey) {
        ConfigType type = switch (gk.kind()) {
            case BOOLEAN -> ConfigType.BOOLEAN;
            case INTEGER -> ConfigType.INTEGER;
            case DOUBLE -> ConfigType.DOUBLE;
            case STRING -> ConfigType.STRING;
            case COLOR -> ConfigType.COLOR;
            case ENUM -> ConfigType.ENUM;
            case TEXTURE -> ConfigType.TEXTURE;
        };
        return new ConfigEntry(
                "graph." + gk.path(), type,
                current.getOrDefault(gk.path(), gk.defaultValue()), false,
                gk.defaultValue(),
                labelKey, labelKey + ".desc",
                gk.min() == null ? Double.NEGATIVE_INFINITY : gk.min(),
                gk.max() == null ? Double.POSITIVE_INFINITY : gk.max(),
                gk.path(), gk.enumConstants(), gk.enumType());
    }

    /**
     * With no unit test able to reach the generated key list — the moddev plugin keeps NeoForge
     * off the test classpath — this is the only thing between a forgotten translation and a row
     * that quietly shows a raw toml path.
     */
    private void warnAboutMissingLangKeys() {
        for (ConfigEntry entry : graphEntries()) {
            if (!I18n.exists(entry.labelKey)) {
                LOGGER.warn("Graph config row {} has no label key {}",
                        entry.key, entry.labelKey);
            }
            if (!I18n.exists(entry.descKey)) {
                LOGGER.warn("Graph config row {} has no description key {}",
                        entry.key, entry.descKey);
            }
        }
    }

    /** Every graph row, including the style blocks that live behind their own screen. */
    private List<ConfigEntry> graphEntries() {
        List<ConfigEntry> all = new ArrayList<>();
        if (graphSections != null) {
            for (ConfigSection section : graphSections) all.addAll(section.entries);
        }
        for (List<ConfigEntry> block : styleEntries.values()) all.addAll(block);
        return all;
    }

    /**
     * Every row on every tab. The four callers that used to each assemble their own list are
     * why the graph rows have to be added in one place: three of them would have been easy to
     * update and one easy to forget.
     */
    private List<ConfigEntry> allEntries() {
        List<ConfigEntry> all = new ArrayList<>();
        if (clientSections != null) {
            for (ConfigSection section : clientSections) all.addAll(section.entries);
        }
        if (commonSections != null) {
            for (ConfigSection section : commonSections) all.addAll(section.entries);
        }
        all.addAll(graphEntries());
        return all;
    }

    private void resetToDefaults() {
        for (ConfigEntry entry : allEntries()) {
            entry.value = entry.defaultValue;
        }
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

    /**
     * The graph counterpart of {@link #onCommonConfigSynced()}. Save sends every graph row that has
     * a toml path, style blocks included, so an editor still holding its build-time snapshot would
     * push that snapshot back and undo whichever admin saved first.
     */
    public static void onGraphConfigSynced() {
        ConfigEditorScreen screen = active.get();
        if (screen != null && screen.graphSections != null) screen.refreshGraphValues();
    }

    /**
     * Merges the freshly synced graph values into every graph row, the six style blocks included —
     * {@link GraphStyleScreen} edits those very objects rather than copies. Same split as
     * {@link #refreshCommonValues()}: untouched rows follow the server, edited rows keep the edit
     * and only get a new baseline.
     */
    private void refreshGraphValues() {
        Map<String, String> fresh = GraphConfigCodec.collect();
        for (ConfigEntry entry : graphEntries()) {
            // Null on the SUBSCREEN row, which holds no value of its own.
            if (entry.path == null) continue;
            String synced = fresh.get(entry.path);
            if (synced == null) continue;
            boolean untouched = entry.value.equals(entry.initialValue);
            entry.initialValue = synced;
            if (untouched) entry.value = synced;
        }
    }

    /** Package-private: {@link GraphStyleScreen} saves through this screen and shows the same marker. */
    boolean hasChanges() {
        for (ConfigEntry entry : allEntries()) {
            if (!entry.value.equals(entry.initialValue)) return true;
        }
        return false;
    }

    private List<ConfigSection> getActiveSections() {
        return switch (activeTab) {
            case 0 -> clientSections;
            case 2 -> graphSections;
            default -> commonSections;
        };
    }

    /** The six style blocks, for {@link GraphStyleScreen} to edit in place. */
    Map<String, List<ConfigEntry>> styleEntries() {
        return styleEntries;
    }

    private void switchTab(int tab) {
        if (activeTab != tab) {
            activeTab = tab;
            // Its row is on the tab we just left.
            closeDropdown();
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
            height += ConfigRowList.SECTION_HEADER_HEIGHT;
            height += section.entries.size() * ConfigRowList.ENTRY_HEIGHT;
            height += ConfigRowList.SECTION_GAP;
        }
        return height;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op — we draw our own background in render() and want to avoid 1.21's menu blur shader
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScroll.settle((float) scrollOffset, 0.5f);
        syncPickerState();

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
            guiGraphics.fill(contentLeft, y, contentRight, y + ConfigRowList.SECTION_HEADER_HEIGHT, 0x30FFFFFF);
            guiGraphics.drawString(this.font,
                    Component.translatable(section.titleKey).getString(),
                    contentLeft + 5, y + 7, 0xFFCC00, false);
            y += ConfigRowList.SECTION_HEADER_HEIGHT;

            // Entries
            for (ConfigEntry entry : section.entries) {
                if (y + ConfigRowList.ENTRY_HEIGHT > listTop - 20 && y < listBottom + 20) {
                    configRows.renderRow(guiGraphics, entry, contentLeft, y, contentRight, mouseX, mouseY);

                    // Check hover for tooltip
                    boolean entryHovered = mouseX >= contentLeft && mouseX <= contentRight
                            && mouseY >= y && mouseY < y + ConfigRowList.ENTRY_HEIGHT
                            && mouseY >= listTop && mouseY <= listBottom;
                    // A missing .desc key resolves to the raw key, so ask I18n instead of
                    // checking the resolved string for emptiness.
                    if (entryHovered && I18n.exists(entry.descKey)) {
                        currentHovered = entry.key;
                        currentDescription = Component.translatable(entry.descKey).getString();
                    }
                }
                y += ConfigRowList.ENTRY_HEIGHT;
            }

            y += ConfigRowList.SECTION_GAP;
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
            int barHeight = Math.max(20, (int) ((float) scrollAreaHeight / (maxScroll + scrollAreaHeight) * scrollAreaHeight));
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
            drawSmallText(guiGraphics, Component.translatable("editor.historystages.unsaved").getString(), dotX + 9, this.height - 24, 0xFFCC00);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // Enum picker popup. Outside the scissor above, so it is not clipped to the scrolling
        // list, and after super.render so it covers the buttons. It keeps being drawn after it
        // collapses, which is what lets it roll back up instead of vanishing.
        if (openDropdown != null) {
            // The row drew a collapsed button here; the live one goes on top so the frame turns
            // gold and the caret flips over while the popup is open.
            openDropdown.renderButton(guiGraphics, this.font, mouseX, mouseY);
            openDropdown.renderPopup(guiGraphics, this.font, mouseX, mouseY);
            // A row tooltip under an open picker is noise.
            if (openDropdown.isExpanded()) currentHovered = null;
        }

        // Item picker overlay (single-item selector for ITEM entries).
        // Lifted above everything drawn so far: text is batched and flushed after the picker's
        // panel fills, so the config rows and button labels underneath would otherwise bleed
        // through it. Same treatment the stage editor gives its own pickers.
        if (itemPickerOverlay != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, 0, 200);
            guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);
            itemPickerOverlay.render(guiGraphics, this.font, mouseX, mouseY);
            guiGraphics.pose().popPose();
            // A config-row tooltip drawn on top of the picker makes no sense.
            currentHovered = null;
        }

        // Tooltip last, after everything else including super — it belongs on top of all of it.
        tooltip.render(guiGraphics, this.font, currentHovered, currentDescription,
                tooltipMouseX, tooltipMouseY, this.width, this.height);
    }

    /** Package-private: the unsaved-changes marker is drawn on the style screen too. */
    static void drawSmallText(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(SMALL_SCALE, SMALL_SCALE, 1.0f);
        guiGraphics.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Enum picker first: its popup is drawn on top of everything, so it must get the click
        // before the row underneath it does.
        if (openDropdown != null) {
            boolean wasExpanded = openDropdown.isExpanded();
            if (openDropdown.mouseClicked(mouseX, mouseY)) return true;
            closeDropdown();
            // A click that only dismissed an open popup stops there — it must not also toggle
            // whatever row happened to be underneath.
            if (wasExpanded) return true;
        }

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
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    switchTab(i);
                    return true;
                }
            }
        }

        if (super.mouseClicked(mouseX, mouseY, button)) return true;

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
            y += ConfigRowList.SECTION_HEADER_HEIGHT;

            for (ConfigEntry entry : section.entries) {
                if (configRows.hitTest(entry, contentLeft, contentRight, y, mouseX, mouseY)) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    handleEntryClick(entry, contentLeft, y);
                    return true;
                }
                y += ConfigRowList.ENTRY_HEIGHT;
            }
            y += ConfigRowList.SECTION_GAP;
        }

        return false;
    }

    /**
     * The open picker overlay, or null — the item picker for ITEM rows, the texture picker for
     * TEXTURE ones. One slot rather than one field per kind: only one can be open at a time, and
     * every path that dismisses, renders or forwards input to it is identical for both.
     */
    private PickerOverlay itemPickerOverlay;
    private ConfigEntry pickingItemEntry;

    /**
     * The open enum picker, or null. Anchored to the screen position its row had when it was
     * clicked, and drawn outside the list's scissor — a popup clipped to the scrolling area
     * would be cut off after two entries. Because the anchor is fixed, scrolling closes it
     * rather than letting it drift away from its row.
     */
    private EnumDropdown openDropdown;

    /** Drops the open enum picker. Safe to call when there is none. */
    private void closeDropdown() {
        openDropdown = null;
    }

    /**
     * @param contentLeft left edge of the row, needed to place the popup under the value control
     * @param rowY        the row's screen y at click time
     */
    private void openEnumDropdown(ConfigEntry entry, int contentLeft, int rowY) {
        if (entry.enumConstants.isEmpty()) return;
        EnumDropdown dropdown = new EnumDropdown(
                entry.enumConstants, entry.value, ConfigRowList.DROPDOWN_MIN_WIDTH,
                constant -> ConfigRowList.enumLabel(entry.enumType, constant),
                picked -> entry.value = picked);
        // Placed exactly over the collapsed button the row drew, so the popup grows out of the
        // control the user clicked rather than appearing beside it.
        dropdown.setPosition(configRows.controlX(entry, contentLeft),
                rowY + ConfigRowList.DROPDOWN_INSET_Y);
        dropdown.expand();
        openDropdown = dropdown;
    }

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

    /**
     * @param contentLeft left edge of the row, and {@code rowY} its screen y — only the ENUM
     *                    case needs them, to anchor its popup to the row that was clicked.
     */
    private void handleEntryClick(ConfigEntry entry, int contentLeft, int rowY) {
        playClick();
        switch (entry.type) {
            case BOOLEAN -> {
                boolean current = Boolean.parseBoolean(entry.value);
                entry.value = String.valueOf(!current);
            }
            case INTEGER, DOUBLE, STRING -> this.minecraft.setScreen(new ValueInputScreen(this, entry));
            case ITEM_LIST -> this.minecraft.setScreen(new ItemListEditorScreen(this, entry));
            case TAG_LIST -> this.minecraft.setScreen(new TagListEditorScreen(this, entry));
            case ITEM -> openItemPicker(entry);
            case BOOSTER_LIST -> this.minecraft.setScreen(new BoosterListEditorScreen(this, entry));
            case EFFECT_LIST -> this.minecraft.setScreen(new EffectListEditorScreen(this, entry));
            case MULTI_STAGE_POLICY -> {
                boolean strict = !"LENIENT".equalsIgnoreCase(entry.value);
                entry.value = strict ? "LENIENT" : "STRICT";
            }
            case ENUM -> openEnumDropdown(entry, contentLeft, rowY);
            case COLOR -> this.minecraft.setScreen(new ColorInputScreen(this, entry));
            case SUBSCREEN -> this.minecraft.setScreen(new GraphStyleScreen(this));
            case TEXTURE -> openTexturePicker(entry);
        }
    }

    private void openItemPicker(ConfigEntry entry) {
        openPicker(entry, new SearchableItemList(itemId -> {
            entry.value = itemId;
            closePicker();
        }));
    }

    private void openTexturePicker(ConfigEntry entry) {
        openPicker(entry, new SearchableTextureList(texture -> {
            entry.value = texture;
            closePicker();
        }));
    }

    private void openPicker(ConfigEntry entry, PickerOverlay picker) {
        pickingItemEntry = entry;
        itemPickerOverlay = picker;
        picker.show(this.width / 2, this.height / 2, this.width);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // The popup is anchored to a fixed screen position, so scrolling would slide the list
        // out from under it. Closing is the honest answer.
        closeDropdown();

        syncPickerState();
        if (itemPickerOverlay != null) return itemPickerOverlay.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        double delta = scrollY;
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 16));
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // The picker sits on top and owns the pointer while it is open. Without this its
        // scrollbar takes the press, sets itself dragging, and then never hears another mouse
        // move — the thumb stays where it jumped to and nothing follows the cursor.
        syncPickerState();
        if (itemPickerOverlay != null && itemPickerOverlay.mouseDragged(mouseX, mouseY)) return true;

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
        // Likewise: without this the picker's drag flag is never cleared, so its thumb keeps
        // rendering as held and the next press behaves as though the button were still down.
        syncPickerState();
        if (itemPickerOverlay != null && itemPickerOverlay.mouseReleased()) return true;

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
        if (itemPickerOverlay != null) return itemPickerOverlay.charTyped(c);
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

    /**
     * Package-private: {@link GraphStyleScreen} has its own Save button, and it has to be the
     * same save — one that covers every tab, not just the block on screen.
     */
    void saveConfig() {
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

        // Send graph.toml to the server, keyed by toml path. The style rows come along here
        // too — GraphStyleScreen edits these very objects rather than keeping its own copies.
        Map<String, String> graphValues = new HashMap<>();
        for (ConfigEntry entry : graphEntries()) {
            if (entry.path != null) graphValues.put(entry.path, entry.value);
        }
        PacketHandler.sendToServer(new SaveGraphConfigPacket(graphValues));

        // Update initial values so hasChanges() returns false
        for (ConfigEntry entry : allEntries()) {
            entry.initialValue = entry.value;
        }

        // JEI hiding (Issue #64): live-apply config changes if JEI is loaded.
        if (net.neoforged.fml.ModList.get().isLoaded("jei")) {
            try {
                net.bananemdnsa.historystages.compat.jei.JEIPlugin.tryApplyDiff();
            } catch (Throwable ignored) {}
        }

        // EMI: rebuild its index so booster/config changes show up immediately.
        if (net.neoforged.fml.ModList.get().isLoaded("emi")) {
            try {
                net.bananemdnsa.historystages.compat.emi.EmiReloadBridge.reloadIfPresent();
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Applies the Client tab's values and writes client.toml back out.
     * <p>
     * The per-key handling lives in {@link ClientConfigSync}, which is also the list the reflection
     * check runs against — this was a switch, and two rows the editor offered had no case at all.
     * <p>
     * The save is what makes the edit outlive the session: without it the values only ever lived in
     * the in-memory spec, and the next launch read the untouched file back. The common tab has had
     * the equivalent {@code COMMON_SPEC.save()} in {@link SaveConfigPacket} all along.
     */
    private void applyClientConfig(Map<String, String> values) {
        ClientConfigSync.applyAll(values);
        Config.CLIENT_SPEC.save();
    }

    @Override
    public void onClose() {
        closeDropdown();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    // --- Inner data classes ---

    public enum ConfigType {
        BOOLEAN, INTEGER, DOUBLE, STRING, ITEM_LIST, TAG_LIST, ITEM, BOOSTER_LIST, EFFECT_LIST,
        MULTI_STAGE_POLICY, ENUM, COLOR, TEXTURE,
        /** A row that opens another screen instead of editing a value of its own. */
        SUBSCREEN
    }

    /** Encode the live biome-effect config list as the editor's internal string: "id,seconds,amp;...". */
    private static String encodeEffectList(java.util.List<? extends String> entries) {
        return entries.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.joining(";"));
    }

    /** Encode the live booster config list as the editor's internal string:
     *  "block,speed,cost,tier,mode;..." (legacy 3-token rows are accepted on read but written 5-token). */
    private static String encodeBoosterList(java.util.List<? extends String> entries) {
        return entries.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.joining(";"));
    }

    public static class ConfigEntry {
        public final String key;
        public final ConfigType type;
        public String value;
        String initialValue;
        final boolean isClient;
        /** Public so dialogs outside this package can offer a reset-to-default control. */
        public final String defaultValue;
        /** Lang key of the hover tooltip. */
        final String descKey;
        /** Inclusive bounds for INTEGER / DOUBLE entries, mirroring Config.java's defineInRange. */
        final double min;
        final double max;

        /**
         * Dotted toml path for graph entries, null for Client and Common ones. Graph values are
         * addressed by path on the wire; {@link #key} stays the editor-side identity used for
         * lang lookup and hover state.
         */
        final String path;
        /** Lang key of the row label. Split from {@link #key} so the ten style rows can share
         *  one set of labels across all six blocks instead of repeating them sixty times. */
        public final String labelKey;
        /** Allowed values for an ENUM row; empty otherwise. */
        public final List<String> enumConstants;
        /**
         * Simple name of the enum type behind an ENUM row, null otherwise. Part of the lang key
         * because constant names are not unique across the graph's enums: {@code SOLID} is both
         * an edge style and a canvas background, and German calls those two different things.
         */
        public final String enumType;

        /**
         * True while this row shows a value it does not own — the per-stage style editor draws
         * such rows dimmed. Always false for Client, Common and Graph rows, which have no layer
         * to inherit from.
         */
        public boolean inherited;

        /**
         * True when this row may be sent back to inheriting, which is what draws the clear ×.
         * Separate from {@link #inherited} because a row can be clearable and currently set.
         */
        public boolean clearable;

        /**
         * True when there is no single value to show — the per-stage style editor's all-states
         * tab, where the three state blocks it inherits from disagree. {@link ConfigRowList}
         * then draws the "differs per state" hint instead of the control, because every control
         * would have to invent a value to draw. Always false for Client, Common and Graph rows.
         */
        public boolean varies;

        ConfigEntry(String key, ConfigType type, String value, boolean isClient,
                    String defaultValue) {
            this(key, type, value, isClient, defaultValue,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        }

        ConfigEntry(String key, ConfigType type, String value, boolean isClient,
                    String defaultValue, double min, double max) {
            this(key, type, value, isClient, defaultValue,
                    "editor.historystages.config." + key,
                    "editor.historystages.config." + key + ".desc",
                    min, max, null, List.of(), null);
        }

        ConfigEntry(String key, ConfigType type, String value, boolean isClient,
                    String defaultValue, String labelKey, String descKey,
                    double min, double max, String path, List<String> enumConstants,
                    String enumType) {
            this.key = key;
            this.type = type;
            this.value = value;
            this.initialValue = value;
            this.isClient = isClient;
            this.defaultValue = defaultValue;
            this.labelKey = labelKey;
            this.descKey = descKey;
            this.min = min;
            this.max = max;
            this.path = path;
            this.enumConstants = enumConstants == null ? List.of() : enumConstants;
            this.enumType = enumType;
        }

        /**
         * A row for a per-stage style override. The value is whatever applies right now; whether
         * this stage owns it is carried by {@link #inherited}, which the caller sets.
         *
         * <p>Public because {@link StageStyleScreen} builds rows the config editor never sees,
         * and the twelve-argument constructor should not have to be repeated to do it.
         */
        public static ConfigEntry styleRow(String key, ConfigType type, String value,
                                           String defaultValue, String labelKey,
                                           double min, double max,
                                           List<String> enumConstants, String enumType) {
            ConfigEntry entry = new ConfigEntry(key, type, value, false, defaultValue,
                    labelKey, labelKey + ".desc", min, max, null, enumConstants, enumType);
            entry.clearable = true;
            return entry;
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
     * Shows a list of current items with remove buttons and an add button using SearchableItemList overlay.
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
            super(Component.translatable(entry.labelKey));
            this.parent = parent;
            this.entry = entry;
            this.items = new ArrayList<>();
            if (!entry.value.isEmpty()) {
                for (String s : entry.value.split(",")) {
                    String trimmed = s.trim();
                    if (!trimmed.isEmpty()) items.add(trimmed);
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
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // No-op — avoid 1.21's menu blur shader
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
            smoothScroll.settle((float) scrollOffset, 0.5f);
            syncOverlayState();
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
                        Item item = BuiltInRegistries.ITEM.get(rl);
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
                int barHeight = Math.max(20, (int) ((float) scrollAreaHeight / (maxScroll + scrollAreaHeight) * scrollAreaHeight));
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

            if (super.mouseClicked(mouseX, mouseY, button)) return true;

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
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            double delta = scrollY;
            syncOverlayState();
            if (itemOverlay != null) return itemOverlay.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        public void onClose() {
            saveAndClose();
        }

        @Override
        public boolean isPauseScreen() { return true; }
    }

    /**
     * Screen for editing a TAG_LIST config entry (e.g. replacementTags).
     * Shows a list of current tags with remove buttons and an add button using SearchableTagList overlay.
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
            super(Component.translatable(entry.labelKey));
            this.parent = parent;
            this.entry = entry;
            this.tags = new ArrayList<>();
            if (!entry.value.isEmpty()) {
                for (String s : entry.value.split(",")) {
                    String trimmed = s.trim();
                    if (!trimmed.isEmpty()) tags.add(trimmed);
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
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            // No-op — avoid 1.21's menu blur shader
        }

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
            smoothScroll.settle((float) scrollOffset, 0.5f);
            syncOverlayState();
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
                int barHeight = Math.max(20, (int) ((float) scrollAreaHeight / (maxScroll + scrollAreaHeight) * scrollAreaHeight));
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

            if (super.mouseClicked(mouseX, mouseY, button)) return true;

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
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            double delta = scrollY;
            syncOverlayState();
            if (tagOverlay != null) return tagOverlay.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
            if (keyCode == 256) { saveAndClose(); return true; }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char c, int modifiers) {
            syncOverlayState();
            if (tagOverlay != null) return tagOverlay.charTyped(c);
            return super.charTyped(c, modifiers);
        }

        @Override
        public void onClose() {
            saveAndClose();
        }

        @Override
        public boolean isPauseScreen() { return true; }
    }

    /**
     * Modal dialog for editing integer, decimal or string config values. Numeric entries get
     * the range declared on the ConfigEntry, so the dialog cannot produce a value that the
     * underlying ModConfigSpec would reject.
     */
    /**
     * Takes any {@link Screen} as its parent, not just the config editor: {@link GraphStyleScreen}
     * shows the same rows and a value edited there has to come back to it rather than to the tab.
     */
    static class ValueInputScreen extends AbstractInputScreen {
        private final Screen parent;
        private final ConfigEntry entry;

        ValueInputScreen(Screen parent, ConfigEntry entry) {
            super(parent, Component.translatable(entry.labelKey));
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
     * Editor for the researchBoosters config list. Each row shows a block icon,
     * its ID and two EditBoxes for speed / cost percent (0-90). The "Add" button
     * opens a SearchableItemList to pick a block.
     *
     * Internal entry serialization: "block_id,speed,cost" joined by ';'.
     */
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
            super(Component.translatable(entry.labelKey));
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
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}

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
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            syncOverlayState();
            if (effectOverlay != null) return effectOverlay.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 16));
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
            super(Component.translatable(entry.labelKey));
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
         * scroll the full ID horizontally. Inlined from {@code AbstractSearchableList} so
         * this Screen-based editor doesn't have to inherit it.
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
        public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {}

        @Override
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            smoothScroll.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
            smoothScroll.settle((float) scrollOffset, 0.5f);
            syncOverlayState();
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
                        Item item = BuiltInRegistries.ITEM.get(rl);
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
                // Track + thumb, 6px wide for an easier hit target.
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

            // Scrollbar takes precedence over EditBoxes/buttons so a click on it always wins.
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
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            syncOverlayState();
            if (itemOverlay != null) return itemOverlay.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - scrollY * 16));
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

        private static class BoosterRow {
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
        public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

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
                Item item = BuiltInRegistries.ITEM.get(rl);
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
