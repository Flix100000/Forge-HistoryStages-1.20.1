package net.bananemdnsa.historystages;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class Config {

    // --- CLIENT CONFIG (Nur Dinge, die die eigene Anzeige/UI betreffen) ---
    public static class Client {
        public final ModConfigSpec.BooleanValue showTooltips;
        public final ModConfigSpec.BooleanValue showStageName;
        public final ModConfigSpec.BooleanValue showAllUntilComplete;
        // Jade integration
        public final ModConfigSpec.BooleanValue jadeShowInfo;
        public final ModConfigSpec.BooleanValue jadeStageName;
        public final ModConfigSpec.BooleanValue jadeShowAllUntilComplete;
        public final ModConfigSpec.BooleanValue dimUseActionbar;
        public final ModConfigSpec.BooleanValue dimShowChat;
        public final ModConfigSpec.BooleanValue dimShowStagesInChat;
        public final ModConfigSpec.BooleanValue showLockIcons;
        public final ModConfigSpec.BooleanValue mobUseActionbar;
        public final ModConfigSpec.BooleanValue mobShowChat;
        public final ModConfigSpec.BooleanValue mobShowStagesInChat;

        // Individual Stages
        public final ModConfigSpec.BooleanValue showSilverLockIcons;
        public final ModConfigSpec.BooleanValue showIndividualTooltips;

        // Dependencies
        public final ModConfigSpec.BooleanValue showDependenciesOnScroll;
        public final ModConfigSpec.BooleanValue hideFulfilledDependencies;

        public Client(ModConfigSpec.Builder builder) {
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
                    .comment("Show a lock icon overlay on locked items in JEI and Inventories? (Will be disabled if EMI is installed) [Default: true]")
                    .define("showLockIcons", true);

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
        }
    }

    // --- COMMON CONFIG (Server-Einstellungen und globale Logik) ---
    public static class Common {
        public final ModConfigSpec.BooleanValue showWelcomeMessage;
        public final ModConfigSpec.BooleanValue showDebugErrors;
        public final ModConfigSpec.BooleanValue enableRuntimeLogging;

        public final ModConfigSpec.BooleanValue lockMobLoot;
        public final ModConfigSpec.BooleanValue lockBlockBreaking;
        public final ModConfigSpec.DoubleValue lockedBlockBreakSpeedMultiplier;
        public final ModConfigSpec.BooleanValue lockItemUsage;
        public final ModConfigSpec.BooleanValue lockEntityItems;
        public final ModConfigSpec.BooleanValue lockBlockInteraction;
        public final ModConfigSpec.BooleanValue lockContainerInteraction;
        public final ModConfigSpec.BooleanValue lockEnchanting;

        // Zentrale Benachrichtigungen (Chat, Actionbar, Sounds, Texte)
        public final ModConfigSpec.BooleanValue broadcastChat;
        public final ModConfigSpec.ConfigValue<String> unlockMessageFormat;
        public final ModConfigSpec.BooleanValue useActionbar;
        public final ModConfigSpec.BooleanValue useSounds;
        public final ModConfigSpec.BooleanValue useToasts;
        public final ModConfigSpec.ConfigValue<String> defaultStageIcon;

        // Forschungsstation
        public final ModConfigSpec.IntValue researchTimeInSeconds;
        public final ModConfigSpec.BooleanValue showDependencyScreenInPedestal;

        // Loot-Ersetzungen
        public final ModConfigSpec.BooleanValue useReplacements;
        public final ModConfigSpec.ConfigValue<List<? extends String>> replacementItems;
        public final ModConfigSpec.ConfigValue<List<? extends String>> replacementTags;

        // Individual Stages - Gameplay
        public final ModConfigSpec.BooleanValue individualLockItemPickup;
        public final ModConfigSpec.BooleanValue individualDropOnRevoke;
        public final ModConfigSpec.BooleanValue individualLockBlockBreaking;
        public final ModConfigSpec.DoubleValue individualLockedBlockBreakSpeedMultiplier;
        public final ModConfigSpec.BooleanValue individualLockItemUsage;
        public final ModConfigSpec.BooleanValue individualLockBlockInteraction;
        public final ModConfigSpec.BooleanValue individualLockEnchanting;

        // Individual Stages - Notifications
        public final ModConfigSpec.BooleanValue individualBroadcastChat;
        public final ModConfigSpec.ConfigValue<String> individualUnlockMessageFormat;
        public final ModConfigSpec.BooleanValue individualUseActionbar;
        public final ModConfigSpec.BooleanValue individualUseSounds;
        public final ModConfigSpec.BooleanValue individualUseToasts;

        // Structure Lock
        public final ModConfigSpec.IntValue structureCheckInterval;
        public final ModConfigSpec.BooleanValue structureDamageEnabled;
        public final ModConfigSpec.DoubleValue structureDamageAmount;
        public final ModConfigSpec.IntValue structureDamageInterval;
        public final ModConfigSpec.BooleanValue structureMessageEnabled;
        public final ModConfigSpec.ConfigValue<String> structureLockMessageFormat;
        public final ModConfigSpec.BooleanValue structureLockInChat;

        public Common(ModConfigSpec.Builder builder) {
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

            builder.pop(); // messages

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
                    .comment("Default icon item shown in unlock toasts when a stage has no icon set. Use the item's full registry ID. [Default: historystages:research_scroll]")
                    .define("defaultStageIcon", "historystages:research_scroll");

            builder.pop(); // notifications

            // --- RESEARCH Pedestal SECTION ---
            builder.comment("Research Pedestal Settings").push("research");
            researchTimeInSeconds = builder
                    .comment("Default research time in seconds. Used as fallback if a stage does not define its own 'research_time' in the JSON. [Default: 20]")
                    .defineInRange("researchTimeInSeconds", 20, 1, 3600);

            showDependencyScreenInPedestal = builder
                    .comment("Show dependency checklist screen when interacting with pedestal that has dependency requirements? [Default: true]")
                    .define("showDependencyScreenInPedestal", true);

            builder.pop(); // research

            // --- LOOT REPLACEMENTS SECTION ---
            builder.comment("Settings for replacing locked loot with alternatives").push("loot_replacements");

            useReplacements = builder
                    .comment("If true, locked items are replaced by specific items/tags. If false, they disappear. [Default: false]")
                    .define("useReplacements", false);

            replacementItems = builder
                    .comment("{ReplacementPriority:1} A list of Item IDs to pick from if 'useReplacements' is true. [Default: cobblestone, dirt]")
                    .defineList("replacementItems", List.of("minecraft:cobblestone", "minecraft:dirt"), o -> o instanceof String);

            replacementTags = builder
                    .comment("{ReplacementPriority:2} A list of tags (e.g. 'c:dusts') to pick a random replacement from. [Default: empty]")
                    .defineList("replacementTags", List.of(), o -> o instanceof String);
            builder.pop(); // loot_replacements

            // --- INDIVIDUAL STAGES SECTION ---
            builder.comment("Individual Stage Settings (per-player stages)").push("individual_stages");

            individualLockItemPickup = builder
                    .comment("Prevent players from picking up items locked by individual stages? [Default: true]")
                    .define("lockItemPickup", true);

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

            builder.pop(); // individual_stages

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

            builder.pop(); // structure_lock
        }
    }

    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;
    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        final Pair<Client, ModConfigSpec> clientPair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = clientPair.getLeft();
        CLIENT_SPEC = clientPair.getRight();

        final Pair<Common, ModConfigSpec> commonPair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON = commonPair.getLeft();
        COMMON_SPEC = commonPair.getRight();
    }
}
