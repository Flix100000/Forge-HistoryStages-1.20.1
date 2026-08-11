package net.bananemdnsa.historystages;

import net.bananemdnsa.historystages.data.ScrollCompletion;
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
        public final ModConfigSpec.BooleanValue showBoosterTooltips;
        public final ModConfigSpec.BooleanValue showScrollTierTooltip;
        public final ModConfigSpec.BooleanValue structureBorderEnabled;
        public final ModConfigSpec.DoubleValue structureBorderDistance;
        public final ModConfigSpec.BooleanValue structureLockOverlayEnabled;
        public final ModConfigSpec.DoubleValue structureLockOverlayOpacity;
        public final ModConfigSpec.BooleanValue mobUseActionbar;
        public final ModConfigSpec.BooleanValue mobShowChat;
        public final ModConfigSpec.BooleanValue mobShowStagesInChat;

        // Individual Stages
        public final ModConfigSpec.BooleanValue showSilverLockIcons;
        public final ModConfigSpec.BooleanValue showIndividualTooltips;

        // JEI Hiding (Issue #64)
        public final ModConfigSpec.BooleanValue hideLockedItemsInJei;
        public final ModConfigSpec.BooleanValue hideLockedRecipesInJei;
        public final ModConfigSpec.EnumValue<MultiStagePolicy> lockedItemMultiStagePolicy;

        public enum MultiStagePolicy {
            STRICT,   // locked while ANY assigned stage is locked
            LENIENT   // unlocked as soon as ANY assigned stage is unlocked
        }

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
                    .comment("Show a lock icon overlay on locked items in JEI/EMI and Inventories? [Default: true]")
                    .define("showLockIcons", true);

            showBoosterTooltips = builder
                    .comment("Show a tooltip on Research Pedestal booster blocks describing their speed/cost effect? [Default: true]")
                    .define("showBoosterTooltips", true);

            showScrollTierTooltip = builder
                    .comment("Show the minimum required Pedestal tier on Research Scroll tooltips? [Default: true]")
                    .define("showScrollTierTooltip", true);

            builder.pop();

            builder.comment("Visual feedback for locked structures (border + overlay)").push("structure_overlay");

            structureBorderEnabled = builder
                    .comment("Render a force-field-style border on the walls of locked structures when you get close? [Default: true]")
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

            builder.comment("JEI integration — fully hide locked items/recipes instead of using the lock overlay")
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
        public final ModConfigSpec.ConfigValue<List<? extends String>> researchBoosters;
        public final ModConfigSpec.BooleanValue lockScrollWhileResearching;
        public final ModConfigSpec.ConfigValue<String> defaultScrollCompletion;

        // Loot-Ersetzungen
        public final ModConfigSpec.BooleanValue useReplacements;
        public final ModConfigSpec.ConfigValue<List<? extends String>> replacementItems;
        public final ModConfigSpec.ConfigValue<List<? extends String>> replacementTags;

        // Scroll tooltip
        public final ModConfigSpec.ConfigValue<List<? extends String>> scrollTooltipLines;
        public final ModConfigSpec.BooleanValue hideFulfilledDependencies;

        // Open scroll document
        public final ModConfigSpec.ConfigValue<List<? extends String>> openScrollChapters;
        public final ModConfigSpec.ConfigValue<String> openScrollLockedDisplay;

        // Individual Stages - Gameplay
        public final ModConfigSpec.BooleanValue individualLockItemPickup;
        public final ModConfigSpec.BooleanValue individualLockLoot;
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
        public final ModConfigSpec.IntValue structureLockPadding;
        public final ModConfigSpec.IntValue structureClusterDistance;
        public final ModConfigSpec.BooleanValue structureBlockRightClick;
        public final ModConfigSpec.BooleanValue structureBlockLeftClick;
        public final ModConfigSpec.BooleanValue structureBlockProjectiles;

        public final ModConfigSpec.IntValue biomeCheckInterval;
        public final ModConfigSpec.BooleanValue biomeEffectsEnabled;
        public final ModConfigSpec.ConfigValue<List<? extends String>> biomeEffects;
        public final ModConfigSpec.BooleanValue biomeClearEffectsOnLeave;
        public final ModConfigSpec.BooleanValue biomeMessageEnabled;
        public final ModConfigSpec.ConfigValue<String> biomeLockMessageFormat;
        public final ModConfigSpec.BooleanValue biomeLockInChat;
        public final ModConfigSpec.BooleanValue biomeDamageEnabled;
        public final ModConfigSpec.DoubleValue biomeDamageAmount;
        public final ModConfigSpec.IntValue biomeDamageInterval;
        public final ModConfigSpec.BooleanValue biomeBlockRightClick;
        public final ModConfigSpec.BooleanValue biomeBlockLeftClick;
        public final ModConfigSpec.BooleanValue biomeBlockProjectiles;

        // Lock-Message Overrides (leer = Translation Key wird verwendet)
        public final ModConfigSpec.ConfigValue<String> msgDimensionUnknown;
        public final ModConfigSpec.ConfigValue<String> msgMobUnknown;
        public final ModConfigSpec.ConfigValue<String> msgItemLocked;
        public final ModConfigSpec.ConfigValue<String> msgBlockLocked;
        public final ModConfigSpec.ConfigValue<String> msgEntityItemLocked;
        public final ModConfigSpec.ConfigValue<String> msgEnchantmentLocked;

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
                    .defineInRange("researchTimeInSeconds", 20, 1, 86400);

            showDependencyScreenInPedestal = builder
                    .comment("Show dependency checklist screen when interacting with pedestal that has dependency requirements? [Default: true]")
                    .define("showDependencyScreenInPedestal", true);

            researchBoosters = builder
                    .comment(
                            "Booster blocks placed directly UNDER a Research Pedestal modify the active research.",
                            "Format per entry: \"block_id, speed_percent, cost_percent, tier, mode\"",
                            "  speed_percent: research time reduction (0-90). 90% = max (research runs 10x).",
                            "  cost_percent:  item-dependency count reduction (0-90). Locked into the scroll on first deposit.",
                            "  tier:          minimum pedestal tier the booster works under (1-4).",
                            "  mode:          'min' = this tier and higher, 'exact' = only this tier.",
                            "Unknown block ids and out-of-range values are logged and skipped/clamped.")
                    .defineListAllowEmpty("researchBoosters",
                            List.of(),
                            obj -> obj instanceof String);

            lockScrollWhileResearching = builder
                    .comment("Lock the scroll in the pedestal once research has started? Prevents players (and hoppers) from removing the scroll until research completes or the pedestal is broken. [Default: false]")
                    .define("lockScrollWhileResearching", false);

            defaultScrollCompletion = builder
                    .comment(
                            "What happens to a research scroll when its research finishes.",
                            "  consume: the scroll is used up (behaviour before this option existed).",
                            "  replace: a fresh scroll for the same stage is placed back into the pedestal,",
                            "           so the next player can research it without needing a second copy.",
                            "  open:    an open scroll is placed into the pedestal as a keepsake. No refill.",
                            "A single stage can override this with its own 'scroll_completion'. [Default: consume]")
                    .define("defaultScrollCompletion", "consume",
                            o -> o instanceof String s && ScrollCompletion.isKnown(s));

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

            builder.pop(); // structure_lock

            // --- BIOME LOCK SECTION ---
            builder.comment("Biome Lock Settings (punishes players standing in a biome they haven't unlocked yet)").push("biome_lock");

            biomeCheckInterval = builder
                    .comment("How often (in ticks) to re-check a player's biome even when they haven't moved to a new biome cell. Moving always re-checks immediately. [Default: 10]")
                    .defineInRange("checkInterval", 10, 1, 200);

            biomeEffectsEnabled = builder
                    .comment("Apply the potion effects listed below while the player is inside a locked biome? [Default: true]")
                    .define("effectsEnabled", true);

            biomeEffects = builder
                    .comment(
                            "Potion effects applied while the player stands in a locked biome.",
                            "Format per entry: \"effect_id, seconds, amplifier\"",
                            "  seconds:   how long the effect lasts. It is refreshed while the player stays inside,",
                            "             so this is really 'how long it lingers after leaving' (1-3600).",
                            "  amplifier: 0 = level I, 1 = level II, ... (0-255).",
                            "Unknown effect ids are logged once and skipped.")
                    .defineListAllowEmpty("effects",
                            List.of("minecraft:blindness, 30, 0"),
                            obj -> obj instanceof String);

            biomeClearEffectsOnLeave = builder
                    .comment("Remove those effects the moment the player leaves the locked biome, instead of letting them run out? [Default: false]")
                    .define("clearEffectsOnLeave", false);

            biomeMessageEnabled = builder
                    .comment("Show the player a message while they are inside a locked biome? [Default: true]")
                    .define("messageEnabled", true);

            biomeLockMessageFormat = builder
                    .comment("Message format for biome lock. Use {biome} for the biome ID, {stage} for the required stage, and & for colors.")
                    .define("messageFormat", "&cYou cannot survive in &e{biome}&c yet!");

            biomeLockInChat = builder
                    .comment("Show the biome lock message in chat as well (otherwise only actionbar)? [Default: false]")
                    .define("showInChat", false);

            biomeDamageEnabled = builder
                    .comment("Damage the player while they are inside a locked biome? [Default: true]")
                    .define("damageEnabled", true);

            biomeDamageAmount = builder
                    .comment("Amount of damage dealt per damage tick. [Default: 1.0]")
                    .defineInRange("damageAmount", 1.0, 0.1, 100.0);

            biomeDamageInterval = builder
                    .comment("How often (in ticks) to deal damage while inside a locked biome. [Default: 20]")
                    .defineInRange("damageInterval", 20, 1, 600);

            biomeBlockRightClick = builder
                    .comment("Cancel ALL right-click interactions (blocks, items, entities) while the player is inside a locked biome? [Default: true]")
                    .define("blockRightClick", true);

            biomeBlockLeftClick = builder
                    .comment("Cancel ALL left-click interactions (attacking entities, breaking blocks) while the player is inside a locked biome? [Default: true]")
                    .define("blockLeftClick", true);

            biomeBlockProjectiles = builder
                    .comment("Cancel projectiles (arrows, snowballs, ender pearls, etc.) the moment they would impact inside a locked biome? [Default: true]")
                    .define("blockProjectiles", true);

            builder.pop(); // biome_lock

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

            builder.comment(
                    "Layout of the Research Scroll tooltip.",
                    "Each entry is one line: id|enabled|spacerBefore|style|text",
                    "  text  empty = use the built-in translation",
                    "  style empty = use the line's built-in colour;",
                    "        otherwise ChatFormatting names joined with '+', e.g. gray+italic",
                    "The order of the movable ids (individual_badge, owner, info1, info2, tier,",
                    "dependencies) is the order they render in. Unknown ids are ignored, missing",
                    "ones fall back to their default, so an update can add lines safely.",
                    "Easiest way to edit this is the in-game config editor.")
                    .push("scroll_tooltip");

            scrollTooltipLines = builder
                    .comment("The tooltip lines, in render order.")
                    .defineList("lines",
                            net.bananemdnsa.historystages.data.tooltip.ScrollTooltipLayout.defaultsEncoded(),
                            entry -> entry instanceof String);

            hideFulfilledDependencies = builder
                    .comment("Hide already fulfilled dependencies in scroll tooltips? [Default: false]")
                    .define("hideFulfilledDependencies", false);

            builder.pop(); // scroll_tooltip

            builder.comment("The document an Open Scroll shows when right-clicked.",
                            "Chapters are drawn in the order they appear below.",
                            "Each entry is one chapter: id|enabled|mode",
                            "  id   overview, items, creatures, world",
                            "  mode icons or text; overview and world are always text",
                            "Unknown ids are ignored and missing ones fall back to their default,",
                            "so an update can add chapters safely.")
                    .push("open_scroll");

            openScrollChapters = builder
                    .comment("The chapters, in tab order.")
                    .defineList("chapters",
                            net.bananemdnsa.historystages.data.scroll.OpenScrollChapters.defaultsEncoded(),
                            entry -> entry instanceof String);

            openScrollLockedDisplay = builder
                    .comment("What a reader sees for a stage they have not unlocked.",
                            "visible  = everything readable, the scroll is just a record",
                            "obscured = locked entries as silhouettes, names in enchanting glyphs",
                            "[Default: obscured]")
                    .define("lockedDisplay",
                            net.bananemdnsa.historystages.data.scroll.OpenScrollVisibility.OBSCURED.serialize());

            builder.pop(); // open_scroll
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
