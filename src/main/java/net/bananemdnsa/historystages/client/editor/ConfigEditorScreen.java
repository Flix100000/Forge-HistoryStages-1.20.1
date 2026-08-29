package net.bananemdnsa.historystages.client.editor;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.api.editor.CustomFieldScreens;
import net.bananemdnsa.historystages.api.config.AddonConfigField;
import net.bananemdnsa.historystages.api.config.AddonConfigSection;
import net.bananemdnsa.historystages.data.config.AddonConfigSections;
import net.bananemdnsa.historystages.data.graph.GraphConfigCodec;
import net.bananemdnsa.historystages.data.graph.GraphConfigEntries;
import net.bananemdnsa.historystages.data.graph.GraphKey;
import net.bananemdnsa.historystages.data.ScrollCompletion;
import net.bananemdnsa.historystages.data.scroll.OpenScrollChapters;
import net.bananemdnsa.historystages.data.scroll.OpenScrollOverviewBlocks;
import net.bananemdnsa.historystages.data.scroll.OpenScrollSort;
import net.bananemdnsa.historystages.data.scroll.OpenScrollVisibility;
import net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLayout;
import net.bananemdnsa.historystages.network.serverbound.SaveGraphConfigPacket;
import net.bananemdnsa.historystages.api.editor.widget.AbstractInputScreen;
import net.bananemdnsa.historystages.api.editor.widget.InputField;
import net.bananemdnsa.historystages.api.editor.widget.InputValues;
import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;
import net.bananemdnsa.historystages.data.config.ConfigSpecCodec;
import net.bananemdnsa.historystages.data.config.LegacyConfigMigration;
import net.bananemdnsa.historystages.client.editor.toast.EditorToast;
import net.bananemdnsa.historystages.client.editor.toast.EditorToastHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncConfigPacket;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.serverbound.SaveConfigPacket;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Fade;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.api.editor.widget.FormattedTextScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import net.bananemdnsa.historystages.client.editor.dialog.ColorInputScreen;
import net.bananemdnsa.historystages.client.editor.widget.dropdown.EnumDropdown;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
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

    /**
     * Visual values that have no row of their own because another row's sub-screen edits them.
     * Same arrangement as {@link #styleEntries}: they must still be saved, compared and refreshed,
     * so every place that walks the sections has to walk these too — {@link #allEntries()},
     * {@link #saveConfig()}, {@link #refreshVisualValues()} and {@link #findEntry(String)}.
     * Keeping them out of a section is what stops them from drawing a second, redundant row.
     */
    private final List<ConfigEntry> visualSubEntries = new ArrayList<>();
    /** graph.toml's five non-style tables, generated from the spec. */
    private List<ConfigSection> graphSections;
    /**
     * One section per addon-registered {@link AddonConfigSection}, in the order
     * {@link AddonConfigSections#all()} returns. Empty (never null after {@link #init()}) when no
     * addon has registered anything — that is what keeps the tab itself from appearing.
     */
    private List<ConfigSection> addonSections;
    /**
     * Row key ({@link ConfigEntry#key}) to the {@link AddonConfigField} it renders, for every row
     * in {@link #addonSections}. Built alongside that list. {@link #saveConfig()} needs it to call
     * a CLIENT field's own {@code write()} directly — COMMON fields are written on the server
     * instead, reached through {@link AddonConfigSections#commonEntries()}, not through this map.
     */
    private Map<String, AddonConfigField> addonFieldsByKey;
    /**
     * The six node-style blocks, keyed {@code "global.unlocked"} and so on. Edited by
     * {@link GraphStyleScreen} but owned here, so one Save covers them and the unsaved-changes
     * marker stays honest across both screens.
     */
    private final Map<String, List<ConfigEntry>> styleEntries = new LinkedHashMap<>();

    /** Hover tooltip, including its own appear-delay bookkeeping. */
    private final EditorTooltip tooltip = new EditorTooltip();

    // Tab layout
    /** The three tabs every install has. {@link #tabKeys} appends a fourth when an addon
     *  registers a section — never fewer, never reordered, so indices 0-2 always mean what
     *  they mean today. */
    private static final String[] BASE_TAB_KEYS = {
            "editor.historystages.tab.visual",
            "editor.historystages.tab.gameplay",
            "editor.historystages.tab.graph"
    };
    /**
     * The tab bar actually shown, computed once in {@link #init()}. Not {@code static final}
     * like {@link #BASE_TAB_KEYS} any more: whether the Addons tab exists depends on whether
     * any addon has registered a section, which is only known once — and stable for — this
     * screen's lifetime.
     */
    private String[] tabKeys;
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

    /** Set once a session, so a resize or a second visit to the screen does not repeat the notice. */
    private static boolean migrationNoticeShown;

    /**
     * Tells a pack author their settings moved, in the place they went looking for them.
     *
     * <p>The migration also writes a log line, but someone who updates, launches and opens the
     * editor to find a setting never reads the log. Only meaningful in singleplayer: the counter
     * lives on whichever side ran the migration, and on a dedicated server that is not this
     * client. That is the case that matters, because a pack is built locally.
     */
    private static void showMigrationNoticeOnce() {
        if (migrationNoticeShown || !LegacyConfigMigration.migrated()) return;
        migrationNoticeShown = true;
        EditorToastHandler.show(EditorToast.Level.INFO,
                Component.translatable("editor.historystages.toast.config_migrated.title"),
                Component.translatable("editor.historystages.toast.config_migrated.message",
                        LegacyConfigMigration.carriedCount()));
    }

    @Override
    protected void init() {
        // Build once per instance, not once per init(): init() also runs on every window resize,
        // and rebuilding there would throw away whatever the admin has typed but not saved yet.
        // Staying stale is instead handled by onCommonConfigSynced().
        if (clientSections == null) buildConfigEntries();
        if (graphSections == null) buildGraphEntries();
        if (addonSections == null) buildAddonConfigEntries();

        showMigrationNoticeOnce();

        // Addons tab only when it has something to show — a tab that opens onto nothing would
        // promise a feature that isn't there. Built fresh every init() (not just guarded like
        // the section lists above) since it only reads addonSections, which is already stable.
        tabKeys = addonSections.isEmpty()
                ? BASE_TAB_KEYS
                : Arrays.copyOf(BASE_TAB_KEYS, BASE_TAB_KEYS.length + 1);
        if (tabKeys.length > BASE_TAB_KEYS.length) {
            tabKeys[BASE_TAB_KEYS.length] = "editor.historystages.tab.addons";
        }
        // A shorter bar must never leave activeTab pointing past its end — it starts at 1
        // (Common), which every configuration has, so no clamp is needed here, but a tab that
        // was active and then vanished (addon unregistered mid-session — never happens today,
        // but the guard is cheap) falls back to Common rather than rendering nothing.
        if (activeTab >= tabKeys.length) activeTab = 1;

        active = new java.lang.ref.WeakReference<>(this);

        // Compute tab positions
        tabY = 30;
        tabX = new int[tabKeys.length];
        tabW = new int[tabKeys.length];
        // 100px per tab is what the three built-in tabs have always split 300px three ways;
        // a fourth tab extends the bar by the same amount instead of squeezing all four.
        int tabTotalWidth = 100 * tabKeys.length;
        int gap = 2;
        int tabStartX = this.width / 2 - tabTotalWidth / 2;
        int tabWidthEach = (tabTotalWidth - gap) / tabKeys.length;
        int x = tabStartX;
        for (int i = 0; i < tabKeys.length; i++) {
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
        // --- VISUAL CONFIG ---
        clientSections = new ArrayList<>();
        // Cleared with the sections: buildConfigEntries runs again on a rebuild, and appending
        // would otherwise leave a stale duplicate that fights the live one on save.
        visualSubEntries.clear();

        ConfigSection visuals = new ConfigSection("editor.historystages.config.visuals");
        visuals.add(new ConfigEntry("visuals.showTooltips", "showTooltips", ConfigType.BOOLEAN,
                Config.VISUAL.showTooltips.get().toString(), true, "true"));
        visuals.add(new ConfigEntry("visuals.showStageName", "showStageName", ConfigType.BOOLEAN,
                Config.VISUAL.showStageName.get().toString(), true, "true"));
        visuals.add(new ConfigEntry("visuals.showAllUntilComplete", "showAllUntilComplete", ConfigType.BOOLEAN,
                Config.VISUAL.showAllUntilComplete.get().toString(), true, "true"));
        visuals.add(new ConfigEntry("visuals.showLockIcons", "showLockIcons", ConfigType.BOOLEAN,
                Config.VISUAL.showLockIcons.get().toString(), true, "true"));
        visuals.add(new ConfigEntry("visuals.showBoosterTooltips", "showBoosterTooltips", ConfigType.BOOLEAN,
                Config.VISUAL.showBoosterTooltips.get().toString(), true, "true"));
        visuals.add(new ConfigEntry("visuals.showScrollTierTooltip", "showScrollTierTooltip", ConfigType.BOOLEAN,
                Config.VISUAL.showScrollTierTooltip.get().toString(), true, "true"));
        // Under visuals rather than open_scroll, where the rest of the document's settings sit: it
        // dims the world behind the scroll rather than describing anything on the page.
        visuals.add(new ConfigEntry("visuals.openScrollBackdrop", "openScrollBackdrop", ConfigType.INTEGER,
                Config.VISUAL.openScrollBackdrop.get().toString(), true, "60", 0, 100));
        visuals.add(new ConfigEntry("visuals.showWelcomeMessage", "showWelcomeMessage", ConfigType.BOOLEAN,
                Config.VISUAL.showWelcomeMessage.get().toString(), true, "true"));
        clientSections.add(visuals);

        ConfigSection jade = new ConfigSection("editor.historystages.config.jade");
        jade.add(new ConfigEntry("jade.showInfo", "jadeShowInfo", ConfigType.BOOLEAN,
                Config.VISUAL.jadeShowInfo.get().toString(), true, "true"));
        jade.add(new ConfigEntry("jade.showStageName", "jadeStageName", ConfigType.BOOLEAN,
                Config.VISUAL.jadeStageName.get().toString(), true, "true"));
        jade.add(new ConfigEntry("jade.showAllUntilComplete", "jadeShowAllUntilComplete", ConfigType.BOOLEAN,
                Config.VISUAL.jadeShowAllUntilComplete.get().toString(), true, "true"));
        clientSections.add(jade);

        ConfigSection individualClient = new ConfigSection("editor.historystages.config.individual_stages");
        individualClient.add(new ConfigEntry("individual_stages.showSilverLockIcons", "showSilverLockIcons", ConfigType.BOOLEAN,
                Config.VISUAL.showSilverLockIcons.get().toString(), true, "true"));
        individualClient.add(new ConfigEntry("individual_stages.showIndividualTooltips", "showIndividualTooltips", ConfigType.BOOLEAN,
                Config.VISUAL.showIndividualTooltips.get().toString(), true, "true"));
        clientSections.add(individualClient);

        ConfigSection dimLock = new ConfigSection("editor.historystages.config.dimension_lock");
        dimLock.add(new ConfigEntry("dimension_lock.useActionbar", "dimUseActionbar", ConfigType.BOOLEAN,
                Config.VISUAL.dimUseActionbar.get().toString(), true, "true"));
        dimLock.add(new ConfigEntry("dimension_lock.showInChat", "dimShowChat", ConfigType.BOOLEAN,
                Config.VISUAL.dimShowChat.get().toString(), true, "false"));
        dimLock.add(new ConfigEntry("dimension_lock.showStagesInChat", "dimShowStagesInChat", ConfigType.BOOLEAN,
                Config.VISUAL.dimShowStagesInChat.get().toString(), true, "true"));
        clientSections.add(dimLock);

        ConfigSection mobLock = new ConfigSection("editor.historystages.config.mob_lock");
        mobLock.add(new ConfigEntry("mob_lock.useActionbar", "mobUseActionbar", ConfigType.BOOLEAN,
                Config.VISUAL.mobUseActionbar.get().toString(), true, "true"));
        mobLock.add(new ConfigEntry("mob_lock.showInChat", "mobShowChat", ConfigType.BOOLEAN,
                Config.VISUAL.mobShowChat.get().toString(), true, "false"));
        mobLock.add(new ConfigEntry("mob_lock.showStagesInChat", "mobShowStagesInChat", ConfigType.BOOLEAN,
                Config.VISUAL.mobShowStagesInChat.get().toString(), true, "true"));
        clientSections.add(mobLock);

        ConfigSection structureVisuals = new ConfigSection("editor.historystages.config.structure_visuals");
        structureVisuals.add(new ConfigEntry("structure_overlay.structureBorderEnabled", "structureBorderEnabled", ConfigType.BOOLEAN,
                Config.VISUAL.structureBorderEnabled.get().toString(), true, "true"));
        structureVisuals.add(new ConfigEntry("structure_overlay.structureBorderDistance", "structureBorderDistance", ConfigType.DOUBLE,
                Config.VISUAL.structureBorderDistance.get().toString(), true, "8.0", 1.0, 32.0));
        structureVisuals.add(new ConfigEntry("structure_overlay.structureLockOverlayEnabled", "structureLockOverlayEnabled", ConfigType.BOOLEAN,
                Config.VISUAL.structureLockOverlayEnabled.get().toString(), true, "true"));
        structureVisuals.add(new ConfigEntry("structure_overlay.structureLockOverlayOpacity", "structureLockOverlayOpacity", ConfigType.DOUBLE,
                Config.VISUAL.structureLockOverlayOpacity.get().toString(), true, "0.3", 0.0, 1.0));
        clientSections.add(structureVisuals);

        // JEI hiding (Issue #64)
        ConfigSection jeiHiding = new ConfigSection("editor.historystages.config.jei_hiding");
        jeiHiding.add(new ConfigEntry("jei_hiding.hideLockedItemsInJei", "hideLockedItemsInJei", ConfigType.BOOLEAN,
                Config.VISUAL.hideLockedItemsInJei.get().toString(), true, "false"));
        jeiHiding.add(new ConfigEntry("jei_hiding.hideLockedRecipesInJei", "hideLockedRecipesInJei", ConfigType.BOOLEAN,
                Config.VISUAL.hideLockedRecipesInJei.get().toString(), true, "false"));
        // An ENUM row rather than a toggle: STRICT and LENIENT are two named policies, and
        // cycling through them one click at a time says nothing about what the other one is.
        jeiHiding.add(new ConfigEntry("jei_hiding.lockedItemMultiStagePolicy", ConfigType.ENUM,
                Config.VISUAL.lockedItemMultiStagePolicy.get().name(), true, "STRICT",
                "editor.historystages.config.lockedItemMultiStagePolicy",
                "editor.historystages.config.lockedItemMultiStagePolicy.desc",
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, null,
                java.util.Arrays.stream(Config.Visual.MultiStagePolicy.values()).map(Enum::name).toList(),
                Config.Visual.MultiStagePolicy.class.getSimpleName()));
        clientSections.add(jeiHiding);

        ConfigSection notifications = new ConfigSection("editor.historystages.config.notifications");
        notifications.add(new ConfigEntry("notifications.broadcastChat", "broadcastChat", ConfigType.BOOLEAN,
                Config.VISUAL.broadcastChat.get().toString(), true, "true"));
        notifications.add(new ConfigEntry("notifications.unlockMessageFormat", "unlockMessageFormat", ConfigType.RICH_TEXT,
                Config.VISUAL.unlockMessageFormat.get(), true,
                "&fThe world has entered the &b{stage}&f!"));
        notifications.add(new ConfigEntry("notifications.useActionbar", "useActionbar", ConfigType.BOOLEAN,
                Config.VISUAL.useActionbar.get().toString(), true, "false"));
        notifications.add(new ConfigEntry("notifications.useSounds", "useSounds", ConfigType.BOOLEAN,
                Config.VISUAL.useSounds.get().toString(), true, "true"));
        notifications.add(new ConfigEntry("notifications.useToasts", "useToasts", ConfigType.BOOLEAN,
                Config.VISUAL.useToasts.get().toString(), true, "true"));
        notifications.add(new ConfigEntry("notifications.defaultStageIcon", "defaultStageIcon", ConfigType.ITEM,
                Config.VISUAL.defaultStageIcon.get(), true, "historystages:research_scroll"));
        clientSections.add(notifications);

        // A section of its own rather than five more rows in the one above: the individual labels
        // read exactly like the global ones, so a single flat list would show every label twice
        // with nothing on the row to say which reach it means.
        ConfigSection notificationsIndividual =
                new ConfigSection("editor.historystages.config.notifications_individual");
        notificationsIndividual.add(new ConfigEntry("notifications.individual.broadcastChat",
                "individualBroadcastChat", ConfigType.BOOLEAN,
                Config.VISUAL.individualBroadcastChat.get().toString(), true, "true"));
        notificationsIndividual.add(new ConfigEntry("notifications.individual.unlockMessageFormat",
                "individualUnlockMessageFormat", ConfigType.RICH_TEXT,
                Config.VISUAL.individualUnlockMessageFormat.get(), true,
                "&fYou have unlocked &b{stage}&f!"));
        notificationsIndividual.add(new ConfigEntry("notifications.individual.useActionbar",
                "individualUseActionbar", ConfigType.BOOLEAN,
                Config.VISUAL.individualUseActionbar.get().toString(), true, "false"));
        notificationsIndividual.add(new ConfigEntry("notifications.individual.useSounds",
                "individualUseSounds", ConfigType.BOOLEAN,
                Config.VISUAL.individualUseSounds.get().toString(), true, "true"));
        notificationsIndividual.add(new ConfigEntry("notifications.individual.useToasts",
                "individualUseToasts", ConfigType.BOOLEAN,
                Config.VISUAL.individualUseToasts.get().toString(), true, "true"));
        clientSections.add(notificationsIndividual);

        ConfigSection lockMessages = new ConfigSection("editor.historystages.config.lock_messages");
        lockMessages.add(new ConfigEntry("lock_messages.dimensionUnknown", "msgDimensionUnknown", ConfigType.RICH_TEXT,
                Config.VISUAL.msgDimensionUnknown.get(), true, ""));
        lockMessages.add(new ConfigEntry("lock_messages.mobUnknown", "msgMobUnknown", ConfigType.RICH_TEXT,
                Config.VISUAL.msgMobUnknown.get(), true, ""));
        lockMessages.add(new ConfigEntry("lock_messages.itemLocked", "msgItemLocked", ConfigType.RICH_TEXT,
                Config.VISUAL.msgItemLocked.get(), true, ""));
        lockMessages.add(new ConfigEntry("lock_messages.blockLocked", "msgBlockLocked", ConfigType.RICH_TEXT,
                Config.VISUAL.msgBlockLocked.get(), true, ""));
        lockMessages.add(new ConfigEntry("lock_messages.entityItemLocked", "msgEntityItemLocked", ConfigType.RICH_TEXT,
                Config.VISUAL.msgEntityItemLocked.get(), true, ""));
        lockMessages.add(new ConfigEntry("lock_messages.enchantmentLocked", "msgEnchantmentLocked", ConfigType.RICH_TEXT,
                Config.VISUAL.msgEnchantmentLocked.get(), true, ""));
        clientSections.add(lockMessages);

        ConfigSection scrollTooltip = new ConfigSection("editor.historystages.config.scroll_tooltip");
        scrollTooltip.add(new ConfigEntry("scroll_tooltip.lines", ConfigType.SUBSCREEN,
                joinConfigList(Config.VISUAL.scrollTooltipLines.get()), true,
                String.join(";", ScrollTooltipLayout.defaultsEncoded()),
                "editor.historystages.config.scrollTooltipLines",
                "editor.historystages.config.scrollTooltipLines.desc",
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, null, List.of(), null));
        scrollTooltip.add(new ConfigEntry("scroll_tooltip.hideFulfilledDependencies", "hideFulfilledDependencies", ConfigType.BOOLEAN,
                Config.VISUAL.hideFulfilledDependencies.get().toString(), true, "false"));
        clientSections.add(scrollTooltip);

        // One row, not two: chapters and overview blocks answer the same question — what the
        // document shows, in which order — and they were always going to open the same screen.
        // The blocks value therefore has no row of its own and rides in visualSubEntries.
        ConfigSection openScroll = new ConfigSection("editor.historystages.config.open_scroll");
        openScroll.add(new ConfigEntry("open_scroll.chapters", ConfigType.SUBSCREEN,
                joinConfigList(Config.VISUAL.openScrollChapters.get()), true,
                String.join(";", OpenScrollChapters.defaultsEncoded()),
                "editor.historystages.config.openScrollDocument",
                "editor.historystages.config.openScrollDocument.desc",
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, null, List.of(), null));
        visualSubEntries.add(new ConfigEntry("open_scroll.overviewBlocks", ConfigType.SUBSCREEN,
                joinConfigList(Config.VISUAL.openScrollOverviewBlocks.get()), true,
                String.join(";", OpenScrollOverviewBlocks.defaultsEncoded()),
                "editor.historystages.config.openScrollDocument",
                "editor.historystages.config.openScrollDocument.desc",
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, null, List.of(), null));
        openScroll.add(new ConfigEntry("open_scroll.lockedDisplay", ConfigType.ENUM,
                Config.VISUAL.openScrollLockedDisplay.get(), true,
                OpenScrollVisibility.OBSCURED.serialize(),
                "editor.historystages.config.openScrollLockedDisplay",
                "editor.historystages.config.openScrollLockedDisplay.desc",
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, null,
                java.util.Arrays.stream(OpenScrollVisibility.values())
                        .map(OpenScrollVisibility::serialize).toList(),
                OpenScrollVisibility.class.getSimpleName()));
        openScroll.add(new ConfigEntry("open_scroll.entrySort", ConfigType.ENUM,
                Config.VISUAL.openScrollEntrySort.get(), true,
                OpenScrollSort.DEFINED.serialize(),
                "editor.historystages.config.openScrollEntrySort",
                "editor.historystages.config.openScrollEntrySort.desc",
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, null,
                java.util.Arrays.stream(OpenScrollSort.values())
                        .map(OpenScrollSort::serialize).toList(),
                OpenScrollSort.class.getSimpleName()));
        openScroll.add(new ConfigEntry("open_scroll.showSearch", "openScrollShowSearch", ConfigType.BOOLEAN,
                Config.VISUAL.openScrollShowSearch.get().toString(), true, "true"));
        openScroll.add(new ConfigEntry("open_scroll.showEntryIds", "openScrollShowEntryIds", ConfigType.BOOLEAN,
                Config.VISUAL.openScrollShowEntryIds.get().toString(), true, "true"));
        openScroll.add(new ConfigEntry("open_scroll.inkHeading", "openScrollInkHeading", ConfigType.COLOR,
                Config.VISUAL.openScrollInkHeading.get(), true, "#3F2D13"));
        openScroll.add(new ConfigEntry("open_scroll.inkBody", "openScrollInkBody", ConfigType.COLOR,
                Config.VISUAL.openScrollInkBody.get(), true, "#4A3416"));
        openScroll.add(new ConfigEntry("open_scroll.inkFaint", "openScrollInkFaint", ConfigType.COLOR,
                Config.VISUAL.openScrollInkFaint.get(), true, "#7A5A2C"));
        clientSections.add(openScroll);

        // --- GAMEPLAY CONFIG ---
        commonSections = new ArrayList<>();

        ConfigSection logging = new ConfigSection("editor.historystages.config.logging");
        logging.add(new ConfigEntry("logging.showDebugErrors", "showDebugErrors", ConfigType.BOOLEAN,
                Config.GAMEPLAY.showDebugErrors.get().toString(), false, "true"));
        logging.add(new ConfigEntry("logging.enableRuntimeLogging", "enableRuntimeLogging", ConfigType.BOOLEAN,
                Config.GAMEPLAY.enableRuntimeLogging.get().toString(), false, "false"));
        commonSections.add(logging);

        ConfigSection gameplay = new ConfigSection("editor.historystages.config.gameplay");
        gameplay.add(new ConfigEntry("gameplay.lockMobLoot", "lockMobLoot", ConfigType.BOOLEAN,
                Config.GAMEPLAY.lockMobLoot.get().toString(), false, "true"));
        gameplay.add(new ConfigEntry("gameplay.lockBlockBreaking", "lockBlockBreaking", ConfigType.BOOLEAN,
                Config.GAMEPLAY.lockBlockBreaking.get().toString(), false, "true"));
        gameplay.add(new ConfigEntry("gameplay.lockedBlockBreakSpeedMultiplier", "lockedBlockBreakSpeedMultiplier", ConfigType.DOUBLE,
                Config.GAMEPLAY.lockedBlockBreakSpeedMultiplier.get().toString(), false, "0.05",
                0.001, 1.0));
        gameplay.add(new ConfigEntry("gameplay.lockItemUsage", "lockItemUsage", ConfigType.BOOLEAN,
                Config.GAMEPLAY.lockItemUsage.get().toString(), false, "true"));
        gameplay.add(new ConfigEntry("gameplay.lockEntityItems", "lockEntityItems", ConfigType.BOOLEAN,
                Config.GAMEPLAY.lockEntityItems.get().toString(), false, "true"));
        gameplay.add(new ConfigEntry("gameplay.lockBlockInteraction", "lockBlockInteraction", ConfigType.BOOLEAN,
                Config.GAMEPLAY.lockBlockInteraction.get().toString(), false, "true"));
        commonSections.add(gameplay);

        ConfigSection individualCommon = new ConfigSection("editor.historystages.config.individual_stages");
        individualCommon.add(new ConfigEntry("individual_stages.lockItemPickup", "individualLockItemPickup", ConfigType.BOOLEAN,
                Config.GAMEPLAY.individualLockItemPickup.get().toString(), false, "true"));
        individualCommon.add(new ConfigEntry("individual_stages.lockLoot", "individualLockLoot", ConfigType.BOOLEAN,
                Config.GAMEPLAY.individualLockLoot.get().toString(), false, "true"));
        individualCommon.add(new ConfigEntry("individual_stages.dropOnRevoke", "individualDropOnRevoke", ConfigType.BOOLEAN,
                Config.GAMEPLAY.individualDropOnRevoke.get().toString(), false, "true"));
        individualCommon.add(new ConfigEntry("individual_stages.lockBlockBreaking", "individualLockBlockBreaking", ConfigType.BOOLEAN,
                Config.GAMEPLAY.individualLockBlockBreaking.get().toString(), false, "true"));
        individualCommon.add(new ConfigEntry("individual_stages.lockedBlockBreakSpeedMultiplier", "individualLockedBlockBreakSpeedMultiplier",
                ConfigType.DOUBLE,
                Config.GAMEPLAY.individualLockedBlockBreakSpeedMultiplier.get().toString(), false,
                "0.05", 0.001, 1.0));
        individualCommon.add(new ConfigEntry("individual_stages.lockItemUsage", "individualLockItemUsage", ConfigType.BOOLEAN,
                Config.GAMEPLAY.individualLockItemUsage.get().toString(), false, "true"));
        individualCommon.add(new ConfigEntry("individual_stages.lockBlockInteraction", "individualLockBlockInteraction", ConfigType.BOOLEAN,
                Config.GAMEPLAY.individualLockBlockInteraction.get().toString(), false, "true"));
        commonSections.add(individualCommon);

        ConfigSection research = new ConfigSection("editor.historystages.config.research");
        research.add(new ConfigEntry("research.researchTimeInSeconds", "researchTimeInSeconds", ConfigType.INTEGER,
                Config.GAMEPLAY.researchTimeInSeconds.get().toString(), false, "20", 1, 86400));
        // An ENUM rather than a toggle: three named outcomes, and a stage may override this one
        // with its own scroll_completion, so the row has to say which default it is overriding.
        research.add(new ConfigEntry("research.defaultScrollCompletion", ConfigType.ENUM,
                Config.GAMEPLAY.defaultScrollCompletion.get(), false,
                ScrollCompletion.CONSUME.serialize(),
                "editor.historystages.config.defaultScrollCompletion",
                "editor.historystages.config.defaultScrollCompletion.desc",
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, null,
                java.util.Arrays.stream(ScrollCompletion.values())
                        .map(ScrollCompletion::serialize).toList(),
                ScrollCompletion.class.getSimpleName()));
        research.add(new ConfigEntry("research.enableScrollResealing", "enableScrollResealing", ConfigType.BOOLEAN,
                Config.GAMEPLAY.enableScrollResealing.get().toString(), false, "true"));
        research.add(new ConfigEntry("research.researchBoosters", "researchBoosters", ConfigType.BOOSTER_LIST,
                encodeBoosterList(Config.GAMEPLAY.researchBoosters.get()), false, ""));
        commonSections.add(research);

        ConfigSection lootReplace = new ConfigSection("editor.historystages.config.loot_replacements");
        lootReplace.add(new ConfigEntry("loot_replacements.useReplacements", "useReplacements", ConfigType.BOOLEAN,
                Config.GAMEPLAY.useReplacements.get().toString(), false, "false"));
        lootReplace.add(new ConfigEntry("loot_replacements.replacementItems", "replacementItems", ConfigType.ITEM_LIST,
                String.join(ConfigSpecCodec.LIST_SEPARATOR, Config.GAMEPLAY.replacementItems.get()), false,
                "minecraft:cobblestone;minecraft:dirt"));
        lootReplace.add(new ConfigEntry("loot_replacements.replacementTags", "replacementTags", ConfigType.TAG_LIST,
                String.join(ConfigSpecCodec.LIST_SEPARATOR, Config.GAMEPLAY.replacementTags.get()), false, ""));
        commonSections.add(lootReplace);

        ConfigSection structureLock = new ConfigSection("editor.historystages.config.structure_lock");
        structureLock.add(new ConfigEntry("structure_lock.checkInterval", "structureCheckInterval", ConfigType.INTEGER,
                Config.GAMEPLAY.structureCheckInterval.get().toString(), false, "10", 1, 200));
        structureLock.add(new ConfigEntry("structure_lock.messageEnabled", "structureMessageEnabled", ConfigType.BOOLEAN,
                Config.GAMEPLAY.structureMessageEnabled.get().toString(), false, "true"));
        structureLock.add(new ConfigEntry("structure_lock.messageFormat", "structureLockMessageFormat", ConfigType.RICH_TEXT,
                Config.GAMEPLAY.structureLockMessageFormat.get(), false,
                "&cYou cannot enter &e{structure}&c yet!"));
        structureLock.add(new ConfigEntry("structure_lock.showInChat", "structureLockInChat", ConfigType.BOOLEAN,
                Config.GAMEPLAY.structureLockInChat.get().toString(), false, "false"));
        structureLock.add(new ConfigEntry("structure_lock.damageEnabled", "structureDamageEnabled", ConfigType.BOOLEAN,
                Config.GAMEPLAY.structureDamageEnabled.get().toString(), false, "false"));
        structureLock.add(new ConfigEntry("structure_lock.damageAmount", "structureDamageAmount", ConfigType.DOUBLE,
                Config.GAMEPLAY.structureDamageAmount.get().toString(), false, "1.0", 0.1, 100.0));
        structureLock.add(new ConfigEntry("structure_lock.damageInterval", "structureDamageInterval", ConfigType.INTEGER,
                Config.GAMEPLAY.structureDamageInterval.get().toString(), false, "20", 1, 600));
        structureLock.add(new ConfigEntry("structure_lock.blockRightClick", "structureBlockRightClick", ConfigType.BOOLEAN,
                Config.GAMEPLAY.structureBlockRightClick.get().toString(), false, "true"));
        structureLock.add(new ConfigEntry("structure_lock.blockLeftClick", "structureBlockLeftClick", ConfigType.BOOLEAN,
                Config.GAMEPLAY.structureBlockLeftClick.get().toString(), false, "true"));
        structureLock.add(new ConfigEntry("structure_lock.blockProjectiles", "structureBlockProjectiles", ConfigType.BOOLEAN,
                Config.GAMEPLAY.structureBlockProjectiles.get().toString(), false, "true"));
        commonSections.add(structureLock);

        ConfigSection biomeLock = new ConfigSection("editor.historystages.config.biome_lock");
        biomeLock.add(new ConfigEntry("biome_lock.checkInterval", "biomeCheckInterval", ConfigType.INTEGER,
                Config.GAMEPLAY.biomeCheckInterval.get().toString(), false, "10", 1, 200));
        biomeLock.add(new ConfigEntry("biome_lock.effectsEnabled", "biomeEffectsEnabled", ConfigType.BOOLEAN,
                Config.GAMEPLAY.biomeEffectsEnabled.get().toString(), false, "true"));
        biomeLock.add(new ConfigEntry("biome_lock.effects", "biomeEffects", ConfigType.EFFECT_LIST,
                encodeEffectList(Config.GAMEPLAY.biomeEffects.get()), false,
                "minecraft:blindness, 30, 0"));
        biomeLock.add(new ConfigEntry("biome_lock.clearEffectsOnLeave", "biomeClearEffectsOnLeave", ConfigType.BOOLEAN,
                Config.GAMEPLAY.biomeClearEffectsOnLeave.get().toString(), false, "false"));
        biomeLock.add(new ConfigEntry("biome_lock.messageEnabled", "biomeMessageEnabled", ConfigType.BOOLEAN,
                Config.GAMEPLAY.biomeMessageEnabled.get().toString(), false, "true"));
        biomeLock.add(new ConfigEntry("biome_lock.messageFormat", "biomeLockMessageFormat", ConfigType.RICH_TEXT,
                Config.GAMEPLAY.biomeLockMessageFormat.get(), false,
                "&cYou cannot survive in &e{biome}&c yet!"));
        biomeLock.add(new ConfigEntry("biome_lock.showInChat", "biomeLockInChat", ConfigType.BOOLEAN,
                Config.GAMEPLAY.biomeLockInChat.get().toString(), false, "false"));
        biomeLock.add(new ConfigEntry("biome_lock.damageEnabled", "biomeDamageEnabled", ConfigType.BOOLEAN,
                Config.GAMEPLAY.biomeDamageEnabled.get().toString(), false, "true"));
        biomeLock.add(new ConfigEntry("biome_lock.damageAmount", "biomeDamageAmount", ConfigType.DOUBLE,
                Config.GAMEPLAY.biomeDamageAmount.get().toString(), false, "1.0", 0.1, 100.0));
        biomeLock.add(new ConfigEntry("biome_lock.damageInterval", "biomeDamageInterval", ConfigType.INTEGER,
                Config.GAMEPLAY.biomeDamageInterval.get().toString(), false, "20", 1, 600));
        biomeLock.add(new ConfigEntry("biome_lock.blockRightClick", "biomeBlockRightClick", ConfigType.BOOLEAN,
                Config.GAMEPLAY.biomeBlockRightClick.get().toString(), false, "true"));
        biomeLock.add(new ConfigEntry("biome_lock.blockLeftClick", "biomeBlockLeftClick", ConfigType.BOOLEAN,
                Config.GAMEPLAY.biomeBlockLeftClick.get().toString(), false, "true"));
        biomeLock.add(new ConfigEntry("biome_lock.blockProjectiles", "biomeBlockProjectiles", ConfigType.BOOLEAN,
                Config.GAMEPLAY.biomeBlockProjectiles.get().toString(), false, "true"));
        commonSections.add(biomeLock);
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

    /**
     * Builds the Addons tab's rows straight from the registry, one {@link ConfigSection} per
     * {@link AddonConfigSection} in {@link AddonConfigSections#all()}'s stable, id-sorted order.
     *
     * <p>Every value below comes from the addon's own {@code read()} callback. {@link #saveConfig()}
     * routes each row back to its addon: CLIENT rows through {@link #addonFieldsByKey}, COMMON
     * rows through {@link AddonConfigSections#commonEntries()}.
     */
    private void buildAddonConfigEntries() {
        addonSections = new ArrayList<>();
        addonFieldsByKey = new HashMap<>();
        for (AddonConfigSection section : AddonConfigSections.all()) {
            ConfigSection configSection = new ConfigSection(section.titleLangKey());
            for (AddonConfigField field : section.fields()) {
                // The same wire key AddonConfigSections.commonEntries() uses for this field,
                // from the one place that builds it — so this row's key and a COMMON field's
                // published wire key can never drift apart.
                String entryKey = AddonConfigSections.wireKey(section, field);
                String descKey = field.descLangKey() != null ? field.descLangKey() : "";
                ConfigType type = mapAddonKind(field.kind());

                List<String> enumConstants = List.of();
                String enumType = null;
                Map<String, String> enumLabels = null;
                if (field.kind() == AddonConfigField.AddonConfigKind.CHOICE) {
                    enumConstants = field.optionValues();
                    // Only used as the enumLabel(String, String) fallback, which a fully
                    // populated enumLabels map below never falls through to; any addon-unique
                    // string is fine here.
                    enumType = entryKey;
                    enumLabels = new LinkedHashMap<>();
                    for (String option : enumConstants) {
                        String langKey = field.optionLangKey(option);
                        if (langKey != null) enumLabels.put(option, langKey);
                    }
                }

                configSection.add(ConfigEntry.addonRow(entryKey, type, field.read().get(),
                        field.defaultValue(), field.labelLangKey(), descKey,
                        field.min(), field.max(), enumConstants, enumType, enumLabels,
                        field.placeholders()));
                addonFieldsByKey.put(entryKey, field);
            }
            addonSections.add(configSection);
        }
    }

    /**
     * Exhaustive on purpose, with no {@code default}: a twelfth {@link AddonConfigField.AddonConfigKind}
     * must fail this compile rather than silently render as whatever kind happens to fall through.
     */
    private static ConfigType mapAddonKind(AddonConfigField.AddonConfigKind kind) {
        return switch (kind) {
            case BOOL -> ConfigType.BOOLEAN;
            case INTEGER -> ConfigType.INTEGER;
            case DECIMAL -> ConfigType.DOUBLE;
            case TEXT -> ConfigType.STRING;
            case RICH_TEXT -> ConfigType.RICH_TEXT;
            case COLOR -> ConfigType.COLOR;
            case ITEM -> ConfigType.ITEM;
            case ITEM_LIST -> ConfigType.ITEM_LIST;
            case TAG_LIST -> ConfigType.TAG_LIST;
            case TEXTURE -> ConfigType.TEXTURE;
            case CHOICE -> ConfigType.ENUM;
            case CUSTOM_SCREEN -> ConfigType.CUSTOM_SCREEN;
        };
    }

    /** One generated row. The path is what goes on the wire; the key is the editor-side identity. */
    private ConfigEntry toEntry(GraphKey gk, Map<String, String> current, String labelKey) {
        ConfigType type = switch (gk.kind()) {
            case BOOLEAN -> ConfigType.BOOLEAN;
            case INTEGER -> ConfigType.INTEGER;
            case DOUBLE -> ConfigType.DOUBLE;
            case STRING -> ConfigType.STRING;
            case RICH_TEXT -> ConfigType.RICH_TEXT;
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
        all.addAll(visualSubEntries);
        all.addAll(graphEntries());
        if (addonSections != null) {
            for (ConfigSection section : addonSections) all.addAll(section.entries);
        }
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
        // The packet's own map, spec values and addon values together — the addon rows are not in
        // GAMEPLAY_SPEC, so a plain spec walk here would refresh everything except them and leave an
        // open editor pushing stale addon values back on its next Save.
        Map<String, String> fresh = SyncConfigPacket.readServerConfig();
        for (ConfigSection section : commonSections) {
            for (ConfigEntry entry : section.entries) {
                mergeSynced(entry, fresh);
            }
        }
        // Addon rows too. Save sends them exactly like the mod's own common rows, so leaving them
        // out would let a stale one ride along and undo another admin's edit. A CLIENT addon row
        // simply is not in the map and mergeSynced skips it.
        if (addonSections != null) {
            for (ConfigSection section : addonSections) {
                for (ConfigEntry entry : section.entries) {
                    mergeSynced(entry, fresh);
                }
            }
        }
    }

    private static void mergeSynced(ConfigEntry entry, Map<String, String> fresh) {
        String synced = fresh.get(entry.key);
        if (synced == null) return;
        boolean untouched = entry.value.equals(entry.initialValue);
        entry.initialValue = synced;
        if (untouched) entry.value = synced;
    }

    /**
     * The visual counterpart of {@link #onCommonConfigSynced()}, and the same reasoning: Save sends
     * every client row, not just the edited ones, so an editor still holding its build-time
     * snapshot would push that snapshot back and undo whichever admin saved first.
     */
    public static void onVisualConfigSynced() {
        ConfigEditorScreen screen = active.get();
        if (screen != null && screen.clientSections != null) screen.refreshVisualValues();
    }

    /**
     * Merges the freshly synced visual values into the client rows. Same split as
     * {@link #refreshCommonValues()}: untouched rows follow the server, edited rows keep the edit
     * and only get a new baseline.
     *
     * <p>A plain spec walk is enough here, unlike the common side: addon rows are never in
     * {@code VISUAL_SPEC}, and a CLIENT-side addon row stays local by design — it is simply absent
     * from the map and {@link #mergeSynced} skips it.
     */
    private void refreshVisualValues() {
        Map<String, String> fresh = ConfigSpecCodec.collect(Config.VISUAL_SPEC);
        for (ConfigSection section : clientSections) {
            for (ConfigEntry entry : section.entries) {
                mergeSynced(entry, fresh);
            }
        }
        // The row-less values follow the same rule; skipping them would silently pin them to
        // whatever they were when the screen opened.
        for (ConfigEntry entry : visualSubEntries) {
            mergeSynced(entry, fresh);
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
            // Only reachable once tabKeys actually has a fourth entry — see init().
            case 3 -> addonSections;
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
        for (int i = 0; i < tabKeys.length; i++) {
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

            String label = Component.translatable(tabKeys[i]).getString();
            int textColor = active ? 0xFFFFFF : Fade.mix(0xFF999999, 0xFFDDDDDD, th);
            drawSmallText(guiGraphics, label, tabX[i] + TAB_PAD, tabY + 4, textColor);

            if (hovered) {
                // Every built-in tab has a .tooltip key; an addon's Addons tab does not need
                // one of its own, so this only shows a tooltip when the key actually resolves —
                // the alternative is the raw, untranslated key string on hover.
                String tooltipKey = tabKeys[i] + ".tooltip";
                if (I18n.exists(tooltipKey)) {
                    hoveredTabTooltip = Component.translatable(tooltipKey).getString();
                }
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
            if (openDropdown.isExpanded()) {
                // The row's own tooltip is noise under an open picker. What does help there is
                // what the option under the cursor means — for the enums that have such texts.
                String option = openDropdown.hoveredOption(mouseX, mouseY);
                currentDescription = option == null ? null
                        : ConfigRowList.enumDescription(openDropdownEnumType, option);
                currentHovered = currentDescription == null ? null : "__enum__" + option;
            }
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
            for (int i = 0; i < tabKeys.length; i++) {
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

    /** Enum type behind {@link #openDropdown}, kept for the per-option hover tooltips. */
    private String openDropdownEnumType;

    /** Drops the open enum picker. Safe to call when there is none. */
    private void closeDropdown() {
        openDropdown = null;
        openDropdownEnumType = null;
    }

    /**
     * @param contentLeft left edge of the row, needed to place the popup under the value control
     * @param rowY        the row's screen y at click time
     */
    private void openEnumDropdown(ConfigEntry entry, int contentLeft, int rowY) {
        if (entry.enumConstants.isEmpty()) return;
        EnumDropdown dropdown = new EnumDropdown(
                entry.enumConstants, entry.value, ConfigRowList.DROPDOWN_MIN_WIDTH,
                constant -> ConfigRowList.enumLabel(entry, constant),
                picked -> entry.value = picked);
        // Placed exactly over the collapsed button the row drew, so the popup grows out of the
        // control the user clicked rather than appearing beside it.
        dropdown.setPosition(configRows.controlX(entry, contentLeft),
                rowY + ConfigRowList.DROPDOWN_INSET_Y);
        dropdown.expand();
        openDropdown = dropdown;
        openDropdownEnumType = entry.enumType;
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
            case RICH_TEXT -> openRichTextEditor(entry);
            case CUSTOM_SCREEN -> openCustomFieldScreen(entry);
            case ITEM_LIST -> this.minecraft.setScreen(new ItemListEditorScreen(this, entry));
            case TAG_LIST -> this.minecraft.setScreen(new TagListEditorScreen(this, entry));
            case ITEM -> openItemPicker(entry);
            case BOOSTER_LIST -> this.minecraft.setScreen(new BoosterListEditorScreen(this, entry));
            case EFFECT_LIST -> this.minecraft.setScreen(new EffectListEditorScreen(this, entry));
            case ENUM -> openEnumDropdown(entry, contentLeft, rowY);
            case COLOR -> this.minecraft.setScreen(new ColorInputScreen(this, entry));
            case SUBSCREEN -> {
                if ("scroll_tooltip.lines".equals(entry.key)) {
                    this.minecraft.setScreen(new ScrollTooltipScreen(this, entry));
                } else if ("open_scroll.chapters".equals(entry.key)) {
                    this.minecraft.setScreen(new OpenScrollDocumentScreen(this));
                } else {
                    this.minecraft.setScreen(new GraphStyleScreen(this));
                }
            }
            case TEXTURE -> openTexturePicker(entry);
        }
    }

    /**
     * Placeholders each rich text field accepts, straight from the config comments that document
     * them. A field with no entry still gets the editor for its colour codes; it just shows no
     * placeholder chips.
     *
     * <p>The graph title is the odd one out: it takes no placeholder, but it does take a lang key
     * in place of literal text, and the mod's own key is the one an admin wants back after trying
     * something else. Offering it as a chip beats retyping it from the config comment.
     */
    private static final Map<String, List<String>> RICH_TEXT_PLACEHOLDERS = Map.of(
            "notifications.unlockMessageFormat", List.of("{stage}"),
            "individual_stages.unlockMessageFormat", List.of("{stage}", "{player}"),
            "structure_lock.messageFormat", List.of("{structure}", "{stage}"),
            "biome_lock.messageFormat", List.of("{biome}", "{stage}"),
            "graph.general.title", List.of(GraphConfig.GRAPH.title.getDefault()));

    /**
     * Opens the screen an addon registered for this field.
     *
     * <p>Does nothing when none was registered. The row is still shown and still syncs — the
     * declaration is common-side and does not depend on a client having supplied a screen — it
     * simply cannot be edited here, which is the honest outcome.
     */
    private void openCustomFieldScreen(ConfigEntry entry) {
        AddonConfigField field = addonFieldsByKey.get(entry.key);
        if (field == null) return;
        CustomFieldScreens.Factory factory = CustomFieldScreens.forField(field);
        if (factory == null) return;
        this.minecraft.setScreen(factory.create(this, entry.value, text -> entry.value = text == null ? "" : text));
    }

    private void openRichTextEditor(ConfigEntry entry) {
        List<String> placeholders = entry.placeholders != null
                ? entry.placeholders
                : RICH_TEXT_PLACEHOLDERS.getOrDefault(entry.key, List.of());
        this.minecraft.setScreen(new FormattedTextScreen(this,
                Component.translatable(entry.labelKey),
                entry.value,
                entry.defaultValue,
                placeholders,
                text -> entry.value = text));
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
        // Send the visual config to the server, which owns visual.toml and syncs it back to
        // everyone. This used to be a local write with no packet at all, which is why an admin
        // tuning these settings changed nothing for any other player. Same rule as the two blocks
        // below: an untouched tab is not worth a write, a sync to every client, and a toast.
        Map<String, String> clientValues = new HashMap<>();
        boolean clientChanged = false;
        for (ConfigSection section : clientSections) {
            for (ConfigEntry entry : section.entries) {
                clientValues.put(entry.key, entry.value);
                if (!entry.value.equals(entry.initialValue)) clientChanged = true;
            }
        }
        for (ConfigEntry entry : visualSubEntries) {
            clientValues.put(entry.key, entry.value);
            if (!entry.value.equals(entry.initialValue)) clientChanged = true;
        }
        if (clientChanged) {
            PacketHandler.sendToServer(new SaveConfigPacket(clientValues, true));
        }

        // Send common config to server — but only if something in it actually changed. Each
        // packet's handler answers with its own toast, so sending both unconditionally reported
        // two saves for one edit, and wrote and re-synced a file nobody had touched.
        Map<String, String> commonValues = new HashMap<>();
        boolean commonChanged = false;
        for (ConfigSection section : commonSections) {
            for (ConfigEntry entry : section.entries) {
                commonValues.put(entry.key, entry.value);
                if (!entry.value.equals(entry.initialValue)) commonChanged = true;
            }
        }
        // Addon rows split by their section's side. COMMON membership is decided by
        // AddonConfigSections.commonEntries() — the same list SaveConfigPacket.applyCommonConfig
        // reads to apply them server-side — so the wire key travels here without ever being
        // rebuilt by hand. Anything not in that list is a CLIENT row and is written straight
        // back into the addon's own field; there is nothing else it could be, since
        // AddonConfigSection only knows those two sides. That stays local even though the mod's
        // own visual rows now go to the server: an addon holds its CLIENT state in its own field,
        // and there is no packet on either side that could carry it.
        Map<String, AddonConfigSections.CommonEntry> addonCommonByWireKey = new HashMap<>();
        for (AddonConfigSections.CommonEntry commonEntry : AddonConfigSections.commonEntries()) {
            addonCommonByWireKey.put(commonEntry.wireKey(), commonEntry);
        }
        for (ConfigSection section : addonSections) {
            for (ConfigEntry entry : section.entries) {
                AddonConfigSections.CommonEntry commonEntry = addonCommonByWireKey.get(entry.key);
                boolean changed = !entry.value.equals(entry.initialValue);
                if (commonEntry != null) {
                    commonValues.put(commonEntry.wireKey(), entry.value);
                    if (changed) commonChanged = true;
                } else if (changed) {
                    // Only on a real change: an addon's write callback is its code, and calling
                    // it for every field on every save would hand it work it never asked for.
                    addonFieldsByKey.get(entry.key).write().accept(entry.value);
                }
            }
        }

        if (commonChanged) {
            PacketHandler.sendToServer(new SaveConfigPacket(commonValues, false));
        }

        // Send graph.toml to the server, keyed by toml path. The style rows come along here
        // too — GraphStyleScreen edits these very objects rather than keeping its own copies.
        // Same rule as above: untouched graph settings are not worth a write, a sync to every
        // client, and a second toast.
        Map<String, String> graphValues = new HashMap<>();
        boolean graphChanged = false;
        for (ConfigEntry entry : graphEntries()) {
            if (entry.path == null) continue;
            graphValues.put(entry.path, entry.value);
            if (!entry.value.equals(entry.initialValue)) graphChanged = true;
        }
        if (graphChanged) {
            PacketHandler.sendToServer(new SaveGraphConfigPacket(graphValues));
        }

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
        ENUM, COLOR, TEXTURE,
        /**
         * A string carrying {@code &} format codes, placeholders, or both. Opens the rich text
         * editor instead of the plain one. Deliberately not every STRING row: a field with
         * nothing to format is better served by a plain box than by a dialog full of buttons.
         */
        RICH_TEXT,
        /** A row that opens another screen instead of editing a value of its own. */
        SUBSCREEN,
        /**
         * An addon-declared value edited in a screen the addon registered for it.
         *
         * <p>Apart from {@link #SUBSCREEN}, which navigates somewhere and edits nothing of its
         * own: this row has a value, stored as a string, and the addon's screen returns a new one.
         */
        CUSTOM_SCREEN
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

    /** Encode the live scroll tooltip config list as the editor's internal string. */
    private static String joinConfigList(java.util.List<? extends String> entries) {
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
         * Constant to lang key, for an ENUM row whose options carry their own translation keys
         * rather than ones derived from {@link #enumType}. Null for every row that predates
         * addon config sections — those keep resolving through {@link #enumType} alone via
         * {@link ConfigRowList#enumLabel(String, String)}. Non-null entries still fall back to
         * that derivation for any constant the map has no key for.
         */
        public final Map<String, String> enumLabels;

        /**
         * Placeholder tokens for a RICH_TEXT row's dialog, in declaration order, or {@code null}
         * when the row has none of its own. Null (rather than empty) is what tells
         * {@link #openRichTextEditor(ConfigEntry)} to fall back to {@link #RICH_TEXT_PLACEHOLDERS}
         * instead of showing no chips at all.
         */
        public final List<String> placeholders;

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

        /**
         * A Client or Common row. {@code key} is the dotted toml path the value travels and is
         * addressed under; {@code langKey} is the flat name the translations hang off.
         *
         * <p>The two were the same string until the sync moved to dotted paths. They were split
         * rather than translated over because a dot inside a lang key reads badly and, more to the
         * point, renaming them would have broken every existing translation — including the ones
         * from human translators this project does not touch.
         */
        ConfigEntry(String key, String langKey, ConfigType type, String value, boolean isClient,
                    String defaultValue) {
            this(key, langKey, type, value, isClient, defaultValue,
                    Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
        }

        ConfigEntry(String key, String langKey, ConfigType type, String value, boolean isClient,
                    String defaultValue, double min, double max) {
            this(key, type, value, isClient, defaultValue,
                    "editor.historystages.config." + langKey,
                    "editor.historystages.config." + langKey + ".desc",
                    min, max, null, List.of(), null);
        }

        ConfigEntry(String key, ConfigType type, String value, boolean isClient,
                    String defaultValue, String labelKey, String descKey,
                    double min, double max, String path, List<String> enumConstants,
                    String enumType) {
            this(key, type, value, isClient, defaultValue, labelKey, descKey,
                    min, max, path, enumConstants, enumType, null, null);
        }

        ConfigEntry(String key, ConfigType type, String value, boolean isClient,
                    String defaultValue, String labelKey, String descKey,
                    double min, double max, String path, List<String> enumConstants,
                    String enumType, Map<String, String> enumLabels) {
            this(key, type, value, isClient, defaultValue, labelKey, descKey,
                    min, max, path, enumConstants, enumType, enumLabels, null);
        }

        ConfigEntry(String key, ConfigType type, String value, boolean isClient,
                    String defaultValue, String labelKey, String descKey,
                    double min, double max, String path, List<String> enumConstants,
                    String enumType, Map<String, String> enumLabels, List<String> placeholders) {
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
            this.enumLabels = enumLabels;
            this.placeholders = placeholders;
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

        /**
         * A row for an addon-registered {@link AddonConfigField}. Not client- or common-owned in
         * the sense {@link #isClient} otherwise distinguishes — that split is Task 3b's, once
         * these rows are wired into {@link #saveConfig()}.
         *
         * @param enumLabels constant to lang key for a CHOICE field's options, or null for
         *                    every non-CHOICE kind.
         * @param placeholders the addon's own placeholder chips for a RICH_TEXT field, in
         *                     declaration order (possibly empty); ignored for every other kind.
         */
        static ConfigEntry addonRow(String key, ConfigType type, String value, String defaultValue,
                                     String labelKey, String descKey, double min, double max,
                                     List<String> enumConstants, String enumType,
                                     Map<String, String> enumLabels, List<String> placeholders) {
            return new ConfigEntry(key, type, value, false, defaultValue, labelKey, descKey,
                    min, max, null, enumConstants, enumType, enumLabels, placeholders);
        }
    }

    /**
     * Package-private: {@link OpenScrollDocumentScreen} edits these entries in place rather than
     * keeping copies, so one Save here covers them and the unsaved-changes marker stays honest.
     *
     * <p>Searches both tabs rather than only the gameplay one. The open-scroll settings it is
     * asked for are visual now, and a lookup that still only walked {@code commonSections} would
     * throw on every one of them.
     */
    ConfigEntry findEntry(String key) {
        for (ConfigSection section : clientSections) {
            for (ConfigEntry entry : section.entries) {
                if (entry.key.equals(key)) return entry;
            }
        }
        for (ConfigSection section : commonSections) {
            for (ConfigEntry entry : section.entries) {
                if (entry.key.equals(key)) return entry;
            }
        }
        for (ConfigEntry entry : visualSubEntries) {
            if (entry.key.equals(key)) return entry;
        }
        throw new IllegalStateException("no config entry " + key);
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
                for (String s : entry.value.split(ConfigSpecCodec.LIST_SEPARATOR)) {
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
            entry.value = String.join(ConfigSpecCodec.LIST_SEPARATOR, items);

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
                for (String s : entry.value.split(ConfigSpecCodec.LIST_SEPARATOR)) {
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
            entry.value = String.join(ConfigSpecCodec.LIST_SEPARATOR, tags);

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
