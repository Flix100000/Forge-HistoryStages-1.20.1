package net.bananemdnsa.historystages.client.editor;
import net.bananemdnsa.historystages.client.editor.toast.EditorToastHandler;

import net.bananemdnsa.historystages.client.editor.widget.ConfirmDialog;
import net.bananemdnsa.historystages.client.editor.widget.EditorTooltip;
import net.bananemdnsa.historystages.client.editor.widget.ContextMenu;
import net.bananemdnsa.historystages.client.editor.widget.MarqueeText;
import net.bananemdnsa.historystages.client.editor.widget.popup.ModEntitySelectionPopup;
import net.bananemdnsa.historystages.client.editor.widget.popup.ModEntrySelectionPopup;
import net.bananemdnsa.historystages.client.editor.widget.popup.DimensionFilterPopup;
import net.bananemdnsa.historystages.client.editor.widget.popup.GenerationLimitPopup;
import net.bananemdnsa.historystages.client.editor.widget.popup.SpawnSourcesPopup;
import net.bananemdnsa.historystages.client.editor.widget.popup.TradeLevelsPopup;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableEntityList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableItemList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableDimensionList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableBiomeList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableFluidList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableModList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableProfessionList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableTradeList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableRecipeList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableStructureList;
import net.bananemdnsa.historystages.client.editor.widget.list.SearchableTagList;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.lock.EntityLocks;
import net.bananemdnsa.historystages.data.lock.GenerationPhase;
import net.bananemdnsa.historystages.data.lock.StructureGenerationRule;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.api.editor.widget.PickerOverlay;
import net.bananemdnsa.historystages.api.editor.CategoryEditor;
import net.bananemdnsa.historystages.api.editor.RecipeTypeMeta;
import net.bananemdnsa.historystages.client.editor.recipe.RecipeCardRenderer;
import net.bananemdnsa.historystages.client.editor.recipe.RecipeFluids;
import net.bananemdnsa.historystages.client.editor.recipe.RecipeShape;
import net.bananemdnsa.historystages.client.editor.widget.FluidIcon;
import net.bananemdnsa.historystages.client.ClientFluidRecipeIndex;
import net.bananemdnsa.historystages.data.lock.FluidRecipeIndex;
import net.bananemdnsa.historystages.client.ClientTradeGoods;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.serverbound.RequestTradeGoodsPacket;
import net.bananemdnsa.historystages.data.lock.FluidRecipeScanner;
import net.bananemdnsa.historystages.client.editor.recipe.RecipeTypeMetas;
import net.bananemdnsa.historystages.client.editor.tab.CategoryEditors;
import net.bananemdnsa.historystages.client.editor.tab.CompositeCategoryTab;
import net.bananemdnsa.historystages.api.editor.CategoryTab;
import net.bananemdnsa.historystages.client.editor.tab.EntityCategoryTab;
import net.bananemdnsa.historystages.api.editor.EditorTab;
import net.bananemdnsa.historystages.api.editor.EntryAction;
import net.bananemdnsa.historystages.api.editor.EntryActionContext;
import net.bananemdnsa.historystages.client.editor.tab.EntityTabsState;
import net.bananemdnsa.historystages.client.editor.tab.LockActionGroups;
import net.bananemdnsa.historystages.client.editor.tab.ModLinkedCategoryTab;
import net.bananemdnsa.historystages.client.editor.tab.RichEntryCategoryTab;
import net.bananemdnsa.historystages.client.editor.tab.StructureCategoryTab;
import net.bananemdnsa.historystages.client.editor.tab.TradeOfferCategoryTab;
import net.bananemdnsa.historystages.client.editor.tab.TradeLevelTab;
import net.bananemdnsa.historystages.client.editor.tab.TradeProfessionCategoryTab;
import net.bananemdnsa.historystages.api.editor.StringListCategoryTab;
import net.bananemdnsa.historystages.api.editor.TabInputContext;
import net.bananemdnsa.historystages.api.editor.TabRenderContext;
import net.bananemdnsa.historystages.api.editor.widget.EditorRowList;
import net.bananemdnsa.historystages.client.editor.widget.EntityPreviewRenderer;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;
import net.bananemdnsa.historystages.api.lock.LockCategory;
import net.bananemdnsa.historystages.api.settings.SettingsValues;
import net.bananemdnsa.historystages.api.settings.StageSettingsGroup;
import net.bananemdnsa.historystages.data.settings.StageSettingsGroups;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.engine.CategoryLockIndexes;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.client.cache.ClientStageCache;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.bananemdnsa.historystages.client.editor.anim.Anim;
import net.bananemdnsa.historystages.client.editor.anim.Ease;
import net.bananemdnsa.historystages.client.editor.anim.Timing;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.bananemdnsa.historystages.client.editor.widget.SegmentBar;
import org.jetbrains.annotations.Nullable;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.bananemdnsa.historystages.util.AllRecipesCache;
import net.minecraft.core.registries.BuiltInRegistries;
import org.joml.Quaternionf;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class StageDetailScreen extends Screen {
    private final Screen parent;
    private final String originalStageId;
    /**
     * The stage exactly as it was opened, or null for a new one. Kept so a save can start
     * from it instead of from a blank stage — see {@link #buildEntrySnapshot()}.
     */
    private final StageEntry originalEntry;
    private final boolean isNewStage;

    // Editable data
    private String editStageId;
    private String editDisplayName;
    private int editResearchTime;
    private int editMinPedestalTier;
    private net.bananemdnsa.historystages.research.TierMode editPedestalTierMode;
    private StageMode editMode;
    private AutoTrigger editAutoTrigger;
    private net.bananemdnsa.historystages.data.temporary.TemporaryConfig editTemporary;
    private String editIcon;
    /** Empty means "follow the config default", the same convention {@link #editIcon} uses. */
    private String editScrollCompletion = "";
    private net.bananemdnsa.historystages.data.display.HiddenDisplayConfig editHiddenDisplay;
    private boolean editLoseOnDeath;
    /** Addon settings for this stage, keyed by group id. Only ever holds installed groups. */
    private Map<String, SettingsValues> editAddonSettings = new LinkedHashMap<>();
    // Per-entry REPLACE text overrides (entry index → text); absent = follow stage default.
    /**
     * Tabs already driven by their lock category, keyed by tab index. Anything absent here is
     * still handled by the hardcoded branches below; migrating a tab means adding it here and
     * deleting its old field, picker and branches, one category at a time.
     */
    private final java.util.LinkedHashMap<Integer, CategoryTab> categoryTabs = new java.util.LinkedHashMap<>();
    /** Typed handle on the biomes tab; the mod-lock chain needs its mod-linked satellite. */
    private final ModLinkedCategoryTab biomeTab;
    /** Typed handle on the structures tab; it owns the per-entry generation rules. */
    private final StructureCategoryTab structureTab;
    /** Typed handle on the tags tab; its per-entry extras are read from several places. */
    private final RichEntryCategoryTab<net.bananemdnsa.historystages.data.lock.NamedLockEntry> tagTab;
    /** Typed handle on the mods tab; the exception picker filters by whatever it holds. */
    private final RichEntryCategoryTab<net.bananemdnsa.historystages.data.lock.NamedLockEntry> modTab;
    /** Typed handle on the mod-exceptions tab; its NBT extras are edited from the context menu. */
    private final RichEntryCategoryTab<net.bananemdnsa.historystages.data.ItemEntry> modExceptionTab;
    /**
     * Typed handle on the items tab; its extras drive badges, the NBT editor and overrides.
     * Not final: the picker factory reads it, and Java will not let a lambda in the constructor
     * touch a blank final even though the lambda only runs long after assignment.
     */
    private RichEntryCategoryTab<net.bananemdnsa.historystages.data.ItemEntry> itemTab;
    /**
     * Typed handle on the offer section of the trades tab; its picker writes into it and its
     * criteria are edited from the context menu. Not final, for the same reason {@link #itemTab}
     * is not.
     */
    private TradeOfferCategoryTab tradeOfferTab;
    /**
     * Typed handle on the profession section of the trades tab; its per-profession level
     * narrowing is edited from the context menu. Not final, for the same reason
     * {@link #itemTab} is not.
     */
    private TradeProfessionCategoryTab tradeProfessionTab;
    /**
     * Attack, spawn and interaction locks share one EntityLocks object, so they share one
     * state holder. The fields below are names for its lists rather than lists of their own.
     */
    private final EntityTabsState entityState = new EntityTabsState();
    private final List<String> editAttacklock = entityState.attacklock();
    private final List<String> editInteractionlock = entityState.interactionlock();
    private final Map<String, List<String>> editInteractionlockActions = entityState.interactionActions();
    private final Map<String, List<net.bananemdnsa.historystages.data.ItemEntry>> editInteractionlockItems =
            entityState.interactionItems();
    private final List<String> editSpawnlock = entityState.spawnlock();
    private final Map<String, List<String>> editSpawnlockSources = entityState.spawnSources();
    private final Map<String, List<String>> editSpawnlockDimensions = entityState.spawnDimensions();
    private final List<String> editModLinked = entityState.modLinked();
    private List<DependencyGroup> editDependencies;

    // UI state
    private double scrollOffset = 0;
    private int maxScroll = 0;
    private boolean hasChanges = false;
    private String saveError = "";

    // Tab state: 0-6, one per section
    private int activeTab = 0;

    // Widgets
    /**
     * The mods picker doubles as a lookup for a mod's display name, which the context menu
     * needs outside any picker interaction — so the tab's factory parks it here as well.
     */
    private SearchableModList modPickerForNames;
    private SearchableItemList iconSearch;
    private IconPickerButton iconPickerBtn;
    private ContextMenu contextMenu;
    private ModEntitySelectionPopup modEntityPopup;
    private ModEntrySelectionPopup modStructurePopup;
    private ModEntrySelectionPopup modBiomePopup;
    private DimensionFilterPopup dimFilterPopup;
    private GenerationLimitPopup generationLimitPopup;
    private SpawnSourcesPopup spawnSourcesPopup;
    private TradeLevelsPopup tradeLevelsPopup;
    private net.bananemdnsa.historystages.client.editor.widget.popup.InteractionActionsPopup interactionActionsPopup;
    private net.bananemdnsa.historystages.client.editor.widget.popup.InteractionItemsPopup interactionItemsPopup;
    private SearchableItemList filterItemSearch;
    private SearchableTagList filterTagSearch;
    /** Entity whose interaction item filter is being edited; survives the NBT sub-screen round trip. */
    private String interactionItemsTarget = null;
    private String pendingModId = null;
    private String pendingModDisplayName = null;
    // When non-null, the entity/structure popups are in "edit mode" for this mod:
    // a Confirm replaces the existing mod-linked entries instead of just appending.
    private String editingModId = null;

    // Tooltip hover tracking
    private String hoveredTooltipKey = null;
    private long tooltipHoverStart = 0;
    private static final long TOOLTIP_DELAY_MS = Timing.TOOLTIP_DELAY_MS;

    // Scrollbar drag state
    private boolean scrollBarDragging = false;

    // Animation state
    private final Map<Integer, EditorRowList> rowLists = new HashMap<>();
    private final Anim tabIndicatorXAnim = new Anim();
    private final Anim tabIndicatorWAnim = new Anim();
    private boolean tabIndicatorInit = false;
    private long tabSwitchTime = 0;
    private final Anim smoothScrollOffset = new Anim();
    /** The overlay a declared entry action put up, or null. Cleared once it hides itself. */
    private PickerOverlay actionOverlay;
    private String currentTooltipKey = null;
    private String currentTooltipText = null;

    // Category search box (inline header, next to icon button)
    private EditBox categorySearchBox;
    private String categorySearchFilter = "";
    private boolean categoryDropdownVisible = false;
    private List<String> categoryDropdownSuggestions = new ArrayList<>();
    private int categorySearchBoxX;
    private int categorySearchBoxW;
    private final Anim categorySearchHover = new Anim();
    private static final int DROPDOWN_ENTRY_H = 13;
    private static final int MAX_DROPDOWN_ENTRIES = 8;   // visible rows
    private static final int MAX_DROPDOWN_COLLECT = 50;  // max suggestions collected
    private int categoryDropdownScrollOffset = 0;
    /**
     * Long entry ids are the rule in the suggestion list, not the exception — the dropdown is
     * only as wide as the search box. Same marquee the searchable pickers use, so a hovered
     * suggestion reads the same here as it does there.
     */
    private final MarqueeText categoryDropdownMarquee = new MarqueeText();

    // Marquee state for card entries
    private int hoveredCardIndex = -1;
    private long cardHoverStartTime = 0;
    private static final long CARD_MARQUEE_DELAY_MS = Timing.MARQUEE_DELAY_MS;
    private static final float CARD_MARQUEE_SPEED = Timing.MARQUEE_SPEED;

    // Lock Actions popup state
    private boolean lockActionsPopupVisible = false;
    private int lockActionsPopupTab = -1;   // tab that opened it (0=items, 1=tags, 2=mods)
    private int lockActionsPopupIdx = -1;   // entry index
    private List<String> lockActionsPopupCurrent = new ArrayList<>(); // working copy
    private int cachedLockPopupX, cachedLockPopupY, cachedLockPopupW, cachedLockPopupH;

    // Text-override popup state (per-item REPLACE name/tooltip overrides)
    private boolean overridePopupVisible = false;
    private int overridePopupTab = 0;
    private int overridePopupIdx = -1;
    private boolean overrideShowName = false;
    private boolean overrideShowTooltip = false;
    private String overrideNameDefault = "";
    private String overrideTooltipDefault = "";
    private EditBox overrideNameField;
    private EditBox overrideTooltipField;
    private net.bananemdnsa.historystages.client.editor.widget.StyledButton overrideResetBtn;
    private net.bananemdnsa.historystages.client.editor.widget.StyledButton overrideDoneBtn;
    private int cachedOverrideX, cachedOverrideY, cachedOverrideW, cachedOverrideH;

    // All recognized lock actions in display order
    // No constant vocabulary here any more: the list belongs to the category, because a fluid
    // can be neither worn nor mined and an addon may declare actions of its own.
    // See lockActionsForTab.

    // Spawn sources popup state (per-entity source filter for spawnlock entries)
    private static final String[] SPAWN_SOURCE_KEYS = {"natural", "spawner", "structure", "breeding", "summon", "spawn_egg"};

    // Recipe detail popup state
    private boolean recipePopupVisible = false;
    private String recipePopupId = null;
    // Popup layout cache for click detection
    private int cachedPopupX, cachedPopupY, cachedPopupW, cachedPopupH;
    // Popup recipe ID marquee state
    private long popupMarqueeStartTime = 0;
    private String popupMarqueeLastId = null;
    private boolean popupIdHovered = false;

    // Entity preview cache and hover state

    // Recipe info cache: recipeId -> [workstation, result]
    private final Map<String, ItemStack[]> recipeInfoCache = new HashMap<>();
    private boolean recipeInfoBuilt = false;

    // Short tab label keys
    /**
     * The tab strip is built from the registered tabs rather than a fixed array, so a category
     * the editor has never heard of takes its place in the strip like any other.
     */
    private int tabCount() {
        return categoryTabs.size();
    }

    private String tabKey(int index) {
        CategoryTab tab = categoryTabs.get(index);
        return tab != null ? tab.tabLangKey() : "";
    }

    private String tabTooltipKey(int index) {
        CategoryTab tab = categoryTabs.get(index);
        return tab != null ? tab.tooltipLangKey() : "";
    }

    // Tab layout (computed in init)
    private int[] tabX;
    private int[] tabW;
    private int tabY;
    private int tabScrollOffset = 0;
    private int maxTabScroll = 0;
    private static final int TAB_ARROW_WIDTH = 12;

    // Layout constants
    private static final int HEADER_HEIGHT = 62;
    private static final int CARD_HEIGHT = 22;
    private static final int CARD_GAP = 3;
    private static final int TAB_HEIGHT = 16;
    /** The section bar plus the air above and below it, when the open tab has one. */
    private static final int SECTION_BAR_HEIGHT = SegmentBar.height() + 7;
    private static final int TAB_PAD = 8;
    private static final float SMALL_SCALE = 0.85f;
    private static final int FIELD_HEIGHT = 18;

    private final boolean isIndividual;
    /**
     * Folder a brand-new stage is written to, {@code ""} for the tree root. Ignored by the
     * server for a stage that already exists — that one keeps the folder it lives in.
     */
    private final String targetFolder;

    // Tabs that are disabled for individual stages (Spawnlock=7)
    private boolean isTabDisabled(int tab) {
        CategoryTab categoryTab = categoryTabs.get(tab);
        return categoryTab != null && isIndividual && !categoryTab.availableForIndividualStages();
    }

    public StageDetailScreen(Screen parent, String stageId, StageEntry entry, boolean isIndividual) {
        this(parent, stageId, entry, isIndividual, "");
    }

    public StageDetailScreen(Screen parent, String stageId, StageEntry entry, boolean isIndividual,
                             String targetFolder) {
        this(parent, stageId, entry, isIndividual, targetFolder, "");
    }

    /**
     * @param initialDisplayName display name the creation dialog already collected, {@code ""}
     *                           when none was given. Only consulted for a stage that has no
     *                           entry yet — an existing entry brings its own name.
     */
    public StageDetailScreen(Screen parent, String stageId, StageEntry entry, boolean isIndividual,
                             String targetFolder, String initialDisplayName) {
        super(Component.translatable("editor.historystages.detail_title"));
        this.parent = parent;
        this.originalStageId = stageId;
        this.originalEntry = entry;
        this.isIndividual = isIndividual;
        this.targetFolder = targetFolder == null ? "" : targetFolder;
        this.isNewStage = (stageId == null
                || (!StageManager.getStages().containsKey(stageId)
                    && !StageManager.getIndividualStages().containsKey(stageId)));

        StageEntry e = entry != null ? entry : new StageEntry();
        this.editStageId = stageId != null ? stageId : "";
        this.editDisplayName = (e.getDisplayName().equals("Unknown Stage") && entry == null)
                ? (initialDisplayName == null ? "" : initialDisplayName)
                : e.getDisplayName();
        this.editResearchTime = (entry == null && e.getResearchTime() == 0) ? Config.GAMEPLAY.researchTimeInSeconds.get() : e.getResearchTime();
        this.editMinPedestalTier = e.getMinPedestalTier();
        this.editPedestalTierMode = e.getPedestalTierMode();
        this.editMode = e.getMode();
        this.editAutoTrigger = e.getAutoTrigger() != null ? e.getAutoTrigger().copy() : null;
        this.editTemporary = e.getTemporary() != null ? e.getTemporary().copy() : null;
        this.editHiddenDisplay = e.getHiddenDisplay().copy();
        this.editLoseOnDeath = e.isLoseOnDeath();
        // Safe cast: the built-in items category stores ItemEntry.
        @SuppressWarnings("unchecked")
        LockCategory<net.bananemdnsa.historystages.data.ItemEntry> itemCategory =
                (LockCategory<net.bananemdnsa.historystages.data.ItemEntry>)
                        LockCategories.byId("historystages:items");
        RichEntryCategoryTab<net.bananemdnsa.historystages.data.ItemEntry> itemTabLocal =
                new RichEntryCategoryTab<>(itemCategory,
                        (onSelect, alreadyAdded) -> {
                            SearchableItemList list = new SearchableItemList(onSelect::accept, alreadyAdded::get);
                            list.setMultiSelect(true);
                            // Ctrl-add dumps the held stack's components as match criteria, and
                            // always appends so the same item can be locked once per NBT variant.
                            list.setOnSelectWithNbt((itemId, nbt) -> {
                                itemTab.addEntryWithNbt(itemId, nbt);
                                hasChanges = true;
                                updateMaxScroll();
                            });
                            return list;
                        },
                        () -> { hasChanges = true; updateMaxScroll(); },
                        ITEM_ENTRY_ADAPTER);
        itemTabLocal.load(e);
        this.itemTab = itemTabLocal;
        this.categoryTabs.put(0, itemTabLocal);
        // Safe cast: the built-in tags category stores NamedLockEntry.
        @SuppressWarnings("unchecked")
        LockCategory<net.bananemdnsa.historystages.data.lock.NamedLockEntry> tagCategory =
                (LockCategory<net.bananemdnsa.historystages.data.lock.NamedLockEntry>)
                        LockCategories.byId("historystages:tags");
        RichEntryCategoryTab<net.bananemdnsa.historystages.data.lock.NamedLockEntry> tagTabLocal =
                new RichEntryCategoryTab<>(tagCategory,
                        (onSelect, alreadyAdded) -> {
                            SearchableTagList list = new SearchableTagList(onSelect, alreadyAdded);
                            list.setMultiSelect(true);
                            return list;
                        },
                        () -> { hasChanges = true; updateMaxScroll(); },
                        NAMED_LOCK_ENTRY_ADAPTER);
        tagTabLocal.load(e);
        this.tagTab = tagTabLocal;
        this.categoryTabs.put(2, tagTabLocal);
        // Safe cast: the built-in mod-exceptions category stores ItemEntry.
        @SuppressWarnings("unchecked")
        LockCategory<net.bananemdnsa.historystages.data.ItemEntry> exceptionCategory =
                (LockCategory<net.bananemdnsa.historystages.data.ItemEntry>)
                        LockCategories.byId("historystages:mod_exceptions");
        RichEntryCategoryTab<net.bananemdnsa.historystages.data.ItemEntry> exceptionTabLocal =
                new RichEntryCategoryTab<>(exceptionCategory,
                        (onSelect, alreadyAdded) -> createModExceptionSearch(onSelect, alreadyAdded),
                        () -> { hasChanges = true; updateMaxScroll(); },
                        ITEM_ENTRY_ADAPTER);
        // Its picker is filtered to the mods that are locked right now, and that changes while
        // the editor is open — so it cannot be cached between opens.
        exceptionTabLocal.setRebuildPickerOnOpen(true);
        exceptionTabLocal.load(e);
        this.modExceptionTab = exceptionTabLocal;
        this.categoryTabs.put(4, exceptionTabLocal);
        // Safe cast: the built-in recipes category stores bare ids.
        @SuppressWarnings("unchecked")
        LockCategory<String> recipeCategory =
                (LockCategory<String>) LockCategories.byId("historystages:recipes");
        // Both scopes: the recipes tab is offered on individual stages too, and its picker then
        // filters to the recipe types a per-player gate can actually reach.
        CategoryTab recipeTab = new StringListCategoryTab(recipeCategory,
                (onSelect, alreadyAdded) -> new SearchableRecipeList(onSelect, alreadyAdded, isIndividual),
                () -> { hasChanges = true; updateMaxScroll(); });
        recipeTab.load(e);
        this.categoryTabs.put(5, recipeTab);
        // Safe cast: the built-in dimensions category stores bare ids.
        @SuppressWarnings("unchecked")
        LockCategory<String> dimensionCategory =
                (LockCategory<String>) LockCategories.byId("historystages:dimensions");
        CategoryTab dimensionTab = new StringListCategoryTab(dimensionCategory,
                (onSelect, alreadyAdded) -> {
                    SearchableDimensionList list = new SearchableDimensionList(onSelect, alreadyAdded);
                    list.setMultiSelect(true);
                    return list;
                },
                () -> { hasChanges = true; updateMaxScroll(); });
        dimensionTab.load(e);
        this.categoryTabs.put(6, dimensionTab);
        // Safe cast: the built-in structures category stores bare ids.
        @SuppressWarnings("unchecked")
        LockCategory<String> structureCategory =
                (LockCategory<String>) LockCategories.byId("historystages:structures");
        StructureCategoryTab structureTabLocal = new StructureCategoryTab(structureCategory,
                (onSelect, alreadyAdded) -> {
                    SearchableStructureList list = new SearchableStructureList(onSelect, alreadyAdded);
                    list.setMultiSelect(true);
                    return list;
                },
                () -> { hasChanges = true; updateMaxScroll(); });
        structureTabLocal.load(e);
        this.structureTab = structureTabLocal;
        this.categoryTabs.put(11, structureTabLocal);
        // Safe cast: the built-in biomes category stores bare ids.
        @SuppressWarnings("unchecked")
        LockCategory<String> biomeCategory =
                (LockCategory<String>) LockCategories.byId("historystages:biomes");
        ModLinkedCategoryTab biomeTabLocal = new ModLinkedCategoryTab(biomeCategory,
                (onSelect, alreadyAdded) -> {
                    SearchableBiomeList list = new SearchableBiomeList(onSelect, alreadyAdded, true);
                    list.setMultiSelect(true);
                    return list;
                },
                () -> { hasChanges = true; updateMaxScroll(); },
                StageEntry::getBiomeModLinked, StageEntry::setBiomeModLinked);
        biomeTabLocal.load(e);
        this.biomeTab = biomeTabLocal;
        this.categoryTabs.put(12, biomeTabLocal);
        // Three categories, one tab, sitting where the decision belongs: after the entity locks
        // that gate the merchant itself and before world generation. Structures and biomes moved
        // up a place for it — the index is a position in the strip and nothing reads it as an
        // identity, which is what makes inserting in the middle a rename rather than a rewrite.
        // Safe cast: the built-in trades category stores TradeOfferEntry.
        @SuppressWarnings("unchecked")
        LockCategory<net.bananemdnsa.historystages.data.TradeOfferEntry> tradeOfferCategory =
                (LockCategory<net.bananemdnsa.historystages.data.TradeOfferEntry>)
                        LockCategories.byId("historystages:trades");
        TradeOfferCategoryTab tradeOfferTabLocal = new TradeOfferCategoryTab(tradeOfferCategory,
                (onSelect, alreadyAdded) -> {
                    // Worked out here rather than when the screen opened, because it costs a
                    // moment and almost nobody reaches this tab.
                    ClientTradeGoods.scanLocally(this.minecraft == null ? null : this.minecraft.level);
                    // A picker of trades rather than of items: the lock is written as an item plus
                    // a side, and which half of a trade you point at is what answers both at once.
                    // Its second tab is the full registry, for anything the scan did not find.
                    SearchableTradeList list = new SearchableTradeList(picked -> {
                        net.bananemdnsa.historystages.data.TradeOfferEntry offer =
                                net.bananemdnsa.historystages.data.TradeOfferEntry.decode(picked);
                        if (offer == null) return;
                        tradeOfferTab.addOffer(offer);
                        hasChanges = true;
                        updateMaxScroll();
                    }, () -> tradeOfferTab.addedIdentities());
                    list.setMultiSelect(true);
                    return list;
                },
                () -> { hasChanges = true; updateMaxScroll(); });
        // The server's answer arrives after this screen is already up, so the picker cannot be
        // built once and kept: built at init it would be missing the modded trades for the whole
        // session.
        tradeOfferTabLocal.setRebuildPickerOnOpen(true);
        this.tradeOfferTab = tradeOfferTabLocal;
        // Safe cast: the built-in trade-professions category stores TradeProfessionEntry.
        @SuppressWarnings("unchecked")
        LockCategory<net.bananemdnsa.historystages.data.TradeProfessionEntry> tradeProfessionCategory =
                (LockCategory<net.bananemdnsa.historystages.data.TradeProfessionEntry>)
                        LockCategories.byId("historystages:trade_professions");
        TradeProfessionCategoryTab tradeProfessionTabLocal =
                new TradeProfessionCategoryTab(tradeProfessionCategory,
                        (onSelect, alreadyAdded) -> {
                            SearchableProfessionList list =
                                    new SearchableProfessionList(onSelect, alreadyAdded);
                            list.setMultiSelect(true);
                            return list;
                        },
                        () -> { hasChanges = true; updateMaxScroll(); });
        this.tradeProfessionTab = tradeProfessionTabLocal;
        // Safe cast: the built-in trade-levels category stores bare ids.
        @SuppressWarnings("unchecked")
        LockCategory<String> tradeLevelCategory =
                (LockCategory<String>) LockCategories.byId("historystages:trade_levels");
        CategoryTab tradeLevelTab = new TradeLevelTab(tradeLevelCategory,
                () -> { hasChanges = true; updateMaxScroll(); });
        CompositeCategoryTab tradesTabLocal = new CompositeCategoryTab(
                tradeOfferCategory.id(), tradeOfferCategory.tabLangKey(),
                tradeOfferCategory.tooltipLangKey(),
                List.of(new CompositeCategoryTab.Section(tradeOfferTabLocal,
                                "editor.historystages.trades.section.offers"),
                        new CompositeCategoryTab.Section(tradeProfessionTabLocal,
                                "editor.historystages.trades.section.professions"),
                        new CompositeCategoryTab.Section(tradeLevelTab,
                                "editor.historystages.trades.section.levels")));
        tradesTabLocal.load(e);
        this.categoryTabs.put(10, tradesTabLocal);
        // Fluids sit at index 1, beside items, because that is where someone looks for them.
        // The tab index is a position in the strip and nothing treats it as an identity — every
        // behavioural question goes through isTab against the category id — so putting a tab in
        // the middle renumbers its neighbours and costs nothing else. Addon tabs keep landing
        // after all of these, because their first index is categoryTabs.size().
        @SuppressWarnings("unchecked")
        LockCategory<net.bananemdnsa.historystages.data.FluidEntry> fluidCategory =
                (LockCategory<net.bananemdnsa.historystages.data.FluidEntry>)
                        LockCategories.byId("historystages:fluids");
        RichEntryCategoryTab<net.bananemdnsa.historystages.data.FluidEntry> fluidTabLocal =
                new RichEntryCategoryTab<>(fluidCategory,
                        (onSelect, alreadyAdded) -> {
                            SearchableFluidList list =
                                    new SearchableFluidList(onSelect::accept, alreadyAdded::get);
                            list.setMultiSelect(true);
                            return list;
                        },
                        () -> { hasChanges = true; updateMaxScroll(); },
                        FLUID_ENTRY_ADAPTER);
        fluidTabLocal.load(e);
        this.categoryTabs.put(1, fluidTabLocal);
        this.editIcon = e.getIcon();
        this.editScrollCompletion = e.getScrollCompletion();
        entityState.load(e);
        @SuppressWarnings("unchecked")
        LockCategory<String> attackCategory =
                (LockCategory<String>) LockCategories.byId("historystages:attacklock");
        this.categoryTabs.put(7, new EntityCategoryTab(attackCategory,
                (onSelect, alreadyAdded) -> createEntityPicker(onSelect, alreadyAdded),
                () -> { hasChanges = true; updateMaxScroll(); },
                entityState, entityState.attacklock()));
        this.categoryTabs.put(8, new EntityCategoryTab(
                LockCategories.byId("historystages:spawnlock"),
                (onSelect, alreadyAdded) -> createEntityPicker(onSelect, alreadyAdded),
                () -> { hasChanges = true; updateMaxScroll(); },
                entityState, entityState.spawnlock()));
        this.categoryTabs.put(9, new EntityCategoryTab(
                LockCategories.byId("historystages:interactionlock"),
                (onSelect, alreadyAdded) -> createEntityPicker(onSelect, alreadyAdded),
                () -> { hasChanges = true; updateMaxScroll(); },
                entityState, entityState.interactionlock()));
        // Built after the entity lists, because adding a mod starts the mod-lock chain and
        // that chain reads them.
        // Safe cast: the built-in mods category stores NamedLockEntry.
        @SuppressWarnings("unchecked")
        LockCategory<net.bananemdnsa.historystages.data.lock.NamedLockEntry> modCategory =
                (LockCategory<net.bananemdnsa.historystages.data.lock.NamedLockEntry>)
                        LockCategories.byId("historystages:mods");
        RichEntryCategoryTab<net.bananemdnsa.historystages.data.lock.NamedLockEntry> modTabLocal =
                new RichEntryCategoryTab<>(modCategory,
                        (onSelect, alreadyAdded) -> {
                            // Adding a mod also starts the mod-lock chain, which is screen
                            // orchestration rather than tab bookkeeping — so it wraps the
                            // tab's own add rather than replacing it.
                            SearchableModList list = new SearchableModList(modId -> {
                                onSelect.accept(modId);
                                pendingModId = modId;
                                pendingModDisplayName = modPickerForNames.getDisplayName(modId);
                                editingModId = null; // normal add — not edit mode
                                if (!modEntityPopup.showForMod(modId, pendingModDisplayName,
                                        this.width / 2, this.height / 2, editSpawnlock,
                                        editAttacklock, editInteractionlock)) {
                                    showModStructurePopup();
                                }
                            }, alreadyAdded);
                            modPickerForNames = list;
                            return list;
                        },
                        () -> { hasChanges = true; updateMaxScroll(); },
                        NAMED_LOCK_ENTRY_ADAPTER);
        modTabLocal.load(e);
        this.modTab = modTabLocal;
        this.categoryTabs.put(3, modTabLocal);

        // Addon categories take their place in the strip after the built-ins, provided they
        // registered an editor. One without an editor still gates and still stores — it simply
        // cannot be edited in game, which is a coherent state rather than an error.
        int nextTabIndex = this.categoryTabs.size();
        for (String addonCategoryId : LockCategories.addonIds()) {
            CategoryEditor editor = CategoryEditors.byCategory(addonCategoryId);
            if (editor == null) continue;
            CategoryTab addonTab = editor.createTab(() -> { hasChanges = true; updateMaxScroll(); });
            addonTab.load(e);
            this.categoryTabs.put(nextTabIndex++, addonTab);
        }
        StageScope loadScope = isIndividual ? StageScope.INDIVIDUAL : StageScope.GLOBAL;
        for (StageSettingsGroup group : StageSettingsGroups.all()) {
            this.editAddonSettings.put(group.id(), group.load(e, loadScope));
        }
        this.editDependencies = e.getDependencies().stream()
                .map(DependencyGroup::copy)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    protected void init() {
        // The recipe picker cannot reach a fluid-producing recipe without this, and the recipe
        // count beside each fluid row reads zero for everything until an index exists — which is
        // precisely when that number would inform the decision.
        FluidRecipeIndex.requestForEditor();
        // Which items merchants deal in has to be worked out on the server — a merchant refuses
        // to produce its offers on a client. Asked here so the answer is usually already back by
        // the time somebody reaches the trades tab and opens its picker.
        if (ClientTradeGoods.isEmpty()) {
            PacketHandler.sendToServer(new RequestTradeGoodsPacket());
        }
        ClientFluidRecipeIndex.refresh();

        tabY = 44;
        tabX = new int[tabCount()];
        tabW = new int[tabCount()];
        int tabMargin = 20;
        int totalAvail = this.width - tabMargin * 2;
        int gap = 2;

        // Compute natural width for each tab based on its text content (label + count)
        int[] naturalW = new int[tabCount()];
        int totalNaturalW = 0;
        for (int i = 0; i < tabCount(); i++) {
            String label = Component.translatable(tabKey(i)).getString();
            int count = getListForSection(i).size();
            String tabText = label + " (" + count + ")";
            naturalW[i] = (int)(this.font.width(tabText) * SMALL_SCALE) + TAB_PAD * 2;
            totalNaturalW += naturalW[i];
        }
        int totalGaps = (tabCount() - 1) * gap;

        if (totalNaturalW + totalGaps <= totalAvail) {
            // All tabs fit without scrolling - use natural widths
            int x = tabMargin;
            for (int i = 0; i < tabCount(); i++) {
                tabX[i] = x;
                tabW[i] = naturalW[i];
                x += tabW[i] + gap;
            }
            maxTabScroll = 0;
        } else {
            // Tabs need scrolling - use natural widths, offset by arrow width
            int scrollAreaAvail = totalAvail - TAB_ARROW_WIDTH * 2;
            int x = tabMargin + TAB_ARROW_WIDTH;
            for (int i = 0; i < tabCount(); i++) {
                tabX[i] = x;
                tabW[i] = naturalW[i];
                x += naturalW[i] + gap;
            }
            int totalTabsWidth = x - gap - (tabMargin + TAB_ARROW_WIDTH);
            maxTabScroll = Math.max(0, totalTabsWidth - scrollAreaAvail);
            tabScrollOffset = Math.min(tabScrollOffset, maxTabScroll);
        }

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.back"),
                btn -> tryClose(), 10, this.height - 25, 50, 18));

        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.save"),
                btn -> saveStage(), this.width - 60, this.height - 25, 50, 18));

        int addBtnW = 120;
        this.addButton = this.addRenderableWidget(StyledButton.of(
                Component.literal("+ ").append(Component.translatable("editor.historystages.add")),
                btn -> openAddDialog(),
                (this.width - addBtnW) / 2, this.height - 25, addBtnW, 18));
        updateAddButton();

        // Top-left button row (y=22): Settings | Dependencies | Icon
        String settingsLabel = Component.translatable("editor.historystages.stage_settings.button").getString();
        int settingsBtnW = this.font.width(settingsLabel) + 12;
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.stage_settings.button"),
                btn -> openStageSettings(), 10, 22, settingsBtnW, FIELD_HEIGHT));

        String depLabel = Component.translatable("editor.historystages.dep.title").getString();
        int depBtnW = this.font.width(depLabel) + 12;
        int depBtnX = 10 + settingsBtnW + 6;
        this.addRenderableWidget(StyledButton.of(
                Component.translatable("editor.historystages.dep.title"),
                btn -> openDependencyEditor(), depBtnX, 22, depBtnW, FIELD_HEIGHT));

        int iconBtnX = depBtnX + depBtnW + 6;
        iconPickerBtn = new IconPickerButton(iconBtnX, 22, FIELD_HEIGHT, FIELD_HEIGHT, () -> {
            iconSearch = createIconSearch();
            iconSearch.show(this.width / 2, this.height / 2, this.width - 60);
        });
        this.addRenderableWidget(iconPickerBtn);
        iconSearch = createIconSearch();

        // Category search box — capped width, inline right of icon button
        categorySearchFilter = "";
        categoryDropdownVisible = false;
        categoryDropdownSuggestions = new ArrayList<>();
        int cSearchX = iconBtnX + FIELD_HEIGHT + 8;
        categorySearchBoxW = Math.max(40, Math.min(140, this.width - cSearchX - 80));
        categorySearchBoxX = cSearchX;
        categorySearchBox = new CategorySearchEditBox(cSearchX + 4, 22, categorySearchBoxW - 8, FIELD_HEIGHT);

        categorySearchBox.setValue("");
        categorySearchBox.setResponder(val -> {
            categorySearchFilter = val;
            updateCategoryDropdown();
            categoryDropdownVisible = !val.isEmpty();
        });
        this.addRenderableWidget(categorySearchBox);

        // --- Text-override popup widgets (children; rendered manually on top of the popup) ---
        overrideNameField = new EditBox(this.font, 0, 0, 10, FIELD_HEIGHT,
                Component.translatable("editor.historystages.text_override.name"));
        overrideNameField.setMaxLength(128);
        overrideNameField.visible = false;
        this.addWidget(overrideNameField);

        overrideTooltipField = new EditBox(this.font, 0, 0, 10, FIELD_HEIGHT,
                Component.translatable("editor.historystages.text_override.tooltip"));
        overrideTooltipField.setMaxLength(256);
        overrideTooltipField.visible = false;
        this.addWidget(overrideTooltipField);

        overrideResetBtn = net.bananemdnsa.historystages.client.editor.widget.StyledButton.of(
                Component.translatable("editor.historystages.text_override.reset"),
                btn -> resetOverride(), 0, 0, 10, 18);
        overrideResetBtn.visible = false;
        this.addWidget(overrideResetBtn);

        overrideDoneBtn = net.bananemdnsa.historystages.client.editor.widget.StyledButton.of(
                Component.translatable("editor.historystages.done"),
                btn -> applyOverrideAndClose(), 0, 0, 10, 18);
        overrideDoneBtn.visible = false;
        this.addWidget(overrideDoneBtn);



        modStructurePopup = new ModEntrySelectionPopup(
                Component.translatable("editor.historystages.popup.kind.structures"),
                net.bananemdnsa.historystages.client.ClientStructureRegistry::get,
                selectedIds -> {
            // In edit mode, drop the previous mod-linked structures for this mod first so
            // unchecked rows are actually removed.
            if (editingModId != null) {
                if (structureTab.replaceModSelection(editingModId, selectedIds))
                    hasChanges = true;
            }
            if (!selectedIds.isEmpty())
                hasChanges = true;
            updateMaxScroll();
            showModBiomePopup();
        });

        modBiomePopup = new ModEntrySelectionPopup(
                Component.translatable("editor.historystages.popup.kind.biomes"),
                StageDetailScreen::allKnownBiomeIds,
                selectedIds -> {
            if (editingModId != null && biomeTab.replaceModSelection(editingModId, selectedIds))
                hasChanges = true;
            else if (!selectedIds.isEmpty())
                hasChanges = true;
            editingModId = null;
            updateMaxScroll();
        });

        modEntityPopup = new ModEntitySelectionPopup((spawnlockIds, attacklockIds, interactionlockIds) -> {
            // In edit mode, drop the previous mod-linked entity locks for this mod first
            // so unchecked rows are actually removed.
            if (editingModId != null) {
                String prefix = editingModId + ":";
                boolean removedSpawn = editSpawnlock
                        .removeIf(id -> {
                            if (id.startsWith(prefix) && editModLinked.contains(id)) {
                                editSpawnlockSources.remove(id);
                                editSpawnlockDimensions.remove(id);
                                return true;
                            }
                            return false;
                        });
                boolean removedAttack = editAttacklock
                        .removeIf(id -> id.startsWith(prefix) && editModLinked.contains(id));
                boolean removedInteract = editInteractionlock
                        .removeIf(id -> {
                            if (id.startsWith(prefix) && editModLinked.contains(id)) {
                                editInteractionlockActions.remove(id);
                                editInteractionlockItems.remove(id);
                                return true;
                            }
                            return false;
                        });
                boolean removedLink = editModLinked.removeIf(id -> id.startsWith(prefix));
                if (removedSpawn || removedAttack || removedInteract || removedLink)
                    hasChanges = true;
            }
            for (String id : spawnlockIds) {
                if (!editSpawnlock.contains(id))
                    editSpawnlock.add(id);
                if (!editModLinked.contains(id))
                    editModLinked.add(id);
            }
            for (String id : attacklockIds) {
                if (!editAttacklock.contains(id))
                    editAttacklock.add(id);
                if (!editModLinked.contains(id))
                    editModLinked.add(id);
            }
            for (String id : interactionlockIds) {
                if (!editInteractionlock.contains(id))
                    editInteractionlock.add(id);
                if (!editModLinked.contains(id))
                    editModLinked.add(id);
            }
            if (!spawnlockIds.isEmpty() || !attacklockIds.isEmpty() || !interactionlockIds.isEmpty())
                hasChanges = true;
            updateMaxScroll();
            showModStructurePopup();
        }, () -> {
            // Skip pressed: leave entity locks untouched, but still chain to structure popup
            showModStructurePopup();
        });

        dimFilterPopup = new DimensionFilterPopup((entityId, allowed) -> {
            if (allowed.isEmpty()) {
                editSpawnlockDimensions.remove(entityId);
            } else {
                editSpawnlockDimensions.put(entityId, allowed);
            }
            hasChanges = true;
        });

        generationLimitPopup = new GenerationLimitPopup(this::applyGenerationRule);

        tradeLevelsPopup = new TradeLevelsPopup((professionId, gatedLevels) -> {
            tradeProfessionTab.setLevelsFor(professionId, gatedLevels);
            hasChanges = true;
            updateMaxScroll();
        });
        spawnSourcesPopup = new SpawnSourcesPopup((entityId, blocked) -> {
            if (blocked.isEmpty()) {
                editSpawnlockSources.remove(entityId);
            } else {
                editSpawnlockSources.put(entityId, blocked);
            }
            hasChanges = true;
        });

        interactionActionsPopup = new net.bananemdnsa.historystages.client.editor.widget.popup.InteractionActionsPopup((entityId, blocked) -> {
            // null = every action blocked, which is the default and stored as no filter at all.
            // An empty list is a different answer — the entry blocks nothing — and is kept.
            if (blocked == null) {
                editInteractionlockActions.remove(entityId);
            } else {
                editInteractionlockActions.put(entityId, blocked);
            }
            hasChanges = true;
        });

        interactionItemsPopup = new net.bananemdnsa.historystages.client.editor.widget.popup.InteractionItemsPopup(
                new net.bananemdnsa.historystages.client.editor.widget.popup.InteractionItemsPopup.Handler() {
            @Override
            public List<net.bananemdnsa.historystages.data.ItemEntry> items(String entityId) {
                return editInteractionlockItems.get(entityId);
            }

            @Override
            public void openRowMenu(String entityId, int index, int mouseX, int mouseY) {
                openInteractionItemMenu(entityId, index, mouseX, mouseY);
            }

            @Override
            public void addItem(String entityId) {
                interactionItemsTarget = entityId;
                filterItemSearch.setFilter("");
                filterItemSearch.show(StageDetailScreen.this.width / 2, StageDetailScreen.this.height / 2,
                        StageDetailScreen.this.width);
            }

            @Override
            public void addTag(String entityId) {
                interactionItemsTarget = entityId;
                filterTagSearch.setFilter("");
                filterTagSearch.show(StageDetailScreen.this.width / 2, StageDetailScreen.this.height / 2,
                        StageDetailScreen.this.width);
            }
        });

        filterItemSearch = new SearchableItemList(itemId -> {
            addInteractionFilterEntry(itemId);
        }, () -> interactionFilterIds());
        filterItemSearch.setMultiSelect(true);

        filterTagSearch = new SearchableTagList(tagId -> {
            addInteractionFilterEntry("#" + tagId);
        }, () -> interactionFilterTagIds());
        filterTagSearch.setMultiSelect(true);




        // init() runs again on every resize; the tabs survive, only their pickers are rebuilt.
        for (CategoryTab tab : categoryTabs.values()) tab.rebuildPicker();




        contextMenu = new ContextMenu();
        // Returning from the NBT sub-screen re-runs init(); restore the item filter popup so the
        // round trip doesn't dump the user back on the bare tab.
        if (interactionItemsTarget != null) {
            interactionItemsPopup.show(interactionItemsTarget);
        }
        updateMaxScroll();
    }

    /**
     * Second step of the mod-lock chain (entities → structures → biomes). Skips straight to the
     * biome step when the mod contributes no structures.
     *
     * @return false when neither this step nor the biome step had anything to show
     */
    private boolean showModStructurePopup() {
        if (pendingModId == null) {
            editingModId = null;
            return false;
        }
        if (modStructurePopup.showForMod(pendingModId, pendingModDisplayName,
                this.width / 2, this.height / 2, structureTab.entries())) {
            return true;
        }
        return showModBiomePopup();
    }

    /** Final step of the mod-lock chain. Clears the edit marker when there is nothing to show. */
    private boolean showModBiomePopup() {
        if (pendingModId != null && modBiomePopup.showForMod(pendingModId, pendingModDisplayName,
                this.width / 2, this.height / 2, biomeTab.entries())) {
            return true;
        }
        editingModId = null;
        return false;
    }

    /** All biome IDs from the client's (datapack-driven) registry; empty outside a world. */
    private static Collection<String> allKnownBiomeIds() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return List.of();
        List<String> ids = new ArrayList<>();
        for (net.minecraft.resources.ResourceLocation key : mc.level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.BIOME).keySet()) {
            ids.add(key.toString());
        }
        return ids;
    }

    private boolean isAnyOverlayVisible() {
        return (iconSearch != null && iconSearch.isVisible())
                || anyCategoryPickerVisible()
                || lockActionsPopupVisible || spawnSourcesPopup.isVisible()
                || tradeLevelsPopup.isVisible() || interactionActionsPopup.isVisible()
                || interactionItemsPopup.isVisible() || filterItemSearch.isVisible() || filterTagSearch.isVisible()
                || dimFilterPopup.isVisible() || generationLimitPopup.isVisible()
                || contextMenu.isVisible() || recipePopupVisible
                || modEntityPopup.isVisible() || modStructurePopup.isVisible() || modBiomePopup.isVisible()
                || actionOverlay() != null;
    }

    /**
     * The overlay a declared action put up, while it is still up.
     *
     * <p>Drops the reference as soon as the popup hides itself. Holding on to a hidden overlay is
     * how an editor stops responding without throwing anything: every click keeps being forwarded
     * to something invisible.
     */
    private PickerOverlay actionOverlay() {
        if (actionOverlay != null && !actionOverlay.isVisible()) actionOverlay = null;
        return actionOverlay;
    }

    /** The generation rule stored for a structure entry, or null while it generates unrestricted. */
    private StructureGenerationRule generationRuleFor(String structureId) {
        return structureTab.generationRuleFor(structureId);
    }

    /** Callback of the generation dialog; a null rule means the entry goes back to unrestricted. */
    private void applyGenerationRule(String structureId, StructureGenerationRule rule) {
        structureTab.applyGenerationRule(structureId, rule);
        hasChanges = true;
    }

    /**
     * EditBox variant that replaces the default hard-blink cursor with a smooth
     * gold sine-wave pulse. Everything else (key handling, text storage, click
     * detection) comes from EditBox unchanged.
     */
    private class CategorySearchEditBox extends EditBox {
        CategorySearchEditBox(int x, int y, int w, int h) {
            super(StageDetailScreen.this.font, x, y, w, h, Component.empty());
            setBordered(false);
            setMaxLength(128);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            String val = getValue();
            String highlighted = getHighlighted();
            int textX = getX() + 2;
            int textY = getY() + (getHeight() - 8) / 2;
            int maxW = getWidth() - 4;

            // Show the tail of the string so cursor stays visible while typing
            String display;
            int displayStart;
            if (font.width(val) <= maxW) {
                display = val;
                displayStart = 0;
            } else {
                String rev = new StringBuilder(val).reverse().toString();
                String revDisplay = font.plainSubstrByWidth(rev, maxW);
                displayStart = val.length() - revDisplay.length();
                display = val.substring(displayStart);
            }

            // Blue selection highlight
            if (!highlighted.isEmpty()) {
                int selInFull = val.indexOf(highlighted);
                if (selInFull >= 0) {
                    int selStart = Math.max(0, selInFull - displayStart);
                    int selEnd = Math.min(display.length(), selInFull + highlighted.length() - displayStart);
                    if (selEnd > selStart) {
                        int selX = textX + font.width(display.substring(0, selStart));
                        int selW = font.width(display.substring(selStart, selEnd));
                        g.fill(selX, textY - 1, selX + selW, textY + 9, 0x7F0077FF);
                    }
                }
            }

            g.drawString(font, display, textX, textY, 0xFFFFFF, false);

            if (isFocused()) {
                int cursorX = textX + font.width(display);
                float pulse = (float) (0.45 + 0.55 * Math.sin(System.currentTimeMillis() / 250.0));
                int alpha = (int) (pulse * 255);
                g.drawString(font, "_", cursorX, textY, (alpha << 24) | 0xFFCC00, false);
            }
        }
    }



    private void updateCategoryDropdown() {
        categoryDropdownSuggestions = new ArrayList<>();
        categoryDropdownScrollOffset = 0;
        if (categorySearchFilter.isEmpty()) return;
        String query = categorySearchFilter.toLowerCase();
        for (String entry : getActiveList()) {
            if (entry.toLowerCase().contains(query)) {
                categoryDropdownSuggestions.add(entry);
                if (categoryDropdownSuggestions.size() >= MAX_DROPDOWN_COLLECT) break;
            }
        }
    }

    private void switchTab(int tab) {
        if (isTabDisabled(tab)) return;
        if (activeTab != tab) {
            activeTab = tab;
            scrollOffset = 0;
            smoothScrollOffset.set(0.0f);
            tabSwitchTime = System.currentTimeMillis();
            rowList(tab).resetSlideIn();
            CategoryTab switched = categoryTabs.get(tab);
            if (switched != null) switched.onShown();
            updateAddButton();
            updateMaxScroll();
            // Reset category search when switching tabs
            categorySearchFilter = "";
            categoryDropdownVisible = false;
            categoryDropdownSuggestions = new ArrayList<>();
            if (categorySearchBox != null) categorySearchBox.setValue("");
        }
    }

    /** Closes any open category-driven picker. */
    private void hideCategoryPickers() {
        for (CategoryTab tab : categoryTabs.values()) {
            if (tab.activeOverlay() != null && tab.activeOverlay().isVisible()) tab.activeOverlay().hide();
        }
    }

    /** True when any category-driven picker is open. */
    private boolean anyCategoryPickerVisible() {
        for (CategoryTab tab : categoryTabs.values()) {
            if (tab.activeOverlay() != null && tab.activeOverlay().isVisible()) return true;
        }
        return false;
    }

    /** Forwards one input call to whichever category-driven picker is open. */
    private boolean anyCategoryPicker(java.util.function.Predicate<PickerOverlay> action) {
        for (CategoryTab tab : categoryTabs.values()) {
            if (tab.activeOverlay() != null && tab.activeOverlay().isVisible() && action.test(tab.activeOverlay())) return true;
        }
        return false;
    }

    /**
     * Dual-phase entries for the tab being rendered. Looking at an individual stage, the map holds
     * entry to global stage ids, and the other way round for a global stage — that inversion is
     * deliberate and predates the category registry.
     */
    private Map<String, Set<String>> dualPhaseMapForTab(int tab) {
        CategoryTab categoryTab = sectionAt(tab);
        if (categoryTab == null) return null;
        // Looking at an individual stage the map holds entry to global stage ids, and the other
        // way round for a global stage — that inversion is deliberate and predates the registry.
        return isIndividual
                ? CategoryLockIndexes.dualPhaseGlobal(categoryTab.categoryId())
                : CategoryLockIndexes.dualPhaseIndividual(categoryTab.categoryId());
    }

    /** Splits and rebuilds an ItemEntry, which is how items and mod exceptions store their rows. */
    private static final RichEntryCategoryTab.EntryAdapter<net.bananemdnsa.historystages.data.ItemEntry>
            ITEM_ENTRY_ADAPTER = new RichEntryCategoryTab.EntryAdapter<>() {
        @Override public String id(net.bananemdnsa.historystages.data.ItemEntry e) { return e.getId(); }
        @Override public com.google.gson.JsonObject nbt(net.bananemdnsa.historystages.data.ItemEntry e) { return e.getNbt(); }
        @Override public List<String> lockActions(net.bananemdnsa.historystages.data.ItemEntry e) { return e.getLockActions(); }
        @Override public String nameText(net.bananemdnsa.historystages.data.ItemEntry e) { return e.getNameTextOverride(); }
        @Override public String tooltipText(net.bananemdnsa.historystages.data.ItemEntry e) { return e.getTooltipTextOverride(); }
        @Override public net.bananemdnsa.historystages.data.ItemEntry build(
                String id, com.google.gson.JsonObject nbt, List<String> lockActions,
                String nameText, String tooltipText) {
            return new net.bananemdnsa.historystages.data.ItemEntry(id, nbt, lockActions, nameText, tooltipText);
        }
    };

    /** Splits and rebuilds a FluidEntry. Hands back null for nbt — a fluid entry has none. */
    private static final RichEntryCategoryTab.EntryAdapter<net.bananemdnsa.historystages.data.FluidEntry>
            FLUID_ENTRY_ADAPTER = new RichEntryCategoryTab.EntryAdapter<>() {
        @Override public String id(net.bananemdnsa.historystages.data.FluidEntry e) { return e.getId(); }
        @Override public com.google.gson.JsonObject nbt(net.bananemdnsa.historystages.data.FluidEntry e) { return null; }
        @Override public List<String> lockActions(net.bananemdnsa.historystages.data.FluidEntry e) { return e.getLockActions(); }
        @Override public String nameText(net.bananemdnsa.historystages.data.FluidEntry e) { return e.getNameTextOverride(); }
        @Override public String tooltipText(net.bananemdnsa.historystages.data.FluidEntry e) { return e.getTooltipTextOverride(); }
        @Override public net.bananemdnsa.historystages.data.FluidEntry build(
                String id, com.google.gson.JsonObject nbt, List<String> lockActions,
                String nameText, String tooltipText) {
            return new net.bananemdnsa.historystages.data.FluidEntry(id, lockActions, nameText, tooltipText);
        }
    };

    /** Splits and rebuilds a NamedLockEntry, which is how tags and mods store their rows. */

    private static final RichEntryCategoryTab.EntryAdapter<net.bananemdnsa.historystages.data.lock.NamedLockEntry>
            NAMED_LOCK_ENTRY_ADAPTER = new RichEntryCategoryTab.EntryAdapter<>() {
        @Override public String id(net.bananemdnsa.historystages.data.lock.NamedLockEntry e) { return e.getId(); }
        @Override public com.google.gson.JsonObject nbt(net.bananemdnsa.historystages.data.lock.NamedLockEntry e) { return e.getNbt(); }
        @Override public List<String> lockActions(net.bananemdnsa.historystages.data.lock.NamedLockEntry e) { return e.getLockActions(); }
        @Override public String nameText(net.bananemdnsa.historystages.data.lock.NamedLockEntry e) { return e.getNameTextOverride(); }
        @Override public String tooltipText(net.bananemdnsa.historystages.data.lock.NamedLockEntry e) { return e.getTooltipTextOverride(); }
        @Override public net.bananemdnsa.historystages.data.lock.NamedLockEntry build(
                String id, com.google.gson.JsonObject nbt, List<String> lockActions,
                String nameText, String tooltipText) {
            return new net.bananemdnsa.historystages.data.lock.NamedLockEntry(
                    id, lockActions, nameText, tooltipText, nbt);
        }
    };

    /** One entity picker per entity tab, each adding to the list of the tab that opened it. */
    private SearchableEntityList createEntityPicker(
            java.util.function.Consumer<String> onSelect,
            java.util.function.Supplier<java.util.Collection<String>> alreadyAdded) {
        SearchableEntityList picker = new SearchableEntityList(onSelect::accept, alreadyAdded::get);
        picker.setMultiSelect(true);
        return picker;
    }

    /**
     * Appends whatever extra menu entries the category's editor declared.
     *
     * <p>Placed after the category's own built-in entries and before copy and remove, so those two
     * stay where a maintainer expects them and an addon adds to the menu rather than replacing it.
     */
    private void addDeclaredEntryActions(int tabIdx, int entryIdx) {
        CategoryTab tab = sectionAt(tabIdx);
        if (tab == null) return;
        CategoryEditor editor = CategoryEditors.byCategory(tab.categoryId());
        if (editor == null) return;
        for (EntryAction action : editor.entryActions()) {
            contextMenu.addEntry(Component.translatable(action.langKey()).getString(),
                    () -> action.run(new EntryActionContext(entryIdx, () -> hasChanges = true,
                            screen -> this.minecraft.setScreen(screen),
                            overlay -> this.actionOverlay = overlay)));
        }
    }

    private List<String> getActiveList() {


        return getListForSection(activeTab);
    }

    // Category ids, so the screen can ask "is this the items tab?" instead of "is this tab 0?".
    // The tab index is a position in the strip and moves whenever a tab is added or reordered;
    // the category id does not. Every behavioural check below goes through isTab.
    private static final String CAT_ITEMS      = "historystages:items";
    private static final String CAT_FLUIDS     = "historystages:fluids";
    private static final String CAT_TAGS       = "historystages:tags";
    private static final String CAT_MODS       = "historystages:mods";
    private static final String CAT_ATTACK     = "historystages:attacklock";
    private static final String CAT_EXCEPTIONS = "historystages:mod_exceptions";
    private static final String CAT_RECIPES    = "historystages:recipes";
    private static final String CAT_SPAWN      = "historystages:spawnlock";
    private static final String CAT_INTERACT   = "historystages:interactionlock";
    private static final String CAT_STRUCTURES = "historystages:structures";
    private static final String CAT_BIOMES     = "historystages:biomes";
    /**
     * The item section of the trades tab. Its two sibling sections report ids of their own, which
     * is what lets {@link #isTab} tell an item row from a profession row inside one tab.
     */
    private static final String CAT_TRADES     = "historystages:trades";
    /** The profession section of the trades tab. */
    private static final String CAT_TRADE_PROFESSIONS = "historystages:trade_professions";

    /**
     * The tab at this index, or — when it has sections — the section that is showing.
     *
     * <p>Everything below that asks what a <em>row</em> is goes through this. A tab with sections
     * reports one category id for its label and its tooltip, which is right for a strip that shows
     * one name; it is not enough for a right-click menu, which has to tell an item row from a
     * profession row and would otherwise offer the NBT editor on both.
     */
    private CategoryTab sectionAt(int tab) {
        CategoryTab categoryTab = categoryTabs.get(tab);
        return categoryTab instanceof CompositeCategoryTab composite
                ? composite.activeSection() : categoryTab;
    }

    /** Whether the tab at this index — or its visible section — belongs to that category. */
    private boolean isTab(int tab, String categoryId) {
        CategoryTab categoryTab = sectionAt(tab);
        return categoryTab != null && categoryId.equals(categoryTab.categoryId());
    }

    /** Whether the tab at this index is any of the given categories. */
    private boolean isAnyTab(int tab, String... categoryIds) {
        for (String id : categoryIds) {
            if (isTab(tab, id)) return true;
        }
        return false;
    }

    List<String> getListForSection(int sectionIndex) {
        CategoryTab tab = categoryTabs.get(sectionIndex);
        return tab != null ? tab.entries() : new ArrayList<>();
    }

    /**
     * Top of the scrolling list.
     *
     * <p>Every other measurement in this screen is taken from here — the render and input
     * contexts, the scissor, the scrollbar and the click gate all read it — which is what lets a
     * tab with a section bar above its list reserve the space in one place. Reserving it in three
     * would put the rows, the hit test and the scroll extent one strip apart, and clicks would
     * land on the row above the one under the cursor.
     */
    private int listTop() {
        return HEADER_HEIGHT + (sectionBar() == null ? 0 : SECTION_BAR_HEIGHT);
    }

    /** The tab's section bar, or null when the open tab has no sections. */
    @Nullable
    private CompositeCategoryTab sectionBar() {
        return categoryTabs.get(activeTab) instanceof CompositeCategoryTab composite
                ? composite : null;
    }

    private int listBottom() {
        return this.height - 40;
    }

    private int contentLeft() {
        return 30;
    }

    private int contentRight() {
        return this.width - 30;
    }

    /**
     * The rectangle a tab draws in, scroll already applied.
     *
     * <p>Private to this screen rather than shared with the dependency editor: the two have
     * different content rectangles and different scroll animations, and a common base class for
     * two screens is a bigger commitment than two short methods.
     */
    private TabRenderContext renderContext(GuiGraphics g, int mouseX, int mouseY,
            boolean inputBlocked) {
        return new TabRenderContext(g, this.font, contentLeft(),
                listTop() - Math.round(smoothScrollOffset.value()) + CARD_GAP,
                contentRight() - contentLeft(), listTop(), listBottom(), mouseX, mouseY,
                inputBlocked,
                (key, text) -> { currentTooltipKey = key; currentTooltipText = text; });
    }

    private TabInputContext inputContext(double mouseX, double mouseY) {
        return new TabInputContext(contentLeft(),
                listTop() - Math.round(smoothScrollOffset.value()) + CARD_GAP,
                contentRight() - contentLeft(), listTop(), listBottom(), mouseX, mouseY);
    }


    /**
     * One row list per tab, so two tabs cannot share a hover animation and a tab switch does not
     * carry the previous tab's hover into the next.
     */
    private EditorRowList rowList(int tabIndex) {
        return rowLists.computeIfAbsent(tabIndex, k -> new EditorRowList());
    }

    /**
     * What one row of the active tab shows.
     *
     * <p>The built-in categories still decide this here, by tab index, the way they always
     * have; migrating them onto the tabs is internal and invisible to addons. What is new is the
     * tail: a tab's own {@code iconItemId} and {@code badgeText} are honoured after them, which
     * closes the gap Phase 3 left where only the dependency editor read those two.
     *
     * <p>Badges are declared rather than positioned. The old loop measured each one and advanced a
     * running {@code badgeW} by hand, and the mod badge <em>assigned</em> that width instead of
     * adding to it, so it could draw over an earlier badge. Declaring them stacks them correctly.
     */
    private void decorateRow(EditorRowList.Row row, int index, String entry) {
        boolean isItemsTab = isTab(activeTab, CAT_ITEMS);
        boolean isTagsTab = isTab(activeTab, CAT_TAGS);
        boolean isExceptionsTab = isTab(activeTab, CAT_EXCEPTIONS);
        boolean isEntityTab = isAnyTab(activeTab, CAT_ATTACK, CAT_SPAWN, CAT_INTERACT);

        // Leading icon
        if (isItemsTab || isExceptionsTab) {
            ItemStack stack = getItemStack(entry);
            if (!stack.isEmpty()) row.leading(14, (g, x, y, w, h) -> renderStackIcon(g, stack, x, y));
        } else if (isTab(activeTab, CAT_RECIPES)) {
            ItemStack[] info = getRecipeInfo(entry);
            if (info != null && info.length > 1 && !info[1].isEmpty()) {
                ItemStack result = info[1];
                row.leading(14, (g, x, y, w, h) -> renderStackIcon(g, result, x, y));
            }
        } else if (isEntityTab) {
            LivingEntity living = EntityPreviewRenderer.getOrCreate(entry);
            if (living != null) {
                row.leading(16, (g, x, y, w, h) -> {
                    try {
                        float angle = (System.currentTimeMillis() % 3600) / 10.0f;
                        int scale = (int) Math.max(3,
                                9.0f / Math.max(living.getBbWidth(), living.getBbHeight()));
                        g.enableScissor(x, y, x + w, y + h);
                        // -1 and +1 keep the model exactly where the old loop drew it.
                        EntityPreviewRenderer.renderSpinning(g, x + w / 2 - 1, y + h + 1, scale, angle, living);
                        g.disableScissor();
                    } catch (Exception ignored) {
                    }
                });
            }
        } else {
            // A tab that paints the zone itself has already accounted for its own icon, so the
            // single-item hook is only asked when nothing claimed the zone.
            EditorTab.LeadingArt art =
                    activeTabObject() == null ? null : activeTabObject().leadingArt(index);
            if (art != null) {
                row.leading(art.width(), art.painter());
            } else {
                String iconId = activeTabObject() == null ? null : activeTabObject().iconItemId(index);
                if (iconId != null) {
                    ItemStack stack = getItemStack(iconId);
                    if (!stack.isEmpty()) row.leading(14, (g, x, y, w, h) -> renderStackIcon(g, stack, x, y));
                }
            }
        }

        // Badges, rightmost first
        if (isItemsTab && itemTab.nbtByIndex().containsKey(index)
                || isTagsTab && tagTab.nbtByIndex().containsKey(index)
                || isExceptionsTab && modExceptionTab.nbtByIndex().containsKey(index)) {
            row.badge("§6[NBT]", 0xFFCC00);
        }

        Map<Integer, List<String>> tabLockActions = getLockActionsMapForTab(activeTab);
        List<String> entryLockActions = tabLockActions != null ? tabLockActions.get(index) : null;
        if (entryLockActions != null) {
            String label = Component.translatable("editor.historystages.badge.actions").getString();
            row.badge("[" + label + ": " + entryLockActions.size() + "/"
                            + lockActionsForTab(activeTab).size() + "]",
                    0xCCAA66);
        }

        // How much of the pack this one entry reaches. Gating a common fluid can take out four
        // figures of recipes, and "ingredient" is on by default — so the number belongs on the
        // row, where it is seen before the decision rather than after it.
        if (isTab(activeTab, CAT_FLUIDS)) {
            int recipeCount = net.bananemdnsa.historystages.data.lock.FluidRecipeIndex
                    .recipeCountFor(entry);
            if (recipeCount > 0) {
                String label = Component.translatable("editor.historystages.badge.recipes").getString();
                row.badge("[" + label + ": " + recipeCount + "]", 0xCCAA66);
            }
        }

        if (isAnyTab(activeTab, CAT_ITEMS, CAT_FLUIDS, CAT_TAGS, CAT_MODS)) {
            if (overrideNameMap(activeTab).containsKey(index)) {
                row.badge("[" + Component.translatable("editor.historystages.badge.name_override")
                        .getString() + "]", 0xBBBBBB);
            }
            if (overrideTooltipMap(activeTab).containsKey(index)) {
                row.badge("[" + Component.translatable("editor.historystages.badge.tooltip_override")
                        .getString() + "]", 0xBBBBBB);
            }
        }

        if (isTab(activeTab, CAT_SPAWN)) {
            List<String> srcFilter = editSpawnlockSources.get(entry);
            if (srcFilter != null && !srcFilter.isEmpty() && srcFilter.size() < SPAWN_SOURCE_KEYS.length) {
                String label = Component.translatable("editor.historystages.badge.sources").getString();
                row.badge("[" + label + ": " + srcFilter.size() + "/" + SPAWN_SOURCE_KEYS.length + "]",
                        0xCCAA66);
            }
            List<String> dimFilter = editSpawnlockDimensions.get(entry);
            if (dimFilter != null && !dimFilter.isEmpty()) {
                String label = Component.translatable("editor.historystages.badge.dimensions").getString();
                row.badge("[" + label + ": " + dimFilter.size() + "]", 0xCCAA66);
            }
        }

        if (isTab(activeTab, CAT_INTERACT)) {
            List<String> actFilter = editInteractionlockActions.get(entry);
            int allActions = net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry.ALL_ACTIONS.size();
            if (actFilter != null && !actFilter.isEmpty() && actFilter.size() < allActions) {
                String label = Component.translatable("editor.historystages.badge.actions").getString();
                row.badge("[" + label + ": " + actFilter.size() + "/" + allActions + "]", 0xCCAA66);
            }
            List<net.bananemdnsa.historystages.data.ItemEntry> itemFilter =
                    editInteractionlockItems.get(entry);
            if (itemFilter != null && !itemFilter.isEmpty()) {
                String label = Component.translatable("editor.historystages.badge.items").getString();
                row.badge("[" + label + ": " + itemFilter.size() + "]", 0xCCAA66);
            }
        }

        if ((isEntityTab && editModLinked.contains(entry))
                || (isTab(activeTab, CAT_STRUCTURES) && structureTab.modLinkedEntries().contains(entry))
                || (isTab(activeTab, CAT_BIOMES) && biomeTab.modLinkedEntries().contains(entry))) {
            row.badge("§7[mod]", 0x999999);
        }

        if (isTab(activeTab, CAT_STRUCTURES)) {
            StructureGenerationRule genRule = generationRuleFor(entry);
            if (genRule != null) {
                row.badge(genRule.max() == 0
                        ? Component.translatable("editor.historystages.badge.no_gen").getString()
                        : Component.translatable(
                                genRule.phase() == GenerationPhase.WHILE_LOCKED
                                        ? "editor.historystages.badge.gen_limit"
                                        : "editor.historystages.badge.gen_after",
                                genRule.max()).getString(), 0xCC7766);
            }
        }

        String tabBadge = activeTabObject() == null ? null : activeTabObject().badgeText(index);
        if (tabBadge != null) row.badge(tabBadge);

        // Text, with the dual-phase mark and its tooltip
        boolean dual = false;
        Map<String, Set<String>> dualMap = dualPhaseMapForTab(activeTab);
        if (dualMap != null && dualMap.containsKey(entry)) {
            dual = true;
            if (row.isHovered()) {
                String tooltipKey = isIndividual
                        ? "editor.historystages.dual_phase_tooltip"
                        : "editor.historystages.dual_phase_tooltip_global";
                currentTooltipKey = "dual-phase:" + entry;
                currentTooltipText = String.format(
                        Component.translatable(tooltipKey).getString(), dualMap.get(entry));
            }
        }
        // A recipe lock pointing at a recipe that is not loaded gates nothing. Red plus a
        // tooltip rather than removal: the entry is still what the author wrote, and a recipe
        // comes back when its mod or its script does. Script-generated ids land here most often,
        // because KubeJS renumbers them whenever the script is reordered.
        boolean missingRecipe = isTab(activeTab, CAT_RECIPES)
                && net.bananemdnsa.historystages.data.lock.MissingRecipeIds.isMissing(entry);
        if (missingRecipe && row.isHovered()) {
            currentTooltipKey = "missing-recipe:" + entry;
            currentTooltipText = Component.translatable("editor.historystages.recipes.missing").getString();
        }

        // What is drawn and what is stored part company here. The entry stays what "copy id"
        // copies; a tab may say the row should read as something friendlier.
        String shown = activeTabObject() == null ? null : activeTabObject().displayText(index, entry);
        if (shown == null) shown = entry;
        row.text((missingRecipe ? "§c" : "") + shown + (dual ? " [Dual]" : ""));
    }

    private CategoryTab activeTabObject() {
        return categoryTabs.get(activeTab);
    }

    /**
     * Whether the open tab drew its own content this frame.
     *
     * <p>Recorded rather than asked again: {@code renderContent} is what answers it, and only by
     * running. A tab that drew itself is also the only thing that knows where its rows ended up,
     * so this is what decides whether a right-click asks the tab or walks the host's rows.
     */
    private boolean activeTabDrewItself;

    /** Animations for the section bar. One bar is ever on screen, so one state is enough. */
    private final SegmentBar.State sectionBarState = new SegmentBar.State();

    /**
     * The Add button, kept so it can be hidden.
     *
     * <p>Rebuilt by {@code init()} on every resize, so it is not final and every reader has to
     * cope with it being null before the first one.
     */
    @Nullable
    private StyledButton addButton;

    /** Hides the Add button for a tab with nothing to add to — the merchant levels are five switches. */
    private void updateAddButton() {
        CategoryTab tab = categoryTabs.get(activeTab);
        if (addButton != null) addButton.visible = tab == null || tab.hasAddButton();
    }

    /**
     * Draws the section bar above the list, for a tab that has sections.
     *
     * <p>Above the scrolling area rather than at the top of it. A switcher that scrolls out of
     * sight once a section fills the screen is a switcher nobody can find their way back to, and
     * putting it in the content would also make every row hit test depend on getting the same
     * offset right in three separate places.
     */
    private void renderSectionBar(GuiGraphics g, int mouseX, int mouseY) {
        CompositeCategoryTab composite = sectionBar();
        if (composite == null) return;
        List<String> labels = composite.sectionLabels();
        int x = contentLeft();
        int y = HEADER_HEIGHT + 3;
        int hovered = isAnyOverlayVisible() ? -1
                : SegmentBar.segmentAt(this.font, x, y, mouseX, mouseY, labels);
        sectionBarState.update(composite.activeIndex(), hovered, labels.size());
        SegmentBar.draw(g, this.font, x, y, labels, composite.activeIndex(), sectionBarState);
    }

    /**
     * Handles a click on the section bar.
     *
     * @return true when the click was in the bar's strip, whether or not it hit a segment — the
     *         strip is the tab's, and letting a near miss fall through to the list below would
     *         mean the row nearest the bar reacts to clicks aimed at the bar
     */
    private boolean sectionBarClicked(double mouseX, double mouseY) {
        CompositeCategoryTab composite = sectionBar();
        if (composite == null) return false;
        if (mouseY < HEADER_HEIGHT || mouseY >= HEADER_HEIGHT + SECTION_BAR_HEIGHT) return false;
        int picked = SegmentBar.indexAt(this.font, contentLeft(), mouseX, composite.sectionLabels());
        if (picked >= 0 && composite.setActiveIndex(picked)) {
            scrollOffset = 0;
            smoothScrollOffset.set(0.0f);
            rowList(activeTab).resetSlideIn();
            updateAddButton();
            updateMaxScroll();
            categorySearchFilter = "";
            categoryDropdownVisible = false;
            categoryDropdownSuggestions = new ArrayList<>();
            if (categorySearchBox != null) categorySearchBox.setValue("");
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        return true;
    }

    private void renderStackIcon(GuiGraphics g, ItemStack stack, int x, int y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.85f, 0.85f, 1.0f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    void updateMaxScroll() {
        // Asked of the tab, so one that draws rows of another height — or content that is not rows
        // at all — sizes the scrollbar correctly without this method knowing what it drew.
        CategoryTab tab = categoryTabs.get(activeTab);
        int contentHeight = (tab != null
                ? tab.contentHeight(contentRight() - contentLeft())
                : EditorRowList.heightFor(getActiveList().size())) + CARD_GAP;
        // Measured from listTop(), so a tab with a section bar loses exactly the strip the bar
        // took. The 50 is the old slack below the last row and stays as it was — this task moved
        // the top of the list, not the bottom of it.
        int visibleHeight = this.height - listTop() - 50;
        maxScroll = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op — we draw our own background in render() and want to avoid 1.21's menu blur shader
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, 0xE0101010);

        String titleText = isNewStage
                ? Component.translatable("editor.historystages.new_stage").getString()
                : editDisplayName + " (" + originalStageId + ")";
        guiGraphics.drawCenteredString(this.font, titleText, this.width / 2, 6, 0xFFFFFF);

        // Individual badge
        if (isIndividual) {
            guiGraphics.drawString(this.font, "\u00A77[Individual]", 10, 8, 0xBBBBBB, false);
        }

        // Thin separator between title and button row
        guiGraphics.fill(10, 19, this.width - 10, 20, 0x40FFFFFF);

        // Right-side indicators inline with the button row (y=22..40)

        guiGraphics.fill(10, tabY - 2, this.width - 10, tabY - 1, 0xFF555555);

        // Track tooltip. Fields rather than locals so a tab drawing its own content can ask for
        // one through TabRenderContext without a handle on this method's frame.
        currentTooltipKey = null;
        currentTooltipText = null;


        // Animated tab indicator - smoothly slide to active tab
        if (!tabIndicatorInit) {
            tabIndicatorXAnim.set(tabX[activeTab] - tabScrollOffset);
            tabIndicatorWAnim.set(tabW[activeTab]);
            tabIndicatorInit = true;
        }
        float targetX = tabX[activeTab] - tabScrollOffset;
        float targetW = tabW[activeTab];
        tabIndicatorXAnim.approach(targetX, Timing.SCROLL_HALF_LIFE_MS);
        tabIndicatorWAnim.approach(targetW, Timing.SCROLL_HALF_LIFE_MS);
        tabIndicatorXAnim.settle(targetX, 0.5f);
        tabIndicatorWAnim.settle(targetW, 0.5f);

        // Suppress hover when overlays are open or mouse is over the category search dropdown
        boolean overlayOpen = isAnyOverlayVisible();
        boolean overDropdown = categoryDropdownVisible && !categoryDropdownSuggestions.isEmpty()
                && mouseX >= categorySearchBoxX && mouseX < categorySearchBoxX + categorySearchBoxW
                && mouseY >= 42 && mouseY < 42 + Math.min(MAX_DROPDOWN_ENTRIES, categoryDropdownSuggestions.size()) * DROPDOWN_ENTRY_H + 4;
        int effectiveMouseX = (overlayOpen || overDropdown) ? -1 : mouseX;
        int effectiveMouseY = (overlayOpen || overDropdown) ? -1 : mouseY;

        // Tab scroll arrows
        int tabAreaLeft = 20;
        int tabAreaRight = this.width - 20;
        boolean hasTabScroll = maxTabScroll > 0;
        if (hasTabScroll) {
            // Left arrow
            if (tabScrollOffset > 0) {
                boolean leftHovered = !overlayOpen && mouseX >= tabAreaLeft && mouseX < tabAreaLeft + TAB_ARROW_WIDTH
                        && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
                guiGraphics.fill(tabAreaLeft, tabY, tabAreaLeft + TAB_ARROW_WIDTH, tabY + TAB_HEIGHT,
                        leftHovered ? 0x40FFFFFF : 0x20FFFFFF);
                drawSmallText(guiGraphics, "\u25C0", tabAreaLeft + 2, tabY + 4, leftHovered ? 0xFFFFFF : 0x999999);
            }
            // Right arrow
            if (tabScrollOffset < maxTabScroll) {
                boolean rightHovered = !overlayOpen && mouseX >= tabAreaRight - TAB_ARROW_WIDTH && mouseX < tabAreaRight
                        && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;
                guiGraphics.fill(tabAreaRight - TAB_ARROW_WIDTH, tabY, tabAreaRight, tabY + TAB_HEIGHT,
                        rightHovered ? 0x40FFFFFF : 0x20FFFFFF);
                drawSmallText(guiGraphics, "\u25B6", tabAreaRight - TAB_ARROW_WIDTH + 2, tabY + 4, rightHovered ? 0xFFFFFF : 0x999999);
            }
        }

        // Clip tab area for scrolling (only when scroll is active)
        int tabClipLeft = hasTabScroll ? tabAreaLeft + TAB_ARROW_WIDTH : 0;
        int tabClipRight = hasTabScroll ? tabAreaRight - TAB_ARROW_WIDTH : this.width;
        if (hasTabScroll) {
            guiGraphics.enableScissor(tabClipLeft, tabY, tabClipRight, tabY + TAB_HEIGHT);
        }

        // Render tabs
        for (int i = 0; i < tabCount(); i++) {
            int scrolledTabX = tabX[i] - tabScrollOffset;
            boolean disabled = isTabDisabled(i);
            boolean active = (i == activeTab);
            boolean hovered = !overlayOpen && !disabled && mouseX >= Math.max(scrolledTabX, tabClipLeft)
                    && mouseX < Math.min(scrolledTabX + tabW[i], tabClipRight)
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT;

            int bg;
            if (disabled) {
                bg = 0x10FFFFFF;
            } else {
                bg = active ? 0x40FFCC00 : (hovered ? 0x25FFFFFF : 0x15FFFFFF);
            }
            guiGraphics.fill(scrolledTabX, tabY, scrolledTabX + tabW[i], tabY + TAB_HEIGHT, bg);

            String label = Component.translatable(tabKey(i)).getString();
            int entryCount = getListForSection(i).size();
            String tabText = label + " (" + entryCount + ")";
            int textColor;
            if (disabled) {
                textColor = 0x555555;
            } else {
                textColor = active ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0x999999);
            }
            drawSmallText(guiGraphics, tabText, scrolledTabX + TAB_PAD, tabY + 4, textColor);

            if (hovered) {
                currentTooltipKey = "tab." + i;
                currentTooltipText = Component.translatable(tabTooltipKey(i)).getString();
            } else if (disabled && !overlayOpen && mouseX >= Math.max(scrolledTabX, tabClipLeft)
                    && mouseX < Math.min(scrolledTabX + tabW[i], tabClipRight)
                    && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
                currentTooltipKey = "tab.disabled." + i;
                currentTooltipText = "Not available for individual stages";
            }
        }

        // Icon picker button tooltip
        if (!overlayOpen && iconPickerBtn != null && iconPickerBtn.isHoveredOrFocused()) {
            currentTooltipKey = "field.icon";
            currentTooltipText = Component.translatable("editor.historystages.icon.tooltip").getString();
        }

        // Sliding gold underline indicator
        guiGraphics.fill(Math.round(tabIndicatorXAnim.value()), tabY + TAB_HEIGHT - 2,
                Math.round(tabIndicatorXAnim.value() + tabIndicatorWAnim.value()), tabY + TAB_HEIGHT, 0xFFFFCC00);

        if (hasTabScroll) {
            guiGraphics.disableScissor();
        }

        guiGraphics.fill(10, HEADER_HEIGHT - 2, this.width - 10, HEADER_HEIGHT - 1, 0xFF555555);

        renderSectionBar(guiGraphics, mouseX, mouseY);

        int listTop = listTop();
        int listBottom = listBottom();
        int contentLeft = contentLeft();
        int contentRight = contentRight();

        guiGraphics.enableScissor(contentLeft - 10, listTop, contentRight + 10, listBottom);

        // Smooth scroll interpolation
        smoothScrollOffset.approach((float) scrollOffset, Timing.SCROLL_HALF_LIFE_MS);
        smoothScrollOffset.settle((float) scrollOffset, 0.5f);

        List<String> list = getActiveList();
        int y = listTop - Math.round(smoothScrollOffset.value()) + CARD_GAP;
        CategoryTab activeTabObject = categoryTabs.get(activeTab);
        TabRenderContext tabCtx = renderContext(guiGraphics, mouseX, mouseY,
                overlayOpen || overDropdown);
        activeTabDrewItself = activeTabObject != null && activeTabObject.renderContent(tabCtx);
        if (!activeTabDrewItself) {
            rowList(activeTab).render(tabCtx, list.size(), (row, i) -> decorateRow(row, i, list.get(i)));
        }

        // Empty state: show a centered hint when the active category has no entries. Not for a tab
        // that drew its own content — its list is not what is on screen, and the levels section
        // would get "empty" printed across five switches.
        if (list.isEmpty() && !activeTabDrewItself) {
            String emptyText = Component.translatable("editor.historystages.empty").getString();
            int centerX = (contentLeft + contentRight) / 2;
            int centerY = (listTop + listBottom) / 2;
            guiGraphics.drawCenteredString(this.font, emptyText, centerX, centerY - 4, 0x888888);
        }

        guiGraphics.disableScissor();

        if (maxScroll > 0) {
            int scrollAreaHeight = listBottom - listTop;
            int barHeight = Math.max(20, (int) ((float) scrollAreaHeight / (maxScroll + scrollAreaHeight) * scrollAreaHeight));
            int barY = listTop + (int) ((float) scrollOffset / maxScroll * (scrollAreaHeight - barHeight));
            int barX = contentRight + 2;
            boolean barHovered = mouseX >= barX - 2 && mouseX <= barX + 7
                    && mouseY >= barY && mouseY <= barY + barHeight;
            // Track
            guiGraphics.fill(barX, listTop, barX + 5, listBottom, 0x30FFFFFF);
            // Thumb
            int barColor = (scrollBarDragging || barHovered) ? 0xCCFFFFFF : 0x80FFFFFF;
            guiGraphics.fill(barX, barY, barX + 5, barY + barHeight, barColor);
        }

        if (hasChanges) {
            float phase = (System.currentTimeMillis() % (long) Timing.BREATHE_PERIOD_MS)
                    / Timing.BREATHE_PERIOD_MS;
            float pulse = 0.35f + 0.45f * Ease.breathe(phase);
            int dotAlpha = (int) (pulse * 255);
            int dotX = this.width - 60 - 8 - 6;
            String unsavedLabel = Component.translatable("editor.historystages.unsaved").getString();
            int unsavedW = (int) (this.font.width(unsavedLabel) * SMALL_SCALE);
            guiGraphics.fill(dotX - unsavedW - 4, this.height - 18, dotX - unsavedW + 2, this.height - 12,
                    (dotAlpha << 24) | 0xFFCC00);
            drawSmallText(guiGraphics, unsavedLabel, dotX - unsavedW + 5, this.height - 18, 0xFFCC00);
        }

        if (!saveError.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, saveError, this.width / 2, this.height - 38, 0xFF5555);
        }

        // Category search box — button-style background with focus/hover animation
        if (categorySearchBox != null) {
            boolean csFocused = categorySearchBox.isFocused();
            boolean csHovered = mouseX >= categorySearchBoxX && mouseX < categorySearchBoxX + categorySearchBoxW
                    && mouseY >= 22 && mouseY < 22 + FIELD_HEIGHT && !overlayOpen;
            float hp = Ease.outCubic(categorySearchHover.ramp(csFocused || csHovered,
                    Timing.HOVER_IN_MS, Timing.HOVER_OUT_MS));

            // Background — subtle white tint, brightens when focused/hovered
            int bgAlpha = (int) (0x25 + hp * 0x18);
            guiGraphics.fill(categorySearchBoxX, 22, categorySearchBoxX + categorySearchBoxW, 22 + FIELD_HEIGHT,
                    (bgAlpha << 24) | 0xFFFFFF);
            // Top + side edge highlights (same as StyledButton)
            guiGraphics.fill(categorySearchBoxX, 22, categorySearchBoxX + categorySearchBoxW, 23, 0x20FFFFFF);
            guiGraphics.fill(categorySearchBoxX, 22, categorySearchBoxX + 1, 22 + FIELD_HEIGHT, 0x15FFFFFF);
            guiGraphics.fill(categorySearchBoxX + categorySearchBoxW - 1, 22,
                    categorySearchBoxX + categorySearchBoxW, 22 + FIELD_HEIGHT, 0x15FFFFFF);
            // Bottom accent: gold when focused, subtle otherwise
            int accentAlpha = csFocused ? (int) (0xCC + hp * 0x33) : (int) (0x40 + hp * 0x40);
            int accentRGB = csFocused ? 0xFFCC00 : 0x888888;
            guiGraphics.fill(categorySearchBoxX, 22 + FIELD_HEIGHT - 2,
                    categorySearchBoxX + categorySearchBoxW, 22 + FIELD_HEIGHT,
                    (accentAlpha << 24) | accentRGB);

            // Placeholder text (rendered before super.render so the EditBox text draws over it)
            if (categorySearchFilter.isEmpty() && !csFocused) {
                guiGraphics.drawString(this.font, "Search...", categorySearchBoxX + 5,
                        22 + (FIELD_HEIGHT - 8) / 2, 0x555555, false);
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 200);

        // Category search dropdown (close if a modal overlay is open)
        if (categoryDropdownVisible && isAnyOverlayVisible()) {
            categoryDropdownVisible = false;
        }
        if (categoryDropdownVisible && !categoryDropdownSuggestions.isEmpty()) {
            int total = categoryDropdownSuggestions.size();
            int maxScroll = Math.max(0, total - MAX_DROPDOWN_ENTRIES);
            categoryDropdownScrollOffset = Math.max(0, Math.min(categoryDropdownScrollOffset, maxScroll));
            int visibleCount = Math.min(MAX_DROPDOWN_ENTRIES, total);
            boolean hasScroll = maxScroll > 0;

            int dropX = categorySearchBoxX;
            int dropY = 42;
            int dropW = categorySearchBoxW;
            int dropH = visibleCount * DROPDOWN_ENTRY_H + 4;
            int scrollBarW = hasScroll ? 4 : 0;
            int textAreaW = dropW - scrollBarW - (hasScroll ? 2 : 0);

            // Border + background
            guiGraphics.fill(dropX - 1, dropY - 1, dropX + dropW + 1, dropY + dropH + 1, 0xFF444444);
            guiGraphics.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0xFF1A1A1A);
            // Gold top accent line
            guiGraphics.fill(dropX, dropY, dropX + dropW, dropY + 1, 0xFFFFCC00);

            for (int i = 0; i < visibleCount; i++) {
                String sug = categoryDropdownSuggestions.get(categoryDropdownScrollOffset + i);
                int sugY = dropY + 2 + i * DROPDOWN_ENTRY_H;
                boolean sugHov = mouseX >= dropX && mouseX < dropX + dropW - scrollBarW
                        && mouseY >= sugY && mouseY < sugY + DROPDOWN_ENTRY_H;
                if (sugHov) guiGraphics.fill(dropX, sugY, dropX + textAreaW, sugY + DROPDOWN_ENTRY_H, 0x30FFCC00);
                int availW = textAreaW - 8;
                // The visible index is not an identity: scrolling the list puts a different
                // suggestion under the same row, and the label would carry on from wherever the
                // previous one had scrolled to. Pairing it with the text restarts the delay.
                String marqueeKey = i + "\0" + sug;
                categoryDropdownMarquee.draw(guiGraphics, this.font, marqueeKey, sug,
                        dropX + 4, sugY + 2, availW, sugY, sugY + DROPDOWN_ENTRY_H,
                        sugHov ? 0xFFFFFF : 0xBBBBBB, sugHov, true);
            }

            // Scrollbar
            if (hasScroll) {
                int barX = dropX + dropW - scrollBarW;
                int trackH = dropH - 2;
                guiGraphics.fill(barX, dropY + 1, barX + scrollBarW, dropY + dropH - 1, 0x30FFFFFF);
                int thumbH = Math.max(8, trackH * MAX_DROPDOWN_ENTRIES / total);
                int thumbY = dropY + 1 + (trackH - thumbH) * categoryDropdownScrollOffset / maxScroll;
                guiGraphics.fill(barX, thumbY, barX + scrollBarW, thumbY + thumbH, 0xAAFFCC00);
            }
        }

        for (CategoryTab tab : categoryTabs.values()) {
            if (tab.activeOverlay() != null) tab.activeOverlay().render(guiGraphics, this.font, mouseX, mouseY);
        }
        if (actionOverlay() != null) actionOverlay().render(guiGraphics, this.font, mouseX, mouseY);
        iconSearch.render(guiGraphics, this.font, mouseX, mouseY);
        // Lifted above the popups it can be opened from, so it never gets drawn under their content.
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 400);
        contextMenu.render(guiGraphics, this.font, mouseX, mouseY);
        guiGraphics.pose().popPose();
        modEntityPopup.render(guiGraphics, this.font, mouseX, mouseY);
        modStructurePopup.render(guiGraphics, this.font, mouseX, mouseY);
        modBiomePopup.render(guiGraphics, this.font, mouseX, mouseY);
        if (recipePopupVisible) renderRecipePopup(guiGraphics, mouseX, mouseY);
        if (lockActionsPopupVisible) renderLockActionsPopup(guiGraphics, mouseX, mouseY);
        spawnSourcesPopup.render(guiGraphics, this.font, mouseX, mouseY);
        tradeLevelsPopup.render(guiGraphics, this.font, mouseX, mouseY);
        interactionActionsPopup.render(guiGraphics, this.font, mouseX, mouseY);
        // Skip the popup while one of its pickers is up: text is batched and flushed after the
        // picker's panel fills, so drawing it underneath makes it bleed through the picker.
        if (!filterItemSearch.isVisible() && !filterTagSearch.isVisible()) {
            interactionItemsPopup.render(guiGraphics, this.font, mouseX, mouseY);
        }
        filterItemSearch.render(guiGraphics, this.font, mouseX, mouseY);
        filterTagSearch.render(guiGraphics, this.font, mouseX, mouseY);
        dimFilterPopup.render(guiGraphics, this.font, mouseX, mouseY);
        generationLimitPopup.render(guiGraphics, this.font, mouseX, mouseY);
        if (overridePopupVisible) renderOverridePopup(guiGraphics, mouseX, mouseY);
        guiGraphics.pose().popPose();

        // Tooltip rendering
        if (currentTooltipKey != null && currentTooltipText != null && !currentTooltipText.isEmpty()) {
            if (!currentTooltipKey.equals(hoveredTooltipKey)) {
                hoveredTooltipKey = currentTooltipKey;
                tooltipHoverStart = System.currentTimeMillis();
            }
            if (System.currentTimeMillis() - tooltipHoverStart >= TOOLTIP_DELAY_MS) {
                renderTooltip(guiGraphics, currentTooltipText, mouseX, mouseY);
            }
        } else {
            hoveredTooltipKey = null;
        }

    }

    /**
     * The editor's tooltip look. The wrapping and the box were a second copy of
     * {@code EditorTooltip}, down to the same three colours; the delay above is what this screen
     * still owns.
     */
    private void renderTooltip(GuiGraphics guiGraphics, String text, int mouseX, int mouseY) {
        EditorTooltip.draw(guiGraphics, this.font, text, mouseX, mouseY, this.width, this.height);
    }

    private static ItemStack getItemStack(String itemId) {
        try {
            ResourceLocation loc = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.get(loc);
            return item != null ? new ItemStack(item) : ItemStack.EMPTY;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    /**
     * Returns [workstation, result] for a recipe ID, cached for performance.
     */
    private ItemStack[] getRecipeInfo(String recipeId) {
        if (!recipeInfoBuilt) {
            recipeInfoBuilt = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Collection<RecipeHolder<?>> allCachedRecipes = AllRecipesCache.get();
                Collection<RecipeHolder<?>> recipes = allCachedRecipes.isEmpty()
                        ? mc.level.getRecipeManager().getRecipes()
                        : allCachedRecipes;
                for (RecipeHolder<?> holder : recipes) {
                    try {
                        Recipe<?> recipe = holder.value();
                        String id = holder.id().toString();
                        ItemStack result = recipe.getResultItem(mc.level.registryAccess());
                        ItemStack workstation = RecipeCardRenderer.resolveWorkstation(
                                RecipeTypeMetas.get(String.valueOf(
                                        BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()))));
                        recipeInfoCache.put(id, new ItemStack[]{workstation, result});
                    } catch (Exception ignored) {}
                }
            }
        }
        return recipeInfoCache.get(recipeId);
    }

    private void closeRecipePopup() {
        recipePopupVisible = false;
        recipePopupId = null;
        popupMarqueeLastId = null;
    }

    private void renderRecipePopup(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (recipePopupId == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Recipe<?> recipe = null;
        Collection<RecipeHolder<?>> allCached = AllRecipesCache.get();
        Collection<RecipeHolder<?>> allRecipes = allCached.isEmpty()
                ? mc.level.getRecipeManager().getRecipes()
                : allCached;
        for (RecipeHolder<?> r : allRecipes) {
            if (r.id().toString().equals(recipePopupId)) { recipe = r.value(); break; }
        }
        if (recipe == null) { recipePopupVisible = false; return; }

        ItemStack result = recipe.getResultItem(mc.level.registryAccess());
        String typeId = String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()));
        RecipeTypeMeta typeMeta = RecipeTypeMetas.get(typeId);
        ItemStack workstation = RecipeCardRenderer.resolveWorkstation(typeMeta);
        int typeColor = typeMeta.accentColor();
        String typeName = typeMeta.nameLangKey().isEmpty()
                ? typeMeta.displayFallback()
                : Component.translatable(typeMeta.nameLangKey()).getString();

        // The popup shows the very card the detail column draws, only wider. It used to draw a
        // grid of its own, which is how one shaped recipe could come out looking one way here
        // and another way over there.
        Map<String, Set<FluidRecipeScanner.Position>> fluidSides =
                FluidRecipeIndex.fluidsIn(recipePopupId);
        List<RecipeFluids.Ref> fluids = RecipeFluids.ingredientRow(fluidSides);
        List<String> fluidOutputs = RecipeFluids.definiteOutputs(fluidSides);
        String fluidResult = fluidOutputs.isEmpty() ? "" : fluidOutputs.get(0);

        RecipeShape shape = RecipeShape.of(recipe, fluids.size());

        // Layout
        int pad = 14;
        int popupW = Math.max(RecipeCardRenderer.cardWidth(shape.layout()) + pad * 2, 240);
        int headerH = 40;
        int contentH = shape.layout().cardHeight();
        int popupH = headerH + contentH + pad + 6;

        int popupX = this.width / 2 - popupW / 2;
        int popupY = this.height / 2 - popupH / 2;

        cachedPopupX = popupX;
        cachedPopupY = popupY;
        cachedPopupW = popupW;
        cachedPopupH = popupH;

        // Dim background
        guiGraphics.fill(0, 0, this.width, this.height, 0x88000000);

        // Shadow + border + background
        guiGraphics.fill(popupX + 3, popupY + 3, popupX + popupW + 3, popupY + popupH + 3, 0x50000000);
        guiGraphics.fill(popupX - 1, popupY - 1, popupX + popupW + 1, popupY + popupH + 1, 0xFF333333);
        guiGraphics.fill(popupX, popupY, popupX + popupW, popupY + popupH, 0xFF1A1A1A);

        // Recipe type accent bar
        guiGraphics.fill(popupX, popupY, popupX + popupW, popupY + 3, typeColor);

        // Header: workstation icon + type name
        int hdrY = popupY + 8;
        int hdrX = popupX + pad;
        if (!workstation.isEmpty()) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(hdrX, hdrY - 1, 0);
            guiGraphics.pose().scale(0.75f, 0.75f, 1.0f);
            guiGraphics.renderItem(workstation, 0, 0);
            guiGraphics.pose().popPose();
            hdrX += 14;
        }
        guiGraphics.drawString(this.font, typeName, hdrX, hdrY + 1, 0xFFFFFF, false);

        // ESC hint
        String escText = "[ESC]";
        guiGraphics.drawString(this.font, escText, popupX + popupW - pad - this.font.width(escText), hdrY + 1, 0x444444, false);

        // Recipe ID (with marquee scroll on hover if too wide)
        int idMaxW = popupW - pad * 2;
        int idTextW = (int)(this.font.width(recipePopupId) * SMALL_SCALE);
        int idX = popupX + pad;
        int idY = hdrY + 15;
        int idH = (int)(this.font.lineHeight * SMALL_SCALE);
        boolean isIdHovered = mouseX >= idX && mouseX < idX + idMaxW && mouseY >= idY && mouseY < idY + idH + 2;
        if (idTextW <= idMaxW) {
            drawSmallText(guiGraphics, recipePopupId, idX, idY, 0x666666);
        } else {
            // Track hover state for marquee
            if (isIdHovered && !popupIdHovered) {
                popupIdHovered = true;
                popupMarqueeStartTime = System.currentTimeMillis();
                popupMarqueeLastId = recipePopupId;
            } else if (!isIdHovered) {
                popupIdHovered = false;
            }
            float scrollOff = 0;
            int overflow = idTextW - idMaxW;
            if (isIdHovered) {
                long elapsed = System.currentTimeMillis() - popupMarqueeStartTime;
                if (elapsed > CARD_MARQUEE_DELAY_MS) {
                    float t = (elapsed - CARD_MARQUEE_DELAY_MS) / 1000.0f;
                    float cycle = overflow / CARD_MARQUEE_SPEED;
                    float phase = t % (cycle * 2);
                    scrollOff = phase <= cycle ? (phase / cycle) * overflow : (2 - phase / cycle) * overflow;
                }
            }
            guiGraphics.enableScissor(idX, idY, idX + idMaxW, idY + idH + 2);
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(idX - scrollOff, idY, 0);
            guiGraphics.pose().scale(SMALL_SCALE, SMALL_SCALE, 1.0f);
            guiGraphics.drawString(this.font, recipePopupId, 0, 0, 0x666666, false);
            guiGraphics.pose().popPose();
            guiGraphics.disableScissor();
        }

        // Separator
        int sepY = popupY + headerH - 1;
        guiGraphics.fill(popupX + pad - 2, sepY, popupX + popupW - pad + 2, sepY + 1, 0xFF333333);

        // Content: one card, drawn by the shared renderer
        int cardX = popupX + pad;
        int cardY = popupY + headerH + 6;
        int cardW = popupW - pad * 2;
        RecipeCardRenderer.render(guiGraphics, this.font, shape, result, fluidResult, fluids,
                typeId, recipePopupId, cardX, cardY, cardW, false, false);

        // The card draws items and no names, so a slot has to answer for itself on hover.
        ItemStack hoveredSlot = RecipeCardRenderer.stackAt(shape, result, cardX, cardY, cardW,
                mouseX, mouseY);
        if (!hoveredSlot.isEmpty()) {
            renderTooltip(guiGraphics, hoveredSlot.getHoverName().getString() + "\n§8"
                    + BuiltInRegistries.ITEM.getKey(hoveredSlot.getItem()), mouseX, mouseY);
        } else {
            String hoveredFluid = RecipeCardRenderer.fluidAt(shape, fluidResult, fluids,
                    cardX, cardY, cardW, mouseX, mouseY);
            if (!hoveredFluid.isEmpty()) {
                renderTooltip(guiGraphics, FluidIcon.nameOf(hoveredFluid) + "\n§8" + hoveredFluid,
                        mouseX, mouseY);
            }
        }
    }

    // =============================================
    // LOCK ACTIONS POPUP
    // =============================================

    /**
     * The per-row action map of whichever tab is asking, or null where narrowing an entry to
     * some actions would mean nothing.
     *
     * <p>Was a switch on three tab indices, which is why a fourth rich tab had no way in. Mod
     * exceptions stay out deliberately, exactly as that switch left them out: an exception
     * carves a hole in the mods category rather than locking anything, so it has no action to
     * narrow.
     */
    private Map<Integer, List<String>> getLockActionsMapForTab(int tab) {
        CategoryTab categoryTab = sectionAt(tab);
        if (!(categoryTab instanceof RichEntryCategoryTab<?> rich)) return null;
        if ("historystages:mod_exceptions".equals(categoryTab.categoryId())) return null;
        return rich.lockActionsByIndex();
    }

    /**
     * The action vocabulary of the tab's category — ten for items, tags and mods, seven for
     * fluids, whatever an addon declared for its own.
     */
    private List<String> lockActionsForTab(int tab) {
        CategoryTab categoryTab = sectionAt(tab);
        if (categoryTab != null) {
            LockCategory<?> category = LockCategories.byId(categoryTab.categoryId());
            if (category != null) return category.lockActions();
        }
        return net.bananemdnsa.historystages.api.lock.LockActions.ITEM;
    }

    /**
     * The grouped popup layout with every action the asking tab does not offer removed, and any
     * group left empty dropped entirely. One layout table serves all categories instead of one
     * table per vocabulary.
     */
    private List<String[]> lockActionGroupsForPopup() {
        return LockActionGroups.forVocabulary(lockActionsForTab(lockActionsPopupTab));
    }

    private void openLockActionsPopup(int tab, int idx) {
        lockActionsPopupTab = tab;
        lockActionsPopupIdx = idx;
        Map<Integer, List<String>> map = getLockActionsMapForTab(tab);
        if (map != null && map.containsKey(idx)) {
            lockActionsPopupCurrent = new ArrayList<>(map.get(idx));
        } else {
            // All actions locked by default
            lockActionsPopupCurrent = new ArrayList<>(lockActionsForTab(tab));
        }
        lockActionsPopupVisible = true;
    }

    private void saveLockActionsPopup() {
        Map<Integer, List<String>> map = getLockActionsMapForTab(lockActionsPopupTab);
        if (map == null) return;
        // If all actions are selected → remove from map (null = all locked = default, no JSON bloat)
        boolean allLocked =
                lockActionsPopupCurrent.size() == lockActionsForTab(lockActionsPopupTab).size();
        if (allLocked) {
            map.remove(lockActionsPopupIdx);
        } else {
            map.put(lockActionsPopupIdx, new ArrayList<>(lockActionsPopupCurrent));
        }
        hasChanges = true;
        lockActionsPopupVisible = false;
    }

    // Layout constants for the popup
    private static final int LP_PAD          = 8;
    private static final int LP_WIDTH        = 232;
    private static final int LP_COLS         = 3;
    private static final int LP_HEADER_H     = 18;   // title block (title + underline)
    private static final int LP_HINT_H       = 10;
    private static final int LP_GROUP_HEAD_H = 10;
    private static final int LP_TOGGLE_H     = 14;
    private static final int LP_TOGGLE_GAP   = 2;
    private static final int LP_GROUP_GAP    = 5;
    private static final int LP_DESC_H       = 11;
    private static final int LP_FOOTER_H     = 20;

    private boolean handleLockActionsPopupClick(double mouseX, double mouseY, int button) {
        int popupW = cachedLockPopupW, popupH = cachedLockPopupH;
        int popupX = cachedLockPopupX, popupY = cachedLockPopupY;
        if (popupW == 0) return true; // not yet rendered

        int btnH = 14;
        int btnY = popupY + popupH - btnH - 6;

        // Done button (right, gold)
        int doneW = computeLockDoneBtnWidth();
        int doneX = popupX + popupW - doneW - LP_PAD;
        if (mouseX >= doneX && mouseX < doneX + doneW && mouseY >= btnY && mouseY < btnY + btnH) {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            saveLockActionsPopup();
            return true;
        }

        // All button (left)
        int qBtnW = computeLockQuickBtnWidth();
        int allX = popupX + LP_PAD;
        if (mouseX >= allX && mouseX < allX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH) {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            lockActionsPopupCurrent = new ArrayList<>(lockActionsForTab(lockActionsPopupTab));
            return true;
        }

        // None button (next to All)
        int noneX = allX + qBtnW + 3;
        if (mouseX >= noneX && mouseX < noneX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH) {
            Minecraft.getInstance().getSoundManager()
                    .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            lockActionsPopupCurrent.clear();
            return true;
        }

        // Toggle clicks — walk the grouped layout
        int curY = popupY + LP_HEADER_H + LP_HINT_H + 3;
        int toggleW = (popupW - 2 * LP_PAD - (LP_COLS - 1) * 3) / LP_COLS;
        for (String[] group : lockActionGroupsForPopup()) {
            curY += LP_GROUP_HEAD_H;
            int actionCount = group.length - 1;
            for (int j = 0; j < actionCount; j++) {
                String action = group[j + 1];
                int col = j % LP_COLS;
                int row = j / LP_COLS;
                int tx = popupX + LP_PAD + col * (toggleW + 3);
                int ty = curY + row * (LP_TOGGLE_H + LP_TOGGLE_GAP);
                if (mouseX >= tx && mouseX < tx + toggleW && mouseY >= ty && mouseY < ty + LP_TOGGLE_H) {
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    if (lockActionsPopupCurrent.contains(action)) {
                        lockActionsPopupCurrent.remove(action);
                    } else {
                        lockActionsPopupCurrent.add(action);
                    }
                    return true;
                }
            }
            int rowsInGroup = (actionCount + LP_COLS - 1) / LP_COLS;
            curY += rowsInGroup * LP_TOGGLE_H + (rowsInGroup - 1) * LP_TOGGLE_GAP + LP_GROUP_GAP;
        }

        // Click outside closes (and discards changes)
        if (mouseX < popupX || mouseX > popupX + popupW || mouseY < popupY || mouseY > popupY + popupH) {
            lockActionsPopupVisible = false;
        }
        return true;
    }

    private void renderLockActionsPopup(GuiGraphics g, int mouseX, int mouseY) {
        // Compute popup height from group structure
        int contentH = 0;
        for (String[] group : lockActionGroupsForPopup()) {
            int actionCount = group.length - 1;
            int rowsInGroup = (actionCount + LP_COLS - 1) / LP_COLS;
            contentH += LP_GROUP_HEAD_H + rowsInGroup * LP_TOGGLE_H + (rowsInGroup - 1) * LP_TOGGLE_GAP + LP_GROUP_GAP;
        }
        contentH -= LP_GROUP_GAP; // no gap after last group

        int popupW = computeLockPopupWidth();
        int popupH = LP_HEADER_H + LP_HINT_H + 3 + contentH + LP_DESC_H + LP_FOOTER_H;
        int popupX = this.width / 2 - popupW / 2;
        int popupY = this.height / 2 - popupH / 2;

        cachedLockPopupX = popupX;
        cachedLockPopupY = popupY;
        cachedLockPopupW = popupW;
        cachedLockPopupH = popupH;

        // Backdrop dim
        g.fill(0, 0, this.width, this.height, 0x88000000);
        // Drop shadow
        g.fill(popupX + 3, popupY + 3, popupX + popupW + 3, popupY + popupH + 3, 0x50000000);
        // Outer border + inner background (matches editor dialog style)
        g.fill(popupX - 1, popupY - 1, popupX + popupW + 1, popupY + popupH + 1, 0xFF333333);
        g.fill(popupX, popupY, popupX + popupW, popupY + popupH, 0xFF1A1A1A);

        // Title with subtle gold underline
        g.drawCenteredString(this.font,
                Component.translatable("editor.historystages.lock_actions.title"),
                popupX + popupW / 2, popupY + 5, 0xFFFFFFFF);
        int accentW = 40;
        int accentX = popupX + (popupW - accentW) / 2;
        g.fill(accentX, popupY + 15, accentX + accentW, popupY + 16, 0xFFFFCC00);

        // Hint
        g.drawCenteredString(this.font,
                Component.translatable("editor.historystages.lock_actions.hint"),
                popupX + popupW / 2, popupY + LP_HEADER_H, 0x888888);

        // Render groups
        int curY = popupY + LP_HEADER_H + LP_HINT_H + 3;
        int toggleW = (popupW - 2 * LP_PAD - (LP_COLS - 1) * 3) / LP_COLS;
        String hoveredAction = null;

        for (String[] group : lockActionGroupsForPopup()) {
            String groupKey = group[0];
            Component groupLabel = Component.translatable("editor.historystages.lock_actions.group." + groupKey);

            // Group header — subtle label with thin separator line
            g.drawString(this.font, groupLabel, popupX + LP_PAD, curY + 1, 0xCCCCCC, false);
            int textW = this.font.width(groupLabel);
            int sepX = popupX + LP_PAD + textW + 5;
            int sepY = curY + 4;
            g.fill(sepX, sepY, popupX + popupW - LP_PAD, sepY + 1, 0xFF2E2E2E);
            curY += LP_GROUP_HEAD_H;

            int actionCount = group.length - 1;
            for (int j = 0; j < actionCount; j++) {
                String action = group[j + 1];
                int col = j % LP_COLS;
                int row = j / LP_COLS;
                int tx = popupX + LP_PAD + col * (toggleW + 3);
                int ty = curY + row * (LP_TOGGLE_H + LP_TOGGLE_GAP);

                boolean blocked = lockActionsPopupCurrent.contains(action);
                boolean hovered = mouseX >= tx && mouseX < tx + toggleW && mouseY >= ty && mouseY < ty + LP_TOGGLE_H;
                if (hovered) hoveredAction = action;

                // Background
                int bg = blocked
                        ? (hovered ? 0x40FFCC00 : 0x25FFCC00)
                        : (hovered ? 0x25FFFFFF : 0x10FFFFFF);
                g.fill(tx, ty, tx + toggleW, ty + LP_TOGGLE_H, bg);

                // Bottom accent line
                int accent = blocked
                        ? (hovered ? 0xFFFFCC00 : 0xB0FFCC00)
                        : (hovered ? 0x40FFFFFF : 0x20FFFFFF);
                g.fill(tx, ty + LP_TOGGLE_H - 1, tx + toggleW, ty + LP_TOGGLE_H, accent);

                // Indicator dot + label
                int textColor = blocked ? 0xFFFFFF : 0x999999;
                int dotColor  = blocked ? 0xFFFFCC00 : 0xFF555555;
                g.fill(tx + 4, ty + 6, tx + 7, ty + 9, dotColor);
                g.drawString(this.font,
                        Component.translatable("editor.historystages.lock_actions.action." + action),
                        tx + 10, ty + 3, textColor, false);
            }
            int rowsInGroup = (actionCount + LP_COLS - 1) / LP_COLS;
            curY += rowsInGroup * LP_TOGGLE_H + (rowsInGroup - 1) * LP_TOGGLE_GAP + LP_GROUP_GAP;
        }

        // Description line — shows hovered action's description, or a generic hint
        int descY = popupY + popupH - LP_FOOTER_H - LP_DESC_H + 1;
        g.fill(popupX + LP_PAD, descY - 1, popupX + popupW - LP_PAD, descY, 0xFF2E2E2E);
        Component descText;
        int descColor;
        if (hoveredAction != null) {
            descText = Component.translatable("editor.historystages.lock_actions.action." + hoveredAction)
                    .append(Component.literal(" — "))
                    .append(Component.translatable("editor.historystages.lock_actions.desc." + hoveredAction));
            descColor = 0xCCCCCC;
        } else {
            int blockedCount = lockActionsPopupCurrent.size();
            descText = Component.translatable("editor.historystages.lock_actions.status",
                    blockedCount, lockActionsForTab(lockActionsPopupTab).size());
            descColor = 0x888888;
        }
        g.drawCenteredString(this.font, descText, popupX + popupW / 2, descY + 2, descColor);

        // Footer buttons
        int btnH = 14;
        int btnY = popupY + popupH - btnH - 6;
        int qBtnW = computeLockQuickBtnWidth();

        // All
        int allX = popupX + LP_PAD;
        boolean allHov = mouseX >= allX && mouseX < allX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(allX, btnY, allX + qBtnW, btnY + btnH, allHov ? 0x25FFFFFF : 0x10FFFFFF);
        g.fill(allX, btnY + btnH - 1, allX + qBtnW, btnY + btnH, allHov ? 0x80FFFFFF : 0x40FFFFFF);
        g.drawCenteredString(this.font,
                Component.translatable("editor.historystages.lock_actions.btn_all"),
                allX + qBtnW / 2, btnY + 3, allHov ? 0xFFFFFF : 0xCCCCCC);

        // None
        int noneX = allX + qBtnW + 3;
        boolean noneHov = mouseX >= noneX && mouseX < noneX + qBtnW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(noneX, btnY, noneX + qBtnW, btnY + btnH, noneHov ? 0x25FFFFFF : 0x10FFFFFF);
        g.fill(noneX, btnY + btnH - 1, noneX + qBtnW, btnY + btnH, noneHov ? 0x80FFFFFF : 0x40FFFFFF);
        g.drawCenteredString(this.font,
                Component.translatable("editor.historystages.lock_actions.btn_none"),
                noneX + qBtnW / 2, btnY + 3, noneHov ? 0xFFFFFF : 0xCCCCCC);

        // Done (gold accent)
        int doneW = computeLockDoneBtnWidth();
        int doneX = popupX + popupW - doneW - LP_PAD;
        boolean doneHov = mouseX >= doneX && mouseX < doneX + doneW && mouseY >= btnY && mouseY < btnY + btnH;
        g.fill(doneX, btnY, doneX + doneW, btnY + btnH, doneHov ? 0x50FFCC00 : 0x25FFCC00);
        g.fill(doneX, btnY + btnH - 1, doneX + doneW, btnY + btnH, doneHov ? 0xFFFFCC00 : 0x80FFCC00);
        g.drawCenteredString(this.font,
                Component.translatable("editor.historystages.lock_actions.btn_done"),
                doneX + doneW / 2, btnY + 3, doneHov ? 0xFFFFFF : 0xEEEEEE);
    }

    /**
     * Popup width grown to fit every piece of text it holds — title, hint, toggle labels, the
     * widest hover-description/status line, and the footer button row — instead of shrinking text
     * into a fixed {@link #LP_WIDTH}.
     */
    private int computeLockPopupWidth() {
        int maxToggleW = 0;
        int maxLineW = 0;
        List<String> vocabulary = lockActionsForTab(lockActionsPopupTab);
        for (String action : vocabulary) {
            Component name = Component.translatable("editor.historystages.lock_actions.action." + action);
            Component desc = Component.translatable("editor.historystages.lock_actions.desc." + action);
            maxToggleW = Math.max(maxToggleW, this.font.width(name));
            Component combined = name.copy().append(Component.literal(" — ")).append(desc);
            maxLineW = Math.max(maxLineW, this.font.width(combined));
        }
        Component status = Component.translatable("editor.historystages.lock_actions.status",
                vocabulary.size(), vocabulary.size());
        maxLineW = Math.max(maxLineW, this.font.width(status));
        // Centered header lines must fit too.
        maxLineW = Math.max(maxLineW, this.font.width(Component.translatable("editor.historystages.lock_actions.title")));
        maxLineW = Math.max(maxLineW, this.font.width(Component.translatable("editor.historystages.lock_actions.hint")));

        int neededToggleW = maxToggleW + 14; // dot + gap (10px) + right margin (4px)
        int neededFromGrid = 2 * LP_PAD + LP_COLS * neededToggleW + (LP_COLS - 1) * 3;
        int neededFromLine = maxLineW + 2 * LP_PAD;
        // Footer: [All][None] on the left, [Done] on the right, with a small gap between the groups.
        int neededFromFooter = 2 * LP_PAD + 2 * computeLockQuickBtnWidth() + 3 + 8 + computeLockDoneBtnWidth();
        int needed = Math.max(LP_WIDTH, Math.max(Math.max(neededFromGrid, neededFromLine), neededFromFooter));
        // Never wider than the screen: on a very small GUI the box would otherwise spill off both
        // edges (it is screen-centered). Degrades to slight internal overflow, not an off-screen box.
        return Math.min(needed, this.width - 8);
    }

    /** Width for the "All"/"None" footer buttons, grown to fit whichever label is wider. */
    private int computeLockQuickBtnWidth() {
        int w = this.font.width(Component.translatable("editor.historystages.lock_actions.btn_all"));
        w = Math.max(w, this.font.width(Component.translatable("editor.historystages.lock_actions.btn_none")));
        return Math.max(34, w + 10);
    }

    /** Width for the "Done" footer button, grown to fit its label. */
    private int computeLockDoneBtnWidth() {
        int w = this.font.width(Component.translatable("editor.historystages.lock_actions.btn_done"));
        return Math.max(48, w + 10);
    }

    // ===== Spawn sources popup =====

    /** Removes the entry at removedIdx and shifts all higher indices down by 1. */
    private static void shiftLockActionsMap(Map<Integer, List<String>> map, int removedIdx) {
        map.remove(removedIdx);
        Map<Integer, List<String>> shifted = new HashMap<>();
        for (var e : map.entrySet()) {
            int key = e.getKey();
            shifted.put(key > removedIdx ? key - 1 : key, e.getValue());
        }
        map.clear();
        map.putAll(shifted);
    }

    private static void shiftStringMap(Map<Integer, String> map, int removedIdx) {
        map.remove(removedIdx);
        Map<Integer, String> shifted = new HashMap<>();
        for (var e : map.entrySet()) {
            int key = e.getKey();
            shifted.put(key > removedIdx ? key - 1 : key, e.getValue());
        }
        map.clear();
        map.putAll(shifted);
    }

    // =============================================

    private void drawSmallText(GuiGraphics guiGraphics, String text, int x, int y, int color) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);
        guiGraphics.pose().scale(SMALL_SCALE, SMALL_SCALE, 1.0f);
        guiGraphics.drawString(this.font, text, 0, 0, color, false);
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (modEntityPopup.isVisible()) { return modEntityPopup.mouseClicked(mouseX, mouseY); }
        if (modStructurePopup.isVisible()) { return modStructurePopup.mouseClicked(mouseX, mouseY); }
        if (modBiomePopup.isVisible()) { return modBiomePopup.mouseClicked(mouseX, mouseY); }
        if (lockActionsPopupVisible) { return handleLockActionsPopupClick(mouseX, mouseY, button); }
        if (spawnSourcesPopup.isVisible()) { return spawnSourcesPopup.mouseClicked(mouseX, mouseY); }
        if (tradeLevelsPopup.isVisible()) { return tradeLevelsPopup.mouseClicked(mouseX, mouseY); }
        if (interactionActionsPopup.isVisible()) { return interactionActionsPopup.mouseClicked(mouseX, mouseY); }
        if (filterItemSearch.isVisible()) { if (filterItemSearch.mouseClicked(mouseX, mouseY)) return true; }
        if (filterTagSearch.isVisible()) { if (filterTagSearch.mouseClicked(mouseX, mouseY)) return true; }
        // The row menu sits on top of the popup, so let it consume the click first (its own
        // handler runs further down).
        if (interactionItemsPopup.isVisible() && !contextMenu.isVisible()) {
            boolean handled = interactionItemsPopup.mouseClicked(mouseX, mouseY, button);
            // Closing the popup drops the edit context so a later init() doesn't re-open it.
            if (!interactionItemsPopup.isVisible()) interactionItemsTarget = null;
            return handled;
        }
        if (dimFilterPopup.isVisible()) { return dimFilterPopup.mouseClicked(mouseX, mouseY); }
        if (generationLimitPopup.isVisible()) { return generationLimitPopup.mouseClicked(mouseX, mouseY); }
        if (overridePopupVisible) { return handleOverridePopupClick(mouseX, mouseY, button); }
        if (recipePopupVisible) {
            // Click outside popup closes everything
            if (mouseX < cachedPopupX || mouseX > cachedPopupX + cachedPopupW
                    || mouseY < cachedPopupY || mouseY > cachedPopupY + cachedPopupH) {
                closeRecipePopup();
                hideCategoryPickers();
                return true;
            }
            return true; // consume clicks inside popup
        }
        if (contextMenu.isVisible()) {
            contextMenu.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        if (actionOverlay() != null) return actionOverlay().mouseClicked(mouseX, mouseY);
        if (anyCategoryPicker(pk -> pk.mouseClicked(mouseX, mouseY))) return true;

        // The active tab, after the context menu and the pickers and before this screen's own
        // handling. Earlier and a click on an open picker lands in the content behind it; later
        // and a focused field never sees ESC, because this screen has already acted on it.
        CategoryTab inputTab = categoryTabs.get(activeTab);
        if (inputTab != null && inputTab.mouseClicked(inputContext(mouseX, mouseY), button))
            return true;
        // The section bar sits above the list, so it is checked before anything that measures
        // from listTop() — which already has the bar's strip subtracted out of it.
        if (button == 0 && sectionBarClicked(mouseX, mouseY)) return true;
        if (iconSearch.isVisible()) { if (iconSearch.mouseClicked(mouseX, mouseY)) return true; }

        // Unfocus/clear category search when clicking outside the box + dropdown
        if (categorySearchBox != null && categorySearchBox.isFocused()) {
            boolean inSearchBox = mouseX >= categorySearchBoxX && mouseX < categorySearchBoxX + categorySearchBoxW
                    && mouseY >= 22 && mouseY < 22 + FIELD_HEIGHT;
            int dropH = Math.min(MAX_DROPDOWN_ENTRIES, categoryDropdownSuggestions.size()) * DROPDOWN_ENTRY_H + 4;
            boolean inDropdown = categoryDropdownVisible && mouseX >= categorySearchBoxX
                    && mouseX < categorySearchBoxX + categorySearchBoxW
                    && mouseY >= 42 && mouseY < 42 + dropH;
            if (!inSearchBox && !inDropdown) {
                categoryDropdownVisible = false;
                categorySearchFilter = "";
                categorySearchBox.setValue("");
                categorySearchBox.setFocused(false);
            }
        }

        // Category search dropdown clicks
        if (categoryDropdownVisible && !categoryDropdownSuggestions.isEmpty()) {
            int dropX = categorySearchBoxX;
            int dropY = 42;
            int dropW = categorySearchBoxW;
            int visibleRows = Math.min(MAX_DROPDOWN_ENTRIES, categoryDropdownSuggestions.size());
            int dropH = visibleRows * DROPDOWN_ENTRY_H + 4;
            if (mouseX >= dropX && mouseX < dropX + dropW && mouseY >= dropY && mouseY < dropY + dropH) {
                int visIdx = (int) (mouseY - dropY - 2) / DROPDOWN_ENTRY_H;
                int idx = visIdx + categoryDropdownScrollOffset;
                if (idx >= 0 && idx < categoryDropdownSuggestions.size()) {
                    String target = categoryDropdownSuggestions.get(idx);
                    // Scroll main list so the target entry is visible
                    List<String> list = getActiveList();
                    int targetIdx = list.indexOf(target);
                    if (targetIdx >= 0) {
                        int targetY = targetIdx * (CARD_HEIGHT + CARD_GAP);
                        scrollOffset = Math.max(0, Math.min(maxScroll, targetY));
                        smoothScrollOffset.set((float) scrollOffset);
                    }
                    Minecraft.getInstance().getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                categoryDropdownVisible = false;
                categorySearchFilter = "";
                if (categorySearchBox != null) categorySearchBox.setValue("");
                return true;
            }
        }

        if (mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
            // Tab scroll arrow clicks
            if (maxTabScroll > 0) {
                int tabAreaLeft = 20;
                int tabAreaRight = this.width - 20;
                if (tabScrollOffset > 0 && mouseX >= tabAreaLeft && mouseX < tabAreaLeft + TAB_ARROW_WIDTH) {
                    tabScrollOffset = Math.max(0, tabScrollOffset - 40);
                    return true;
                }
                if (tabScrollOffset < maxTabScroll && mouseX >= tabAreaRight - TAB_ARROW_WIDTH && mouseX < tabAreaRight) {
                    tabScrollOffset = Math.min(maxTabScroll, tabScrollOffset + 40);
                    return true;
                }
            }
            for (int i = 0; i < tabCount(); i++) {
                int scrolledTabX = tabX[i] - tabScrollOffset;
                if (mouseX >= scrolledTabX && mouseX < scrolledTabX + tabW[i]) { Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)); switchTab(i); return true; }
            }
        }

        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        int listTop = listTop();
        int listBottom = listBottom();
        int contentLeft = contentLeft();
        int contentRight = contentRight();

        // Scrollbar drag start
        if (button == 0 && maxScroll > 0 && mouseX >= contentRight + 1 && mouseX <= contentRight + 8
                && mouseY >= listTop && mouseY <= listBottom) {
            scrollBarDragging = true;
            updateScrollFromMouse(mouseY, listTop, listBottom);
            return true;
        }

        if (mouseX < contentLeft - 10 || mouseX > contentRight + 10 || mouseY < listTop || mouseY > listBottom)
            return false;

        List<String> list = getActiveList();
        // Which row was hit, asked once instead of walked. A tab that drew its own content is the
        // only thing that knows where its rows ended up — the host's arithmetic describes the rows
        // the host drew, and on the merchant levels there are none to describe.
        TabInputContext hitCtx = inputContext(mouseX, mouseY);
        CategoryTab hitTab = categoryTabs.get(activeTab);
        int hitRow = activeTabDrewItself
                ? (hitTab == null ? -1 : hitTab.rowAt(hitCtx))
                : rowList(activeTab).rowAt(hitCtx, list.size());

        {
            int i = hitRow;
            if (i >= 0 && i < list.size()) {
                if (button == 0 && isTab(activeTab, CAT_RECIPES)) {
                    // Left-click on recipe card: show recipe detail popup (view-only)
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    recipePopupId = list.get(i);
                    recipePopupVisible = true;
                    return true;
                }
                if (button == 1) {
                    final int entryIdx = i;
                    final String entryValue = list.get(i);
                    final int tabIdx = activeTab;
                    contextMenu = new ContextMenu();
                    if (isTab(tabIdx, CAT_ITEMS)) {
                        contextMenu.addEntry(Component.translatable("editor.historystages.context.edit_nbt").getString(),
                                () -> openNbtEditScreen(entryIdx, entryValue));
                    }
                    if (isTab(tabIdx, CAT_TAGS)) {
                        contextMenu.addEntry(Component.translatable("editor.historystages.context.edit_nbt").getString(),
                                () -> openTagNbtEditScreen(entryIdx, entryValue));
                    }
                    // Fluids belong here too: they carry an action list and text overrides, only
                    // no NBT. Asked by category rather than by tab index, which is exactly what
                    // left them out while the tab sat at the end of the strip.
                    if (getLockActionsMapForTab(tabIdx) != null) {
                        contextMenu.addEntry(Component.translatable("editor.historystages.context.lock_actions").getString(),
                                () -> openLockActionsPopup(tabIdx, entryIdx));
                    }
                    if (isAnyTab(tabIdx, CAT_ITEMS, CAT_FLUIDS, CAT_TAGS, CAT_MODS) && hasReplaceAxis()) {
                        contextMenu.addEntry(Component.translatable("editor.historystages.context.text_override").getString(),
                                () -> openOverridePopup(tabIdx, entryIdx));
                    }
                    if (isTab(tabIdx, CAT_SPAWN)) {
                        contextMenu.addEntry(Component.translatable("editor.historystages.context.spawn_sources").getString(),
                                () -> spawnSourcesPopup.show(entryValue, editSpawnlockSources.get(entryValue)));
                        contextMenu.addEntry(Component.translatable("editor.historystages.context.dimension_filter").getString(),
                                () -> dimFilterPopup.show(entryValue, editSpawnlockDimensions.get(entryValue),
                                        this.width / 2, this.height / 2));
                    }
                    if (isTab(tabIdx, CAT_INTERACT)) {
                        contextMenu.addEntry(Component.translatable("editor.historystages.context.interaction_actions").getString(),
                                () -> interactionActionsPopup.show(entryValue, editInteractionlockActions.get(entryValue)));
                        contextMenu.addEntry(Component.translatable("editor.historystages.context.interaction_items").getString(),
                                () -> {
                                    interactionItemsTarget = entryValue;
                                    interactionItemsPopup.show(entryValue);
                                });
                    }
                    // World generation is global and baked into the chunk, so an individual
                    // (per-player) stage has no coherent answer — no settings offered there.
                    if (isTab(tabIdx, CAT_STRUCTURES) && !isIndividual) {
                        contextMenu.addEntry(Component.translatable("editor.historystages.context.generation").getString(),
                                () -> generationLimitPopup.show(entryValue, generationRuleFor(entryValue),
                                        this.width / 2, this.height / 2));
                    }
                    if (isTab(tabIdx, CAT_MODS)) {
                        contextMenu.addEntry(Component.translatable("editor.historystages.edit").getString(),
                                () -> {
                                    pendingModId = entryValue;
                                    pendingModDisplayName = modPickerForNames.getDisplayName(entryValue);
                                    editingModId = entryValue;
                                    boolean entityShown = modEntityPopup.showForMod(pendingModId,
                                            pendingModDisplayName, this.width / 2, this.height / 2, editSpawnlock,
                                            editAttacklock, editInteractionlock);
                                    if (!entityShown && !showModStructurePopup()) {
                                        // Nothing to edit for this mod — surface the reason instead of
                                        // silently doing nothing.
                                        net.minecraft.client.gui.Gui gui = Minecraft.getInstance().gui;
                                        if (gui != null)
                                            gui.getChat().addMessage(Component.translatable(
                                                    "editor.historystages.edit.nothing_to_edit",
                                                    pendingModDisplayName));
                                        editingModId = null;
                                    }
                                });
                    }
                    if (isTab(tabIdx, CAT_EXCEPTIONS)) {
                        contextMenu.addEntry(Component.translatable("editor.historystages.context.edit_nbt").getString(),
                                () -> openModExceptionNbtEditScreen(entryIdx, entryValue));
                    }
                    // Only the offer section: a profession is an id and a level is a switch, and
                    // neither has a stack to match a criterion against. The criterion is what
                    // tells one enchanted book from another inside a single trade.
                    if (isTab(tabIdx, CAT_TRADES)) {
                        contextMenu.addEntry(Component.translatable("editor.historystages.context.edit_nbt").getString(),
                                () -> openTradeNbtEditScreen(entryIdx));
                    }
                    // A profession gates every level unless it says otherwise, and saying
                    // otherwise is the only way to reach one profession's experts without
                    // reaching every other profession's along with them.
                    if (isTab(tabIdx, CAT_TRADE_PROFESSIONS)) {
                        contextMenu.addEntry(Component.translatable("editor.historystages.context.trade_levels").getString(),
                                () -> tradeLevelsPopup.show(entryValue,
                                        tradeProfessionTab.levelsFor(entryValue)));
                    }
                    addDeclaredEntryActions(tabIdx, entryIdx);
                    contextMenu.addEntry(Component.translatable("editor.historystages.copy_id").getString(), () -> { Minecraft.getInstance().keyboardHandler.setClipboard(entryValue); EditorToastHandler.copiedToClipboard(entryValue); });
                    contextMenu.addEntry(Component.translatable("editor.historystages.remove").getString(), () -> {
                        String removedValue = getListForSection(tabIdx).get(entryIdx);
                        // A migrated tab owns its extras, including renumbering them.
                        CategoryTab migratedTab = categoryTabs.get(tabIdx);
                        if (migratedTab != null) migratedTab.removeAt(entryIdx);
                        else getListForSection(tabIdx).remove(entryIdx);
                        // When removing an item, shift NBT and lockActions indices
                        // When removing a tag, shift NBT, lockActions + override indices
                        // When removing a mod, shift lockActions + override indices
                        // When removing a spawnlock entry, drop its sources + dimensions entry (keyed by entity ID)
                        // When removing an interactionlock entry, drop its action + item filters (keyed by entity ID)
                        // When removing a mod exception, shift NBT indices
                        // When removing a mod, also remove mod-linked entities and exceptions from that mod
                        if (isTab(tabIdx, CAT_MODS) && removedValue != null) {
                            String prefix = removedValue + ":";
                            editSpawnlock.removeIf(id -> {
                                if (id.startsWith(prefix) && editModLinked.contains(id)) {
                                    editSpawnlockSources.remove(id);
                                    editSpawnlockDimensions.remove(id);
                                    return true;
                                }
                                return false;
                            });
                            editAttacklock.removeIf(id -> id.startsWith(prefix) && editModLinked.contains(id));
                            editInteractionlock.removeIf(id -> {
                                if (id.startsWith(prefix) && editModLinked.contains(id)) {
                                    editInteractionlockActions.remove(id);
                                    editInteractionlockItems.remove(id);
                                    return true;
                                }
                                return false;
                            });
                            editModLinked.removeIf(id -> id.startsWith(prefix));
                            structureTab.removeModSelectionByPrefix(prefix);
                            biomeTab.removeModSelectionByPrefix(prefix);
                            // Remove mod exceptions belonging to this mod
                            modExceptionTab.removeAllFromMod(prefix);
                        }
                        hasChanges = true; updateMaxScroll();
                    });
                    contextMenu.show((int) mouseX, (int) mouseY, this.font);
                    return true;
                }
            }
        }

        return false;
    }

    private void openAddDialog() {
        categoryDropdownVisible = false;
        int contentLeft = 30;
        int contentRight = this.width - 30;
        int cw = contentRight - contentLeft;
        CategoryTab categoryTab = categoryTabs.get(activeTab);
        if (categoryTab != null) { categoryTab.openPicker(this.width / 2, this.height / 2, cw); return; }
    }


    /** Appends an entry (plain item ID or "#tag") to the interaction item filter being edited. */
    private void addInteractionFilterEntry(String id) {
        if (interactionItemsTarget == null) return;
        List<net.bananemdnsa.historystages.data.ItemEntry> list =
                editInteractionlockItems.computeIfAbsent(interactionItemsTarget, k -> new ArrayList<>());
        for (net.bananemdnsa.historystages.data.ItemEntry e : list) {
            if (e.getId().equals(id) && !e.hasNbt()) return; // already listed without an NBT criterion
        }
        list.add(new net.bananemdnsa.historystages.data.ItemEntry(id));
        hasChanges = true;
    }

    /** Plain item IDs currently in the edited filter — drives the item picker's selected state. */
    private List<String> interactionFilterIds() {
        List<String> ids = new ArrayList<>();
        if (interactionItemsTarget == null) return ids;
        List<net.bananemdnsa.historystages.data.ItemEntry> list = editInteractionlockItems.get(interactionItemsTarget);
        if (list == null) return ids;
        for (net.bananemdnsa.historystages.data.ItemEntry e : list) {
            if (!e.getId().startsWith("#")) ids.add(e.getId());
        }
        return ids;
    }

    /** Tag IDs (without the "#" prefix) currently in the edited filter — for the tag picker. */
    private List<String> interactionFilterTagIds() {
        List<String> ids = new ArrayList<>();
        if (interactionItemsTarget == null) return ids;
        List<net.bananemdnsa.historystages.data.ItemEntry> list = editInteractionlockItems.get(interactionItemsTarget);
        if (list == null) return ids;
        for (net.bananemdnsa.historystages.data.ItemEntry e : list) {
            if (e.getId().startsWith("#")) ids.add(e.getId().substring(1));
        }
        return ids;
    }

    /** Right-click menu for one interaction item filter row: NBT, copy, duplicate, remove. */
    private void openInteractionItemMenu(String entityId, int index, int mouseX, int mouseY) {
        List<net.bananemdnsa.historystages.data.ItemEntry> list = editInteractionlockItems.get(entityId);
        if (list == null || index < 0 || index >= list.size()) return;
        final String entryId = list.get(index).getId();

        contextMenu = new ContextMenu();
        contextMenu.addEntry(Component.translatable("editor.historystages.context.edit_nbt").getString(),
                () -> openInteractionItemNbtScreen(entityId, index));
        contextMenu.addEntry(Component.translatable("editor.historystages.copy_id").getString(), () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(entryId);
            EditorToastHandler.copiedToClipboard(entryId);
        });
        contextMenu.addEntry(Component.translatable("editor.historystages.duplicate").getString(), () -> {
            List<net.bananemdnsa.historystages.data.ItemEntry> target = editInteractionlockItems.get(entityId);
            if (target == null || index >= target.size()) return;
            target.add(index + 1, target.get(index).copy());
            hasChanges = true;
        });
        contextMenu.addEntry(Component.translatable("editor.historystages.remove").getString(), () -> {
            List<net.bananemdnsa.historystages.data.ItemEntry> target = editInteractionlockItems.get(entityId);
            if (target == null || index >= target.size()) return;
            target.remove(index);
            if (target.isEmpty()) editInteractionlockItems.remove(entityId);
            interactionItemsPopup.clampScroll();
            hasChanges = true;
        });
        contextMenu.show(mouseX, mouseY, this.font);
    }

    /**
     * Opens the shared NBT editor for one interaction filter entry. The editor screen replaces this
     * one, so {@link #interactionItemsTarget} keeps the popup's context and init() re-opens it.
     */
    private void openInteractionItemNbtScreen(String entityId, int index) {
        List<net.bananemdnsa.historystages.data.ItemEntry> list = editInteractionlockItems.get(entityId);
        if (list == null || index < 0 || index >= list.size()) return;
        net.bananemdnsa.historystages.data.ItemEntry entry = list.get(index);
        interactionItemsTarget = entityId;
        this.minecraft.setScreen(new NbtItemEditScreen(this, entry.getId(), entry.getNbt(), nbt -> {
            List<net.bananemdnsa.historystages.data.ItemEntry> target = editInteractionlockItems.get(entityId);
            if (target == null || index >= target.size()) return;
            target.set(index, new net.bananemdnsa.historystages.data.ItemEntry(entry.getId(),
                    (nbt != null && nbt.size() > 0) ? nbt : null));
            hasChanges = true;
            saveStage();
        }));
    }

    private void openNbtEditScreen(int entryIdx, String itemId) {
        com.google.gson.JsonObject currentNbt = itemTab.nbtByIndex().get(entryIdx);
        this.minecraft.setScreen(new NbtItemEditScreen(this, itemId, currentNbt, nbt -> {
            if (nbt != null) {
                itemTab.nbtByIndex().put(entryIdx, nbt);
            } else {
                itemTab.nbtByIndex().remove(entryIdx);
            }
            hasChanges = true;
            saveStage();
        }));
    }

    /**
     * The criterion on one gated offer.
     *
     * <p>Reads the offer's result stack when the lock is checked, which is why a criterion is
     * worth having here at all: "the offer selling an enchanted book with <em>this</em>
     * enchantment" is a trade a pack author can name, and the stack to compare against is right
     * there when the question is asked.
     */
    private void openTradeNbtEditScreen(int entryIdx) {
        String itemId = tradeOfferTab.iconItemId(entryIdx);
        if (itemId == null) return;
        this.minecraft.setScreen(new NbtItemEditScreen(this, itemId,
                tradeOfferTab.nbtAt(entryIdx), nbt -> {
                    tradeOfferTab.setNbtAt(entryIdx, nbt);
                    hasChanges = true;
                    saveStage();
                }));
    }

    private void openTagNbtEditScreen(int entryIdx, String tagId) {
        com.google.gson.JsonObject currentNbt = tagTab.nbtByIndex().get(entryIdx);
        this.minecraft.setScreen(new NbtItemEditScreen(this, tagId, true, currentNbt, nbt -> {
            if (nbt != null) {
                tagTab.nbtByIndex().put(entryIdx, nbt);
            } else {
                tagTab.nbtByIndex().remove(entryIdx);
            }
            hasChanges = true;
            saveStage();
        }));
    }

    private void openModExceptionNbtEditScreen(int entryIdx, String itemId) {
        com.google.gson.JsonObject currentNbt = modExceptionTab.nbtByIndex().get(entryIdx);
        this.minecraft.setScreen(new NbtItemEditScreen(this, itemId, currentNbt, nbt -> {
            if (nbt != null) {
                modExceptionTab.nbtByIndex().put(entryIdx, nbt);
            } else {
                modExceptionTab.nbtByIndex().remove(entryIdx);
            }
            hasChanges = true;
            saveStage();
        }));
    }

    /** The exception picker: multi-select, NBT-aware, and filtered to the locked mods. */
    private SearchableItemList createModExceptionSearch(
            java.util.function.Consumer<String> onSelect,
            java.util.function.Supplier<java.util.Collection<String>> alreadyAdded) {
        SearchableItemList search = new SearchableItemList(onSelect::accept, alreadyAdded::get);
        search.setMultiSelect(true);
        search.setOnSelectWithNbt((itemId, nbt) -> {
            modExceptionTab.addEntryWithNbt(itemId, nbt);
            hasChanges = true;
            updateMaxScroll();
        });
        search.setModFilter(new java.util.HashSet<>(modTab.entries()));
        return search;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (modEntityPopup.isVisible() && modEntityPopup.mouseDragged(mouseX, mouseY))
            return true;
        if (iconSearch != null && iconSearch.isVisible() && iconSearch.mouseDragged(mouseX, mouseY))
            return true;
        if (actionOverlay() != null) return actionOverlay().mouseDragged(mouseX, mouseY);
        CategoryTab draggedTab = categoryTabs.get(activeTab);
        if (draggedTab != null && draggedTab.mouseDragged(inputContext(mouseX, mouseY), button))
            return true;
        if (anyCategoryPicker(pk -> pk.mouseDragged(mouseX, mouseY)))
            return true;
        if (filterItemSearch.isVisible() && filterItemSearch.mouseDragged(mouseX, mouseY))
            return true;
        if (filterTagSearch.isVisible() && filterTagSearch.mouseDragged(mouseX, mouseY))
            return true;
        if (interactionItemsPopup.isVisible() && interactionItemsPopup.mouseDragged(mouseX, mouseY))
            return true;
        if (modBiomePopup.isVisible() && modBiomePopup.mouseDragged(mouseX, mouseY))
            return true;
        if (modStructurePopup.isVisible() && modStructurePopup.mouseDragged(mouseX, mouseY))
            return true;
        if (scrollBarDragging) {
            updateScrollFromMouse(mouseY, listTop(), listBottom());
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (modEntityPopup.isVisible() && modEntityPopup.mouseReleased())
            return true;
        if (modBiomePopup.isVisible() && modBiomePopup.mouseReleased())
            return true;
        if (modStructurePopup.isVisible() && modStructurePopup.mouseReleased())
            return true;
        if (iconSearch != null && iconSearch.isVisible() && iconSearch.mouseReleased())
            return true;
        if (actionOverlay() != null && actionOverlay().mouseReleased()) return true;
        CategoryTab releasedTab = categoryTabs.get(activeTab);
        if (releasedTab != null && releasedTab.mouseReleased(inputContext(mouseX, mouseY), button))
            return true;
        if (anyCategoryPicker(PickerOverlay::mouseReleased))
            return true;
        if (filterItemSearch.isVisible() && filterItemSearch.mouseReleased())
            return true;
        if (filterTagSearch.isVisible() && filterTagSearch.mouseReleased())
            return true;
        if (interactionItemsPopup.isVisible() && interactionItemsPopup.mouseReleased())
            return true;
        if (scrollBarDragging) {
            scrollBarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void updateScrollFromMouse(double mouseY, int listTop, int listBottom) {
        int listH = listBottom - listTop;
        int thumbHeight = Math.max(20, (int) ((float) listH / (maxScroll + listH) * listH));
        float usableH = listH - thumbHeight;
        if (usableH > 0) {
            float ratio = (float) (mouseY - listTop - thumbHeight / 2.0) / usableH;
            ratio = Math.max(0, Math.min(1, ratio));
            scrollOffset = Math.round(ratio * maxScroll);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            // Snapped, not eased: while the thumb is held the list must track the
            // cursor exactly, or the thumb drifts from where the pointer is.
            smoothScrollOffset.set((float) scrollOffset);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double delta = scrollY;
        if (dimFilterPopup.isVisible()) {
            return dimFilterPopup.mouseScrolled(mouseX, mouseY, scrollY);
        }
        // The generation dialog has nothing to scroll, but swallowing the wheel keeps the entry
        // list behind it from moving while it is open.
        if (generationLimitPopup.isVisible()) return true;
        // Scroll inside category search dropdown
        if (categoryDropdownVisible && !categoryDropdownSuggestions.isEmpty()) {
            int total = categoryDropdownSuggestions.size();
            int maxScroll = Math.max(0, total - MAX_DROPDOWN_ENTRIES);
            int dropH = Math.min(MAX_DROPDOWN_ENTRIES, total) * DROPDOWN_ENTRY_H + 4;
            if (mouseX >= categorySearchBoxX && mouseX < categorySearchBoxX + categorySearchBoxW
                    && mouseY >= 42 && mouseY < 42 + dropH) {
                categoryDropdownScrollOffset = Math.max(0, Math.min(maxScroll,
                        categoryDropdownScrollOffset - (int) Math.signum(delta)));
                return true;
            }
        }
        if (modEntityPopup.isVisible() && modEntityPopup.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (modStructurePopup.isVisible() && modStructurePopup.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (modBiomePopup.isVisible() && modBiomePopup.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        // The card shows the whole recipe at once, so there is nothing left to scroll - but the
        // popup still swallows the wheel so the list behind it stays put.
        if (recipePopupVisible) return true;
        if (actionOverlay() != null)
            return actionOverlay().mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (anyCategoryPicker(pk -> pk.mouseScrolled(mouseX, mouseY, scrollX, scrollY))) return true;
        CategoryTab scrollTab = categoryTabs.get(activeTab);
        if (scrollTab != null && scrollTab.mouseScrolled(inputContext(mouseX, mouseY), scrollX, scrollY))
            return true;
        if (filterItemSearch.isVisible() && filterItemSearch.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (filterTagSearch.isVisible() && filterTagSearch.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;
        if (interactionItemsPopup.isVisible() && interactionItemsPopup.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        if (iconSearch.isVisible() && iconSearch.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) return true;

        // Tab area mouse scroll
        if (maxTabScroll > 0 && mouseY >= tabY && mouseY < tabY + TAB_HEIGHT) {
            tabScrollOffset = Math.max(0, Math.min(maxTabScroll, tabScrollOffset - (int)(delta * 30)));
            return true;
        }

        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 16));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (overridePopupVisible) {
            if (keyCode == 256) { applyOverrideAndClose(); return true; }
            if (keyCode == 257 || keyCode == 335) { applyOverrideAndClose(); return true; } // Enter
            if (overrideNameField.isFocused() && overrideNameField.keyPressed(keyCode, scanCode, modifiers)) return true;
            if (overrideTooltipField.isFocused() && overrideTooltipField.keyPressed(keyCode, scanCode, modifiers)) return true;
            return true;
        }
        if (modEntityPopup.isVisible() && modEntityPopup.keyPressed(keyCode)) return true;
        if (modStructurePopup.isVisible() && modStructurePopup.keyPressed(keyCode)) return true;
        if (modBiomePopup.isVisible() && modBiomePopup.keyPressed(keyCode)) return true;
        if (dimFilterPopup.isVisible() && dimFilterPopup.keyPressed(keyCode)) return true;
        if (generationLimitPopup.isVisible() && generationLimitPopup.keyPressed(keyCode)) return true;
        if (spawnSourcesPopup.isVisible() && spawnSourcesPopup.keyPressed(keyCode)) return true;
        if (tradeLevelsPopup.isVisible() && tradeLevelsPopup.keyPressed(keyCode)) return true;
        if (interactionActionsPopup.isVisible() && interactionActionsPopup.keyPressed(keyCode)) return true;
        if (filterItemSearch.isVisible() && filterItemSearch.keyPressed(keyCode)) return true;
        if (filterTagSearch.isVisible() && filterTagSearch.keyPressed(keyCode)) return true;
        if (interactionItemsPopup.isVisible() && interactionItemsPopup.keyPressed(keyCode)) {
            if (!interactionItemsPopup.isVisible()) interactionItemsTarget = null;
            return true;
        }
        if (recipePopupVisible && keyCode == 256) {
            closeRecipePopup();
            return true;
        }
        if (actionOverlay() != null) return actionOverlay().keyPressed(keyCode);
        if (anyCategoryPicker(pk -> pk.keyPressed(keyCode))) return true;
        CategoryTab keyTab = categoryTabs.get(activeTab);
        if (keyTab != null && keyTab.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (iconSearch.isVisible() && iconSearch.keyPressed(keyCode)) return true;

        // Forward all key events to the category search box when it has focus
        // (ensures Ctrl+A/C/V reach EditBox's built-in handlers reliably)
        if (categorySearchBox != null && categorySearchBox.isFocused()
                && categorySearchBox.keyPressed(keyCode, scanCode, modifiers))
            return true;

        if (keyCode == 256) {
            if (categoryDropdownVisible) {
                categoryDropdownVisible = false;
                categorySearchFilter = "";
                if (categorySearchBox != null) categorySearchBox.setValue("");
                return true;
            }
            tryClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }


    @Override
    public boolean charTyped(char c, int modifiers) {
        if (overridePopupVisible) {
            if (overrideNameField.isFocused() && overrideNameField.charTyped(c, modifiers)) return true;
            if (overrideTooltipField.isFocused() && overrideTooltipField.charTyped(c, modifiers)) return true;
            return true;
        }
        if (generationLimitPopup.isVisible() && generationLimitPopup.charTyped(c)) return true;
        if (actionOverlay() != null) return actionOverlay().charTyped(c);
        if (anyCategoryPicker(pk -> pk.charTyped(c))) return true;
        CategoryTab charTab = categoryTabs.get(activeTab);
        if (charTab != null && charTab.charTyped(c, modifiers)) return true;
        if (filterItemSearch.isVisible() && filterItemSearch.charTyped(c)) return true;
        if (filterTagSearch.isVisible() && filterTagSearch.charTyped(c)) return true;
        if (iconSearch.isVisible() && iconSearch.charTyped(c)) return true;
        return super.charTyped(c, modifiers);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    private void tryClose() {
        if (hasChanges) {
            Screen overview = parent;
            this.minecraft.setScreen(new ConfirmDialog(this, Component.translatable("editor.historystages.unsaved_warning_title"),
                    Component.translatable("editor.historystages.unsaved_warning"), () -> Minecraft.getInstance().setScreen(overview)));
        } else { this.minecraft.setScreen(parent); }
    }

    private void openStageSettings() {
        this.minecraft.setScreen(new StageSettingsScreen(this,
                editStageId, editDisplayName, editResearchTime,
                editMinPedestalTier, editPedestalTierMode, editMode, editAutoTrigger, editTemporary,
                editHiddenDisplay.copy(), editLoseOnDeath, editScrollCompletion, editAddonSettings,
                isNewStage, isIndividual,
                (newId, newName, newTime, newTier, newTierMode, newStageMode, newAutoTrigger,
                 newTemporary, newHidden, newLoseOnDeath, newScrollCompletion, newAddonSettings) -> {
                    editStageId = newId;
                    editDisplayName = newName;
                    editResearchTime = newTime;
                    editMinPedestalTier = newTier;
                    editPedestalTierMode = newTierMode;
                    editMode = newStageMode;
                    editAutoTrigger = newAutoTrigger;
                    editTemporary = newTemporary;
                    editHiddenDisplay = newHidden != null ? newHidden : new net.bananemdnsa.historystages.data.display.HiddenDisplayConfig();
                    editLoseOnDeath = newLoseOnDeath;
                    editScrollCompletion = newScrollCompletion == null ? "" : newScrollCompletion;
                    editAddonSettings = newAddonSettings;
                    hasChanges = true;
                    // Saving in a sub-screen persists the whole stage, so the user never has to
                    // come back here and press Save again.
                    saveStage();
                },
                this::buildEntrySnapshot));
    }

    /** True when the stage's hidden-display config has at least one axis set to REPLACE. */
    private boolean hasReplaceAxis() {
        return editHiddenDisplay.getNameMode() == net.bananemdnsa.historystages.data.display.DisplayMode.REPLACE
                || editHiddenDisplay.getTooltipMode() == net.bananemdnsa.historystages.data.display.DisplayMode.REPLACE;
    }

    /**
     * The per-row name-override map of whichever tab is asking.
     *
     * <p>Was a chain of tab-index comparisons ending in "otherwise the items tab", so a new rich
     * tab would have written its overrides straight into the item list. Every caller sits behind
     * an isTab check already, which makes the empty fallback unreachable — it is there so that a
     * future tab loses its own edits visibly rather than corrupting a neighbour's.
     */
    private Map<Integer, String> overrideNameMap(int tab) {
        CategoryTab categoryTab = sectionAt(tab);
        return categoryTab instanceof RichEntryCategoryTab<?> rich
                ? rich.nameTextByIndex() : new HashMap<>();
    }

    /** The tooltip counterpart of {@link #overrideNameMap}. */
    private Map<Integer, String> overrideTooltipMap(int tab) {
        CategoryTab categoryTab = sectionAt(tab);
        return categoryTab instanceof RichEntryCategoryTab<?> rich
                ? rich.tooltipTextByIndex() : new HashMap<>();
    }

    private void openOverridePopup(int tab, int entryIdx) {
        overrideShowName = editHiddenDisplay.getNameMode()
                == net.bananemdnsa.historystages.data.display.DisplayMode.REPLACE;
        overrideShowTooltip = editHiddenDisplay.getTooltipMode()
                == net.bananemdnsa.historystages.data.display.DisplayMode.REPLACE;
        if (!overrideShowName && !overrideShowTooltip) return;

        overridePopupTab = tab;
        overridePopupIdx = entryIdx;
        overrideNameDefault = editHiddenDisplay.getNameText();
        overrideTooltipDefault = editHiddenDisplay.getTooltipText();

        int rows = (overrideShowName ? 1 : 0) + (overrideShowTooltip ? 1 : 0);
        int w = Math.min(300, this.width - 60);
        int h = 30 + rows * 44 + 34;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        cachedOverrideX = x; cachedOverrideY = y; cachedOverrideW = w; cachedOverrideH = h;

        int fieldX = x + 12;
        int fieldW = w - 24;
        int cy = y + 30;

        overrideNameField.visible = overrideShowName;
        if (overrideShowName) {
            overrideNameField.setPosition(fieldX, cy + 12);
            overrideNameField.setWidth(fieldW);
            String cur = overrideNameMap(tab).get(entryIdx);
            overrideNameField.setValue(cur != null ? cur : "");
            overrideNameField.setCursorPosition(0);
            overrideNameField.setHighlightPos(0);
            overrideNameField.setHint(Component.literal(overrideNameDefault.isEmpty() ? "—" : overrideNameDefault));
            cy += 44;
        }
        overrideTooltipField.visible = overrideShowTooltip;
        if (overrideShowTooltip) {
            overrideTooltipField.setPosition(fieldX, cy + 12);
            overrideTooltipField.setWidth(fieldW);
            String cur = overrideTooltipMap(tab).get(entryIdx);
            overrideTooltipField.setValue(cur != null ? cur : "");
            overrideTooltipField.setCursorPosition(0);
            overrideTooltipField.setHighlightPos(0);
            overrideTooltipField.setHint(Component.literal(overrideTooltipDefault.isEmpty() ? "—" : overrideTooltipDefault));
        }

        overrideResetBtn.setPosition(x + 12, y + h - 26);
        overrideResetBtn.setWidth(70);
        overrideResetBtn.visible = true;
        overrideDoneBtn.setPosition(x + w - 72, y + h - 26);
        overrideDoneBtn.setWidth(60);
        overrideDoneBtn.visible = true;

        overridePopupVisible = true;
        this.setFocused(overrideShowName ? overrideNameField : overrideTooltipField);
        if (overrideShowName) overrideNameField.setFocused(true);
        else overrideTooltipField.setFocused(true);
    }

    private void applyOverrideAndClose() {
        if (overrideShowName) putOrRemove(overrideNameMap(overridePopupTab), overridePopupIdx, overrideNameField.getValue());
        if (overrideShowTooltip) putOrRemove(overrideTooltipMap(overridePopupTab), overridePopupIdx, overrideTooltipField.getValue());
        hasChanges = true;
        closeOverridePopup();
    }

    private void resetOverride() {
        overrideNameMap(overridePopupTab).remove(overridePopupIdx);
        overrideTooltipMap(overridePopupTab).remove(overridePopupIdx);
        hasChanges = true;
        closeOverridePopup();
    }

    private void closeOverridePopup() {
        overridePopupVisible = false;
        overridePopupIdx = -1;
        overrideNameField.visible = false;
        overrideNameField.setFocused(false);
        overrideTooltipField.visible = false;
        overrideTooltipField.setFocused(false);
        overrideResetBtn.visible = false;
        overrideDoneBtn.visible = false;
        this.setFocused(null);
    }

    private boolean handleOverridePopupClick(double mouseX, double mouseY, int button) {
        boolean inside = mouseX >= cachedOverrideX && mouseX <= cachedOverrideX + cachedOverrideW
                && mouseY >= cachedOverrideY && mouseY <= cachedOverrideY + cachedOverrideH;
        if (!inside) {
            applyOverrideAndClose();
            return true;
        }
        if (overrideResetBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (overrideDoneBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (overrideShowName && overrideNameField.mouseClicked(mouseX, mouseY, button)) {
            overrideNameField.setFocused(true);
            overrideTooltipField.setFocused(false);
            return true;
        }
        if (overrideShowTooltip && overrideTooltipField.mouseClicked(mouseX, mouseY, button)) {
            overrideTooltipField.setFocused(true);
            overrideNameField.setFocused(false);
            return true;
        }
        return true; // consume any click inside the popup
    }

    private void renderOverridePopup(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, this.width, this.height, 0x80000000);
        int x = cachedOverrideX, y = cachedOverrideY, w = cachedOverrideW, h = cachedOverrideH;
        // Card chrome (matches renderCard style)
        g.fill(x, y, x + w, y + h, 0xFF555555);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF1A1A1A);
        g.fill(x + 1, y + 1, x + w - 1, y + 20, 0xFF2D2D2D);
        g.fill(x + 1, y + 20, x + w - 1, y + 21, 0xFF555555);
        g.drawString(this.font, Component.translatable("editor.historystages.text_override.title").getString(),
                x + 8, y + 7, 0xFFCC00, false);

        int cy = y + 30;
        if (overrideShowName) {
            g.drawString(this.font, Component.translatable("editor.historystages.text_override.name").getString(),
                    x + 12, cy, 0xAAAAAA, false);
            overrideNameField.render(g, mouseX, mouseY, 0f);
            cy += 44;
        }
        if (overrideShowTooltip) {
            g.drawString(this.font, Component.translatable("editor.historystages.text_override.tooltip").getString(),
                    x + 12, cy, 0xAAAAAA, false);
            overrideTooltipField.render(g, mouseX, mouseY, 0f);
        }
        overrideResetBtn.render(g, mouseX, mouseY, 0f);
        overrideDoneBtn.render(g, mouseX, mouseY, 0f);
    }

    private static void putOrRemove(Map<Integer, String> map, int idx, String value) {
        if (value != null && !value.isEmpty()) {
            map.put(idx, value);
        } else {
            map.remove(idx);
        }
    }

    /**
     * Builds a {@link StageEntry} snapshot from the current edit fields. Used both by
     * {@link #saveStage()} (when persisting) and by the auto-trigger editor (to drive
     * the "Hide stage-locked" filter against the live, unsaved lock data).
     */
    private StageEntry buildEntrySnapshot() {
        // Start from the stage as it was, not from a blank one. Everything below overwrites the
        // fields the editor owns; anything it does not model — an addon category's entries, say —
        // would otherwise be erased on every save, because this snapshot is what gets written to
        // disk. A blank base makes that loss silent and applies to every field added in future.
        StageEntry newEntry = originalEntry != null ? originalEntry.copy() : new StageEntry();
        newEntry.setDisplayName(editDisplayName);
        newEntry.setResearchTime(editResearchTime);
        newEntry.setMinPedestalTier(editMinPedestalTier);
        newEntry.setPedestalTierMode(editPedestalTierMode);
        newEntry.setMode(editMode);
        newEntry.setAutoTrigger(editAutoTrigger);
        newEntry.setTemporary(editTemporary);
        newEntry.setHiddenDisplay(editHiddenDisplay);
        newEntry.setLoseOnDeath(editLoseOnDeath);
        newEntry.setIcon(editIcon);
        newEntry.setScrollCompletion(editScrollCompletion);
        newEntry.setDependencies(editDependencies);
        for (CategoryTab tab : categoryTabs.values()) {
            tab.store(newEntry);
        }
        for (StageSettingsGroup group : StageSettingsGroups.all()) {
            group.store(newEntry, editAddonSettings);
        }
        return newEntry;
    }

    private void saveStage() {
        String id = editStageId.trim();
        if (id.isEmpty()) { saveError = Component.translatable("editor.historystages.id_empty").getString(); return; }
        if (!id.matches("[a-zA-Z0-9_\\-]+")) { saveError = Component.translatable("editor.historystages.id_invalid").getString(); return; }
        if (editDisplayName.trim().isEmpty()) { saveError = Component.translatable("editor.historystages.display_name_empty").getString(); return; }
        saveError = "";

        // Keep the edits pending when the stage is too large, so nothing is lost on a failed save.
        if (StageSaver.send(id, buildEntrySnapshot(), isIndividual, false, targetFolder)) {
            hasChanges = false;
        }
    }

    private void openDependencyEditor() {
        this.minecraft.setScreen(new DependencyEditorScreen(this, editDependencies, isIndividual,
                editStageId, deps -> {
                    this.editDependencies = deps;
                    this.hasChanges = true;
                    saveStage();
                }));
    }

    private SearchableItemList createIconSearch() {
        return new SearchableItemList(id -> {
            editIcon = id;
            hasChanges = true;
            iconSearch = createIconSearch(); // reset to hidden state
        });
    }

    private ItemStack resolveIconPreview() {
        return net.bananemdnsa.historystages.client.ClientToastHandler.resolveIcon(editIcon);
    }

    /** Small button that shows the current stage icon and opens the icon picker when clicked. */
    private class IconPickerButton extends net.minecraft.client.gui.components.AbstractWidget {
        private final Runnable action;

        IconPickerButton(int x, int y, int w, int h, Runnable action) {
            super(x, y, w, h, Component.translatable("editor.historystages.field.icon"));
            this.action = action;
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            boolean hov = isHoveredOrFocused();
            int bg = hov ? 0x40FFCC00 : 0x20FFFFFF;
            guiGraphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
            guiGraphics.fill(getX(), getY() + getHeight() - 1, getX() + getWidth(), getY() + getHeight(),
                    hov ? 0xFFFFCC00 : 0xFF555555);
            ItemStack icon = resolveIconPreview();
            if (!icon.isEmpty()) {
                guiGraphics.renderItem(icon, getX() + 1, getY() + 1);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            action.run();
        }

        @Override
        protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput narr) {
            defaultButtonNarrationText(narr);
        }
    }

    @Override public void onClose() { this.minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return true; }

}
