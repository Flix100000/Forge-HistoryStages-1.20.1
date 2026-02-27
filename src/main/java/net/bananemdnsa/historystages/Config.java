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
        public final ForgeConfigSpec.BooleanValue dimUseActionbar;
        public final ForgeConfigSpec.BooleanValue dimShowChat;
        public final ForgeConfigSpec.BooleanValue dimShowStagesInChat;

        public Client(ForgeConfigSpec.Builder builder) {
            builder.comment("Visual and UI settings (Individual for each player)").push("visuals");

            hideInJei = builder
                    .comment("Hide locked items from JEI/REI/EMI? [Default: false]")
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
        }
    }

    // --- COMMON CONFIG (Server-Einstellungen und globale Logik) ---
    public static class Common {
        public final ForgeConfigSpec.BooleanValue lockMobLoot;
        public final ForgeConfigSpec.BooleanValue showDebugErrors;

        // Zentrale Benachrichtigungen (Chat, Actionbar, Sounds, Texte)
        public final ForgeConfigSpec.BooleanValue broadcastChat;
        public final ForgeConfigSpec.ConfigValue<String> unlockMessageFormat;
        public final ForgeConfigSpec.BooleanValue useActionbar;
        public final ForgeConfigSpec.BooleanValue useSounds;

        // Forschungsstation
        public final ForgeConfigSpec.IntValue researchTimeInSeconds;

        // Loot-Ersetzungen
        public final ForgeConfigSpec.BooleanValue useReplacements;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> replacementItems;
        public final ForgeConfigSpec.ConfigValue<String> replacementTag;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.comment("Gameplay and Server-side settings").push("gameplay");

            lockMobLoot = builder
                    .comment("Handle locked items in mob loot tables? [Default: true]")
                    .define("lockMobLoot", true);


            showDebugErrors = builder
                    .comment("If true, players will see debug messages in chat if a JSON stage has errors or missing items. [Default: true]")
                    .define("showDebugErrors", true);

            // --- NOTIFICATIONS SECTION ---
            builder.comment("Global Notification Settings (Server-controlled)").push("notifications");

            broadcastChat = builder
                    .comment("Show unlock/lock messages in the chat for everyone? [Default: true]")
                    .define("broadcastChat", true);

            unlockMessageFormat = builder
                    .comment("Message format for unlocks (Only for the Chat and only if 'broadcastChat' = true). Use {stage} for the name and & for colors.")
                    .define("unlockMessageFormat", "&6[History] &fThe world has entered the &b{stage}&f!");

            useActionbar = builder
                    .comment("Show messages in the actionbar for everyone? [Default: false]")
                    .define("useActionbar", false);

            useSounds = builder
                    .comment("Play notification sounds for everyone? [Default: true]")
                    .define("useSounds", true);

            builder.pop(); // Schließt "notifications"

            // --- RESEARCH Pedestial SECTION ---
            builder.comment("Research Pedestial Settings").push("research");
            researchTimeInSeconds = builder
                    .comment("How long a research process takes in seconds. [Default: 20]")
                    .defineInRange("researchTimeInSeconds", 20, 1, 3600);
            builder.pop(); // Schließt "research"

            // --- LOOT REPLACEMENTS SECTION ---
            builder.comment("Settings for replacing locked loot with alternatives").push("loot_replacements");

            useReplacements = builder
                    .comment("If true, locked items are replaced by specific items/tags. If false, they disappear. [Default: false]")
                    .define("useReplacements", false);

            replacementItems = builder
                    .comment("A list of Item IDs to pick from if 'useReplacements' is true. [Default: cobblestone, dirt]")
                    .defineList("replacementItems", List.of("minecraft:cobblestone", "minecraft:dirt"), o -> o instanceof String);

            replacementTag = builder
                    .comment("A tag (e.g. 'forge:dusts') to pick a random replacement from. [Default: empty]")
                    .define("replacementTag", "");
            builder.pop(); // Schließt "loot_replacements"

            builder.pop(); // gameplay
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