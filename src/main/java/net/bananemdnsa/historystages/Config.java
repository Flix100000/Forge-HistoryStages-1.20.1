package net.bananemdnsa.historystages;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    // --- CLIENT CONFIG (Nur Dinge, die die eigene Anzeige/UI betreffen) ---
    public static class Client {
        public final ForgeConfigSpec.BooleanValue hideInJei;
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
        public final ForgeConfigSpec.BooleanValue mobUseActionbar;
        public final ForgeConfigSpec.BooleanValue mobShowChat;
        public final ForgeConfigSpec.BooleanValue mobShowStagesInChat;

        // Individual Stages
        public final ForgeConfigSpec.BooleanValue showSilverLockIcons;
        public final ForgeConfigSpec.BooleanValue showIndividualTooltips;

        public Client(ForgeConfigSpec.Builder builder) {
            builder.comment(
                    "Found a bug or have a feature request?",
                    "Report it on GitHub: https://github.com/Flix100000/History-Stages/issues",
                    "",
                    "Visual and UI settings (Individual for each player)")
                    .push("visuals");

            hideInJei = builder
                    .comment("Hide locked items from JEI? (Only works with JEI!) [Default: false]")
                    .define("hideInJei", false);

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

        // Forschungsstation
        public final ForgeConfigSpec.IntValue researchTimeInSeconds;
        public final ForgeConfigSpec.BooleanValue showDependencyScreenInPedestal;

        // Loot-Ersetzungen
        public final ForgeConfigSpec.BooleanValue useReplacements;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> replacementItems;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> replacementTag;

        // Individual Stages - Gameplay
        public final ForgeConfigSpec.BooleanValue individualLockItemPickup;
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

            builder.pop(); // Schließt "notifications"

            // --- RESEARCH Pedestal SECTION ---
            builder.comment("Research Pedestal Settings").push("research");
            researchTimeInSeconds = builder
                    .comment("Default research time in seconds. Used as fallback if a stage does not define its own 'research_time' in the JSON. [Default: 20]")
                    .defineInRange("researchTimeInSeconds", 20, 1, 3600);

            showDependencyScreenInPedestal = builder
                    .comment("Show dependency checklist screen when interacting with pedestal that has dependency requirements? [Default: true]")
                    .define("showDependencyScreenInPedestal", true);

            builder.pop(); // Schließt "research"

            // --- LOOT REPLACEMENTS SECTION ---
            builder.comment("Settings for replacing locked loot with alternatives").push("loot_replacements");

            useReplacements = builder
                    .comment("If true, locked items are replaced by specific items/tags. If false, they disappear. [Default: false]")
                    .define("useReplacements", false);

            replacementItems = builder
                    .comment("{ReplacementPriority:1} A list of Item IDs to pick from if 'useReplacements' is true. [Default: cobblestone, dirt]")
                    .defineList("replacementItems", List.of("minecraft:cobblestone", "minecraft:dirt"), o -> o instanceof String);

            replacementTag = builder
                    .comment("{ReplacementPriority:2} A list of tags (e.g. 'forge:dusts') to pick a random replacement from. [Default: empty]")
                    .defineList("replacementTags", List.of(), o -> o instanceof String);
            builder.pop(); // Schließt "loot_replacements"

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

            builder.pop(); // Schließt "individual_stages"
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