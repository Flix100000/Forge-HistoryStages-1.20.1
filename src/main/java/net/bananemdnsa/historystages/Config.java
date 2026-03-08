package net.bananemdnsa.historystages;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.util.List;

public class Config {

    // --- CLIENT CONFIG (Nur Dinge, die die eigene Anzeige/UI betreffen) ---
    public static class Client {
        public final ModConfigSpec.BooleanValue hideInJei;
        public final ModConfigSpec.BooleanValue showTooltips;
        public final ModConfigSpec.BooleanValue showStageName;
        public final ModConfigSpec.BooleanValue showAllUntilComplete;
        public final ModConfigSpec.BooleanValue dimUseActionbar;
        public final ModConfigSpec.BooleanValue dimShowChat;
        public final ModConfigSpec.BooleanValue dimShowStagesInChat;
        public final ModConfigSpec.BooleanValue showLockIcons;
        public final ModConfigSpec.BooleanValue mobUseActionbar;
        public final ModConfigSpec.BooleanValue mobShowChat;
        public final ModConfigSpec.BooleanValue mobShowStagesInChat;

        public Client(ModConfigSpec.Builder builder) {
            builder.comment("Visual and UI settings (Individual for each player)").push("visuals");

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
        }
    }

    // --- COMMON CONFIG (Server-Einstellungen und globale Logik) ---
    public static class Common {
        public final ModConfigSpec.BooleanValue lockMobLoot;
        public final ModConfigSpec.BooleanValue showDebugErrors;

        public final ModConfigSpec.BooleanValue broadcastChat;
        public final ModConfigSpec.ConfigValue<String> unlockMessageFormat;
        public final ModConfigSpec.BooleanValue useActionbar;
        public final ModConfigSpec.BooleanValue useSounds;
        public final ModConfigSpec.BooleanValue useToasts;

        public final ModConfigSpec.IntValue researchTimeInSeconds;

        public final ModConfigSpec.BooleanValue useReplacements;
        public final ModConfigSpec.ConfigValue<List<? extends String>> replacementItems;
        public final ModConfigSpec.ConfigValue<String> replacementTag;

        public Common(ModConfigSpec.Builder builder) {
            builder.comment("Gameplay and Server-side settings").push("gameplay");

            lockMobLoot = builder
                    .comment("Handle locked items in mob loot tables? [Default: true]")
                    .define("lockMobLoot", true);

            showDebugErrors = builder
                    .comment("If true, players will see debug messages in chat if a JSON stage has errors or missing items. [Default: true]")
                    .define("showDebugErrors", true);

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

            builder.pop();

            builder.comment("Research Pedestal Settings").push("research");
            researchTimeInSeconds = builder
                    .comment("Default research time in seconds. Used as fallback if a stage does not define its own 'research_time' in the JSON. [Default: 20]")
                    .defineInRange("researchTimeInSeconds", 20, 1, 3600);
            builder.pop();

            builder.comment("Settings for replacing locked loot with alternatives").push("loot_replacements");

            useReplacements = builder
                    .comment("If true, locked items are replaced by specific items/tags. If false, they disappear. [Default: false]")
                    .define("useReplacements", false);

            replacementItems = builder
                    .comment("{ReplacementPriority:2} A list of Item IDs to pick from if 'useReplacements' is true. [Default: cobblestone, dirt]")
                    .defineList("replacementItems", List.of("minecraft:cobblestone", "minecraft:dirt"), o -> o instanceof String);

            replacementTag = builder
                    .comment("{ReplacementPriority:1} A tag (e.g. 'c:dusts') to pick a random replacement from. [Default: empty]")
                    .define("replacementTag", "");
            builder.pop();

            builder.pop();
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
