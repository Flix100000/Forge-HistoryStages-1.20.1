package net.bananemdnsa.historystages;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    // --- CLIENT CONFIG (Nur Dinge, die die eigene Anzeige/UI betreffen) ---
    public static class Client {
        public final ForgeConfigSpec.BooleanValue showTooltips;
        public final ForgeConfigSpec.BooleanValue showStageName;
        public final ForgeConfigSpec.BooleanValue showAllUntilComplete;
        // Jade integration
        public final ForgeConfigSpec.BooleanValue jadeShowInfo;
        public final ForgeConfigSpec.BooleanValue jadeStageName;
        public final ForgeConfigSpec.BooleanValue jadeShowAllUntilComplete;

        public final ForgeConfigSpec.BooleanValue dimUseActionbar;
        public final ForgeConfigSpec.BooleanValue dimShowChat;
        public final ForgeConfigSpec.BooleanValue dimShowStagesInChat;
        public final ForgeConfigSpec.BooleanValue showLockIcons;
        public final ForgeConfigSpec.BooleanValue showBoosterTooltips;
        public final ForgeConfigSpec.BooleanValue showScrollTierTooltip;
        // Structure lock visuals
        public final ForgeConfigSpec.BooleanValue structureBorderEnabled;
        public final ForgeConfigSpec.DoubleValue structureBorderDistance;
        public final ForgeConfigSpec.BooleanValue structureLockOverlayEnabled;
        public final ForgeConfigSpec.DoubleValue structureLockOverlayOpacity;
        public final ForgeConfigSpec.BooleanValue mobUseActionbar;
        public final ForgeConfigSpec.BooleanValue mobShowChat;
        public final ForgeConfigSpec.BooleanValue mobShowStagesInChat;

        // Individual Stages
        public final ForgeConfigSpec.BooleanValue showSilverLockIcons;
        public final ForgeConfigSpec.BooleanValue showIndividualTooltips;

        // JEI Hiding (Issue #64)
        public final ForgeConfigSpec.BooleanValue hideLockedItemsInJei;
        public final ForgeConfigSpec.BooleanValue hideLockedRecipesInJei;
        public final ForgeConfigSpec.EnumValue<MultiStagePolicy> lockedItemMultiStagePolicy;

        public enum MultiStagePolicy {
            STRICT,   // locked while ANY assigned stage is locked
            LENIENT   // unlocked as soon as ANY assigned stage is unlocked
        }

        public Client(ForgeConfigSpec.Builder builder) {
            builder.comment(
                    "Found a bug or have a feature request?",
                    "Report it on GitHub: https://github.com/Flix100000/History-Stages/issues",
                    "",
                    "Visual and UI settings (Individual for each player)")
                    .push("visuals");

            showTooltips = builder
                    .comment("Show information tooltips on locked items? [Default: true]")
                    .define("showTooltips", true);

            showStageName = builder
                    .comment("If tooltips are enabled, show the name of the required stage? [Default: true]")
                    .define("showStageName", true);

            showAllUntilComplete = builder
                    .comment("If an item is in multiple stages, show all of them until all are unlocked? [Default: true]")
                    .define("showAllUntilComplete", true);

            showLockIcons = builder
                    .comment("Show a lock icon overlay on locked items in JEI/EMI and Inventories? [Default: true]")
                    .define("showLockIcons", true);

            showBoosterTooltips = builder
                    .comment("Show a tooltip on Research Pedestal booster blocks describing their speed/cost effect? [Default: true]")
                    .define("showBoosterTooltips", true);

            showScrollTierTooltip = builder
                    .comment("Show the minimum required Pedestal tier on Research Scroll tooltips? [Default: true]")
                    .define("showScrollTierTooltip", true);

            structureBorderEnabled = builder
                    .comment("Render a red force-field overlay on the walls of locked structures as you approach them? [Default: true]")
                    .define("structureBorderEnabled", true);

            structureBorderDistance = builder
                    .comment("How close (in blocks) to a locked structure wall before the border becomes visible. The border fades in as you approach. [Default: 8.0]")
                    .defineInRange("structureBorderDistance", 8.0, 1.0, 32.0);

            structureLockOverlayEnabled = builder
                    .comment("While standing inside a locked structure, tint the whole screen red (like looking through red glasses) to signal the lock? [Default: true]")
                    .define("structureLockOverlayEnabled", true);

            structureLockOverlayOpacity = builder
                    .comment("Opacity of the red lock-overlay (0.0 = invisible, 1.0 = fully opaque). [Default: 0.30]")
                    .defineInRange("structureLockOverlayOpacity", 0.30, 0.0, 1.0);

            builder.pop();

            builder.comment("Settings for Jade block overlay (requires Jade mod)").push("jade");

            jadeShowInfo = builder
                    .comment("Show stage information on locked blocks in the Jade overlay? [Default: true]")
                    .define("showInfo", true);

            jadeStageName = builder
                    .comment("If Jade info is enabled, show the name of the required stage? [Default: true]")
                    .define("showStageName", true);

            jadeShowAllUntilComplete = builder
                    .comment("If a block is in multiple stages, show all of them until all are unlocked? [Default: true]")
                    .define("showAllUntilComplete", true);

            builder.pop();

            builder.comment("Settings for dimension access feedback").push("dimension_lock");

            dimUseActionbar = builder
                    .comment("Show a simple 'Dimension Locked' message in the actionbar? [Default: true]?")
                    .define("useActionbar", true);

            dimShowChat = builder
                    .comment("Show the dimension lock message in the chat? [Default: false]")
                    .define("showInChat", false);

            dimShowStagesInChat = builder
                    .comment("If dimShowChat is true, should the required stages also be listed? [Default: true]")
                    .define("showStagesInChat", true);

            builder.pop();

            builder.comment("Settings for mob damage lock feedback").push("mob_lock");

            mobUseActionbar = builder
                    .comment("Show a 'Mob Protected' message in the actionbar? [Default: true]")
                    .define("useActionbar", true);

            mobShowChat = builder
                    .comment("Show the mob lock message in the chat? [Default: false]")
                    .define("showInChat", false);

            mobShowStagesInChat = builder
                    .comment("If mobShowChat is true, should the required stages also be listed? [Default: true]")
                    .define("showStagesInChat", true);
            builder.pop();

            builder.comment("Individual Stage Visual Settings").push("individual_stages");

            showSilverLockIcons = builder
                    .comment("Show a silver lock icon on items locked by individual stages? [Default: true]")
                    .define("showSilverLockIcons", true);

            showIndividualTooltips = builder
                    .comment("Show tooltip information for items locked by individual stages? [Default: true]")
                    .define("showIndividualTooltips", true);

            builder.pop();

            builder.comment("Dependency Display Settings").push("dependencies");

            showDependenciesOnScroll = builder
                    .comment("Show dependency requirements in research scroll tooltips? [Default: true]")
                    .define("showDependenciesOnScroll", true);

            hideFulfilledDependencies = builder
                    .comment("Hide already fulfilled dependencies in scroll tooltips? [Default: false]")
                    .define("hideFulfilledDependencies", false);

            builder.pop();

            builder.comment("JEI hiding (Issue #64) — opt-in toggle for fully hiding locked items/recipes in JEI.")
                    .push("jei_hiding");

            hideLockedItemsInJei = builder
                    .comment("Remove locked items from the JEI ingredient panel entirely. [Default: false]")
                    .define("hideLockedItemsInJei", false);

            hideLockedRecipesInJei = builder
                    .comment("Hide recipes whose OUTPUT is a locked item in JEI. [Default: false]")
                    .define("hideLockedRecipesInJei", false);

            lockedItemMultiStagePolicy = builder
                    .comment("How to treat items assigned to multiple stages:",
                            "STRICT  = locked while ANY assigned stage is still locked (default).",
                            "LENIENT = unlocked as soon as ANY assigned stage is unlocked.")
                    .defineEnum("lockedItemMultiStagePolicy", MultiStagePolicy.STRICT);

            builder.pop();
        }

        public final ForgeConfigSpec.BooleanValue showDependenciesOnScroll;
        public final ForgeConfigSpec.BooleanValue hideFulfilledDependencies;
    }

    // --- COMMON CONFIG (Server-Einstellungen und globale Logik) ---
    public static class Common {
        public final ForgeConfigSpec.BooleanValue showWelcomeMessage;
        public final ForgeConfigSpec.BooleanValue showDebugErrors;
        public final ForgeConfigSpec.BooleanValue enableRuntimeLogging;

        public final ForgeConfigSpec.BooleanValue lockMobLoot;
        public final ForgeConfigSpec.BooleanValue lockBlockBreaking;
        public final ForgeConfigSpec.DoubleValue lockedBlockBreakSpeedMultiplier;
        public final ForgeConfigSpec.BooleanValue lockItemUsage;
        public final ForgeConfigSpec.BooleanValue lockEntityItems;
        public final ForgeConfigSpec.BooleanValue lockBlockInteraction;
        public final ForgeConfigSpec.BooleanValue lockContainerInteraction;
        public final ForgeConfigSpec.BooleanValue lockEnchanting;

        // Zentrale Benachrichtigungen (Chat, Actionbar, Sounds, Texte)
        public final ForgeConfigSpec.BooleanValue broadcastChat;
        public final ForgeConfigSpec.ConfigValue<String> unlockMessageFormat;
        public final ForgeConfigSpec.BooleanValue useActionbar;
        public final ForgeConfigSpec.BooleanValue useSounds;
        public final ForgeConfigSpec.BooleanValue useToasts;
        public final ForgeConfigSpec.ConfigValue<String> defaultStageIcon;

        // Forschungsstation
        public final ForgeConfigSpec.IntValue researchTimeInSeconds;
        public final ForgeConfigSpec.BooleanValue showDependencyScreenInPedestal;
        public final ForgeConfigSpec.BooleanValue lockScrollWhileResearching;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> researchBoosters;

        // Loot-Ersetzungen
        public final ForgeConfigSpec.BooleanValue useReplacements;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> replacementItems;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> replacementTags;

        // Individual Stages - Gameplay
        public final ForgeConfigSpec.BooleanValue individualLockItemPickup;
        public final ForgeConfigSpec.BooleanValue individualLockLoot;
        public final ForgeConfigSpec.BooleanValue individualDropOnRevoke;
        public final ForgeConfigSpec.BooleanValue individualLockBlockBreaking;
        public final ForgeConfigSpec.DoubleValue individualLockedBlockBreakSpeedMultiplier;
        public final ForgeConfigSpec.BooleanValue individualLockItemUsage;
        public final ForgeConfigSpec.BooleanValue individualLockBlockInteraction;
        public final ForgeConfigSpec.BooleanValue individualLockEnchanting;

        // Individual Stages - Notifications
        public final ForgeConfigSpec.BooleanValue individualBroadcastChat;
        public final ForgeConfigSpec.ConfigValue<String> individualUnlockMessageFormat;
        public final ForgeConfigSpec.BooleanValue individualUseActionbar;
        public final ForgeConfigSpec.BooleanValue individualUseSounds;
        public final ForgeConfigSpec.BooleanValue individualUseToasts;

        // Structure Lock
        public final ForgeConfigSpec.IntValue structureCheckInterval;
        public final ForgeConfigSpec.BooleanValue structureDamageEnabled;
        public final ForgeConfigSpec.DoubleValue structureDamageAmount;
        public final ForgeConfigSpec.IntValue structureDamageInterval;
        public final ForgeConfigSpec.BooleanValue structureMessageEnabled;
        public final ForgeConfigSpec.ConfigValue<String> structureLockMessageFormat;
        public final ForgeConfigSpec.BooleanValue structureLockInChat;
        public final ForgeConfigSpec.IntValue structureLockPadding;
        public final ForgeConfigSpec.IntValue structureClusterDistance;
        public final ForgeConfigSpec.BooleanValue structureBlockRightClick;
        public final ForgeConfigSpec.BooleanValue structureBlockLeftClick;
        public final ForgeConfigSpec.BooleanValue structureBlockProjectiles;

        // Lock-Message Overrides (leer = Translation Key wird verwendet)
        public final ForgeConfigSpec.ConfigValue<String> msgDimensionUnknown;
        public final ForgeConfigSpec.ConfigValue<String> msgMobUnknown;
        public final ForgeConfigSpec.ConfigValue<String> msgItemLocked;
        public final ForgeConfigSpec.ConfigValue<String> msgBlockLocked;
        public final ForgeConfigSpec.ConfigValue<String> msgEntityItemLocked;
        public final ForgeConfigSpec.ConfigValue<String> msgEnchantmentLocked;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.comment(
                    "Found a bug or have a feature request?",
                    "Report it on GitHub: https://github.com/Flix100000/History-Stages/issues",
                    "",
                    "Chat messages settings"
            ).push("messages");

            showWelcomeMessage = builder
                    .comment("Show a welcome message in chat when a player joins the world? [Default: true]")
                    .define("showWelcomeMessage", true);

            showDebugErrors = builder
                    .comment("Show debug messages in chat if a JSON stage has errors or missing items? [Default: true]")
                    .define("showDebugErrors", true);

            enableRuntimeLogging = builder
                    .comment("Log runtime events (stage unlock/lock, blocked actions, loot replacements) to config/historystages/logs/runtime-*.log? [Default: false]")
                    .define("enableRuntimeLogging", false);

            builder.pop(); // general

            builder.comment("Gameplay and Server-side settings").push("gameplay");

            lockMobLoot = builder
                    .comment("Handle locked items in mob loot tables? [Default: true]")
                    .define("lockMobLoot", true);

            lockBlockBreaking = builder
                    .comment("Make locked blocks much harder to break and prevent their drops? [Default: true]")
                    .define("lockBlockBreaking", true);

            lockedBlockBreakSpeedMultiplier = builder
                    .comment("Break speed multiplier for locked blocks. Lower = slower. 0.05 = 20x slower (like using wrong tool). [Default: 0.05]")
                    .defineInRange("lockedBlockBreakSpeedMultiplier", 0.05, 0.001, 1.0);

            lockItemUsage = builder
                    .comment("Prevent using locked items? (Blocks equipping armor, using weapons, eating food, etc.) [Default: true]")
                    .define("lockItemUsage", true);

            lockEntityItems = builder
                    .comment("Prevent interacting with or breaking armor stands and item frames that contain locked items? [Default: true]")
                    .define("lockEntityItems", true);

            lockBlockInteraction = builder
                    .comment("Prevent opening the GUI of locked blocks? (Chests, furnaces, crafting tables, etc.) [Default: true]")
                    .define("lockBlockInteraction", true);

            lockContainerInteraction = builder
                    .comment("Prevent moving individually-locked items in containers? (Blocks taking items from chests, machines, etc.) [Default: true]")
                    .define("lockContainerInteraction", true);

            lockEnchanting = builder
                    .comment("Prevent applying locked enchantments via anvil (locked enchanted books) and enchanting table? [Default: true]")
                    .define("lockEnchanting", true);

            builder.pop(); // gameplay

            // --- NOTIFICATIONS SECTION ---
            builder.comment("Global Notification Settings (Server-controlled)").push("notifications");

            broadcastChat = builder
                    .comment("Show unlock/lock messages in the chat for everyone? [Default: true]")
                    .define("broadcastChat", true);

            unlockMessageFormat = builder
                    .comment("Message format for unlocks (Only for the Chat and only if 'broadcastChat' = true). Use {stage} for the name and & for colors.")
                    .define("unlockMessageFormat", "&fThe world has entered the &b{stage}&f!");

            useActionbar = builder
                    .comment("Show messages in the actionbar for everyone? [Default: false]")
                    .define("useActionbar", false);

            useSounds = builder
                    .comment("Play notification sounds for everyone? [Default: true]")
                    .define("useSounds", true);

            useToasts = builder
                    .comment("Show an advancement-style toast popup when a stage is unlocked? [Default: true]")
                    .define("useToasts", true);

            defaultStageIcon = builder
                    .comment("Default icon used in unlock toasts for stages that don't define their own icon. [Default: historystages:research_scroll]")
                    .define("defaultStageIcon", "historystages:research_scroll");

            builder.pop(); // Schließt "notifications"

            // --- RESEARCH Pedestal SECTION ---
            builder.comment("Research Pedestal Settings").push("research");
            researchTimeInSeconds = builder
                    .comment("Default research time in seconds. Used as fallback if a stage does not define its own 'research_time' in the JSON. [Default: 20]")
                    .defineInRange("researchTimeInSeconds", 20, 1, 86400);

            showDependencyScreenInPedestal = builder
                    .comment("Show dependency checklist screen when interacting with pedestal that has dependency requirements? [Default: true]")
                    .define("showDependencyScreenInPedestal", true);

            lockScrollWhileResearching = builder
                    .comment("Lock the scroll in the pedestal once research has started? Prevents players (and hoppers) from removing the scroll until research completes or the pedestal is broken. [Default: false]")
                    .define("lockScrollWhileResearching", false);

            researchBoosters = builder
                    .comment(
                            "Booster blocks placed directly UNDER a Research Pedestal modify the active research.",
                            "Format per entry: \"block_id, speed_percent, cost_percent, tier, mode\"",
                            "  speed_percent: research time reduction (0-90). 90% = max (research runs 10x).",
                            "  cost_percent:  item-dependency count reduction (0-90). Locked into the scroll on first deposit.",
                            "  tier:          minimum pedestal tier the booster works under (1-4).",
                            "  mode:          'min' = this tier and higher, 'exact' = only this tier.",
                            "Legacy 3-column rows are accepted and treated as tier=1, mode=min.",
                            "Unknown block ids and out-of-range values are logged and skipped/clamped.")
                    .defineListAllowEmpty("researchBoosters",
                            List.of(),
                            obj -> obj instanceof String);

            builder.pop(); // Schließt "research"

            // --- LOOT REPLACEMENTS SECTION ---
            builder.comment("Settings for replacing locked loot with alternatives").push("loot_replacements");

            useReplacements = builder
                    .comment("If true, locked items are replaced by specific items/tags. If false, they disappear. [Default: false]")
                    .define("useReplacements", false);

            replacementItems = builder
                    .comment("{ReplacementPriority:1} A list of Item IDs to pick from if 'useReplacements' is true. [Default: cobblestone, dirt]")
                    .defineList("replacementItems", List.of("minecraft:cobblestone", "minecraft:dirt"), o -> o instanceof String);

            replacementTags = builder
                    .comment("{ReplacementPriority:2} A list of tags (e.g. 'forge:dusts') to pick a random replacement from. [Default: empty]")
                    .defineList("replacementTags", List.of(), o -> o instanceof String);
            builder.pop(); // Schließt "loot_replacements"

            // --- INDIVIDUAL STAGES SECTION ---
            builder.comment("Individual Stage Settings (per-player stages)").push("individual_stages");

            individualLockItemPickup = builder
                    .comment("Prevent players from picking up items locked by individual stages? [Default: true]")
                    .define("lockItemPickup", true);

            individualLockLoot = builder
                    .comment("Handle items locked by individual stages in Lootr containers and mob loot? Lootr containers are checked against the player opening them; mob drops are shared world items, so they are checked against the killing player. [Default: true]")
                    .define("lockLoot", true);

            individualDropOnRevoke = builder
                    .comment("Drop locked items from a player's inventory when their individual stage is revoked? [Default: true]")
                    .define("dropOnRevoke", true);

            individualLockBlockBreaking = builder
                    .comment("Make blocks locked by individual stages much harder to break and prevent their drops? [Default: true]")
                    .define("lockBlockBreaking", true);

            individualLockedBlockBreakSpeedMultiplier = builder
                    .comment("Break speed multiplier for blocks locked by individual stages. Lower = slower. 0.05 = 20x slower. [Default: 0.05]")
                    .defineInRange("lockedBlockBreakSpeedMultiplier", 0.05, 0.001, 1.0);

            individualLockItemUsage = builder
                    .comment("Prevent using items locked by individual stages? (Blocks equipping armor, using weapons, eating food, etc.) [Default: true]")
                    .define("lockItemUsage", true);

            individualLockBlockInteraction = builder
                    .comment("Prevent opening the GUI of blocks locked by individual stages? (Chests, furnaces, crafting tables, etc.) [Default: true]")
                    .define("lockBlockInteraction", true);

            individualLockEnchanting = builder
                    .comment("Prevent applying enchantments locked by individual stages via anvil and enchanting table? [Default: true]")
                    .define("lockEnchanting", true);

            individualBroadcastChat = builder
                    .comment("Show individual stage unlock/lock messages in the chat for the player? [Default: true]")
                    .define("broadcastChat", true);

            individualUnlockMessageFormat = builder
                    .comment("Message format for individual stage unlocks (chat). Use {stage} for the name, {player} for the player name, and & for colors.")
                    .define("unlockMessageFormat", "&fYou have unlocked &b{stage}&f!");

            individualUseActionbar = builder
                    .comment("Show individual stage messages in the actionbar? [Default: false]")
                    .define("useActionbar", false);

            individualUseSounds = builder
                    .comment("Play notification sounds for individual stage unlocks? [Default: true]")
                    .define("useSounds", true);

            individualUseToasts = builder
                    .comment("Show an advancement-style toast popup when an individual stage is unlocked? [Default: true]")
                    .define("useToasts", true);

            builder.pop(); // Schließt "individual_stages"

            // --- STRUCTURE LOCK SECTION ---
            builder.comment("Structure Lock Settings (locks player entry into specified structures)").push("structure_lock");

            structureCheckInterval = builder
                    .comment("How often (in ticks) to check if a player is inside a locked structure. Higher = better performance, lower = faster reaction. [Default: 10]")
                    .defineInRange("checkInterval", 10, 1, 200);

            structureMessageEnabled = builder
                    .comment("Show the player a message when they are inside a locked structure? [Default: true]")
                    .define("messageEnabled", true);

            structureLockMessageFormat = builder
                    .comment("Message format for structure lock. Use {structure} for the structure ID, {stage} for the required stage, and & for colors.")
                    .define("messageFormat", "&cYou cannot enter &e{structure}&c yet!");

            structureLockInChat = builder
                    .comment("Show the structure lock message in chat as well (otherwise only actionbar)? [Default: false]")
                    .define("showInChat", false);

            structureDamageEnabled = builder
                    .comment("Damage the player while they are inside a locked structure? [Default: false]")
                    .define("damageEnabled", false);

            structureDamageAmount = builder
                    .comment("Amount of damage dealt per damage tick. [Default: 1.0]")
                    .defineInRange("damageAmount", 1.0, 0.1, 100.0);

            structureDamageInterval = builder
                    .comment("How often (in ticks) to deal damage while inside a locked structure. [Default: 20]")
                    .defineInRange("damageInterval", 20, 1, 600);

            structureLockPadding = builder
                    .comment(
                            "ADVANCED — leave this alone if you don't know what it does.",
                            "Extra blocks added around each piece of a locked structure (rooms, corridors, houses, ...).",
                            "Note: a fixed safety buffer of 2 blocks is ALWAYS added on top of this value, so",
                            "the effective padding around structure walls is (lockPadding + 2). Setting this to 0",
                            "still leaves a 2-block safety wall between you and the structure. [Default: 0]"
                    )
                    .defineInRange("lockPadding", 0, 0, 16);

            structureClusterDistance = builder
                    .comment(
                            "ADVANCED — leave this alone if you don't know what it does.",
                            "How far apart (in blocks) two pieces of the same structure can be while still being",
                            "joined into one connected lock zone. Example for a village: with a low value, each",
                            "house is its own little zone and the gaps between houses are walkable; with a higher",
                            "value, neighbouring houses + paths fuse into one lock zone covering the whole area.",
                            "Higher = larger, more 'filled-in' lock zones. Lower = more precise, more gaps. [Default: 6]"
                    )
                    .defineInRange("clusterDistance", 6, 0, 32);

            structureBlockRightClick = builder
                    .comment("Cancel ALL right-click interactions (blocks, items, entities) while the player is inside a locked structure? [Default: true]")
                    .define("blockRightClick", true);

            structureBlockLeftClick = builder
                    .comment("Cancel ALL left-click interactions (attacking entities, breaking blocks) while the player is inside a locked structure? [Default: true]")
                    .define("blockLeftClick", true);

            structureBlockProjectiles = builder
                    .comment("Cancel projectiles (arrows, snowballs, ender pearls, etc.) the moment they would impact something inside a locked structure? [Default: true]")
                    .define("blockProjectiles", true);

            builder.pop(); // Schließt "structure_lock"

            // --- LOCK MESSAGES SECTION ---
            builder.comment(
                    "Override the displayed text for the six 'is locked' / 'unknown' messages.",
                    "Leave a value empty (\"\") to fall back to the default messages.",
                    "Use & for color codes (e.g. &c for red)."
            ).push("lock_messages");

            msgDimensionUnknown = builder
                    .comment("Actionbar message when entering a locked dimension. Lang key: message.historystages.dimension_unknown")
                    .define("dimensionUnknown", "");

            msgMobUnknown = builder
                    .comment("Actionbar message when attacking a locked mob. Lang key: message.historystages.mob_unknown")
                    .define("mobUnknown", "");

            msgItemLocked = builder
                    .comment("Actionbar message when interacting with a locked item. Lang key: message.historystages.item_locked")
                    .define("itemLocked", "");

            msgBlockLocked = builder
                    .comment("Actionbar message when interacting with a locked block. Lang key: message.historystages.block_locked")
                    .define("blockLocked", "");

            msgEntityItemLocked = builder
                    .comment("Actionbar message when interacting with armor stands / item frames holding locked items. Lang key: message.historystages.entity_item_locked")
                    .define("entityItemLocked", "");

            msgEnchantmentLocked = builder
                    .comment("Actionbar message when applying a locked enchantment. Lang key: message.historystages.enchantment_locked")
                    .define("enchantmentLocked", "");

            builder.pop(); // lock_messages
        }
    }

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        final Pair<Client, ForgeConfigSpec> clientPair = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();

        final Pair<Common, ForgeConfigSpec> commonPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();
    }
}