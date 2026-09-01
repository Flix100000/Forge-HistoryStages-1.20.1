package net.bananemdnsa.historystages.data.config;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Where every setting from the pre-6.0 config files lives now.
 *
 * <p>Up to 5.x the settings were split by <em>who owned the value</em>:
 * {@code historystages-client.toml} for the ones each player set for themselves,
 * {@code historystages-common.toml} for the ones the server decided. 6.0 splits them by
 * <em>what the value does</em> instead — {@code visual.toml} for everything a player sees, reads
 * or hears, {@code gameplay.toml} for everything that happens in the background. So a setting can
 * cross files, and two blocks did not survive the move: {@code [messages]} split into one visual
 * key and the new {@code [logging]} block, and the notification half of
 * {@code [individual_stages]} became {@code [notifications.individual]}.
 *
 * <p>Nothing here touches NeoForge, nightconfig or Minecraft on purpose. This table is the part
 * worth guarding with a plain JUnit test, and the test source set has none of those on its
 * classpath — see build.gradle. {@link LegacyConfigMigration} is where the file reading and the
 * spec writing happen.
 *
 * <p><strong>Delete in {@value #REMOVE_IN}.</strong> By then every pack that was going to update
 * has updated, and a table this size only rots once nothing exercises it. Delete alongside it:
 * {@link LegacyConfigMigration}, {@code LegacyConfigMapCoverageTest}, and the two
 * {@code LegacyConfigMigration} calls in {@code HistoryStages} — the {@code capture()} in the mod
 * constructor and the {@code apply()} in the config-loading listener.
 */
public final class LegacyConfigMap {

    /** Which of the two 6.0 files a setting ended up in. */
    public enum Target { VISUAL, GAMEPLAY }

    /** A setting's new home: the file it lives in, and its dotted path inside that file. */
    public record Destination(Target target, String path) {}

    /** The version this whole migration is scheduled to be removed in. */
    public static final String REMOVE_IN = "6.3";

    /** The old client file, spelled the way {@link #lookup} wants to be told about it. */
    public static final String CLIENT = "client";

    /** The old common file, spelled the way {@link #lookup} wants to be told about it. */
    public static final String COMMON = "common";

    private static final Map<String, Destination> MOVES = buildMoves();
    private static final Map<String, String> DROPPED = buildDropped();

    /**
     * Settings that did not exist before the split, as {@code "<TARGET>|<path>"}.
     *
     * <p>They have no old home to come from, and that is not an omission: a setting nobody could
     * have configured in 5.x cannot lose a value on the way over. Without this list the coverage
     * test would demand a migration entry for them, and the only way to satisfy it would be to
     * claim an old key that never existed — a lie the table would then carry until 6.3.
     */
    private static final Set<String> ADDED_SINCE = Set.of(
            // Individual stages could not gate recipes at all before 6.0, so neither the switch
            // nor the message a blocked recipe-book click sends had anything to configure.
            "GAMEPLAY|individual_stages.lockRecipes",
            "VISUAL|lock_messages.recipeLocked",
            // Fluids were not a lock category before 6.0, so there was nothing to refuse and no
            // message to word.
            "VISUAL|lock_messages.fluidLocked",
            // The vanilla recipe book showed locked recipes before 6.0; there was no switch.
            "VISUAL|recipe_book.hideLockedRecipesInBook",
            // The pause-menu editor button was always there for operators before 6.0.
            "VISUAL|visuals.showEditorButton");

    private LegacyConfigMap() {}

    /**
     * The new home of an old setting, or null when the table does not know it.
     *
     * <p>Null is not the same as "dropped". Ask {@link #droppedReason} before reporting an unknown
     * key, or a setting we removed on purpose gets announced as one that went missing.
     *
     * @param oldFile {@link #CLIENT} or {@link #COMMON}. Both files had an
     *                {@code [individual_stages]} block, holding different keys, so the file is
     *                part of a setting's identity rather than decoration.
     * @param oldPath the dotted path inside that file, e.g. {@code notifications.useSounds}
     */
    public static Destination lookup(String oldFile, String oldPath) {
        return MOVES.get(key(oldFile, oldPath));
    }

    /**
     * Why an old setting has no new home, or null when it is not a known removal.
     *
     * <p>Everything listed here went away deliberately, so the migration stays quiet about it
     * instead of telling a pack author their setting was lost.
     */
    public static String droppedReason(String oldFile, String oldPath) {
        return DROPPED.get(key(oldFile, oldPath));
    }

    /**
     * Every destination in the table, as {@code "<TARGET>|<path>"}.
     *
     * <p>Exists for the coverage test. A current config key that appears in no destination is a
     * setting every updating pack silently loses, and nothing about that is visible until someone
     * complains months later that their file reset itself.
     */
    public static Set<String> destinations() {
        Set<String> out = new HashSet<>();
        for (Destination destination : MOVES.values()) {
            out.add(destination.target().name() + "|" + destination.path());
        }
        return out;
    }

    /**
     * Every setting the table can account for: the ones that moved, plus the ones that are new.
     *
     * <p>What the coverage test asks against. A key in neither half is one an updating pack
     * silently loses.
     */
    public static Set<String> accountedFor() {
        Set<String> out = destinations();
        out.addAll(ADDED_SINCE);
        return out;
    }

    private static String key(String oldFile, String oldPath) {
        return oldFile + "|" + oldPath;
    }

    private static Map<String, Destination> buildMoves() {
        Map<String, Destination> map = new LinkedHashMap<>();

        // --- historystages-client.toml -> visual.toml ---
        // The whole client file became visual settings, every path unchanged. Not a coincidence:
        // "what a player sees" is close to what the client file already held.
        unchanged(map, CLIENT, Target.VISUAL, "visuals",
                "showTooltips", "showStageName", "showAllUntilComplete", "showLockIcons",
                "showBoosterTooltips", "showScrollTierTooltip", "openScrollBackdrop");
        unchanged(map, CLIENT, Target.VISUAL, "structure_overlay",
                "structureBorderEnabled", "structureBorderDistance",
                "structureLockOverlayEnabled", "structureLockOverlayOpacity");
        unchanged(map, CLIENT, Target.VISUAL, "jade",
                "showInfo", "showStageName", "showAllUntilComplete");
        unchanged(map, CLIENT, Target.VISUAL, "dimension_lock",
                "useActionbar", "showInChat", "showStagesInChat");
        unchanged(map, CLIENT, Target.VISUAL, "mob_lock",
                "useActionbar", "showInChat", "showStagesInChat");
        unchanged(map, CLIENT, Target.VISUAL, "individual_stages",
                "showSilverLockIcons", "showIndividualTooltips");
        unchanged(map, CLIENT, Target.VISUAL, "jei_hiding",
                "hideLockedItemsInJei", "hideLockedRecipesInJei", "lockedItemMultiStagePolicy");

        // --- historystages-common.toml -> visual.toml ---
        // [messages] held one line a player reads and two things only the log cares about, which
        // is why the block did not survive: this key went visual, the other two went to [logging].
        moved(map, COMMON, "messages.showWelcomeMessage",
                Target.VISUAL, "visuals.showWelcomeMessage");

        unchanged(map, COMMON, Target.VISUAL, "notifications",
                "broadcastChat", "unlockMessageFormat", "useActionbar", "useSounds", "useToasts",
                "defaultStageIcon");

        // The old [individual_stages] block mixed notification settings with gameplay ones. The
        // notification half now sits under [notifications.individual], beside the global
        // notifications it mirrors key for key; the gameplay half kept its block and changed file.
        moved(map, COMMON, "individual_stages.broadcastChat",
                Target.VISUAL, "notifications.individual.broadcastChat");
        moved(map, COMMON, "individual_stages.unlockMessageFormat",
                Target.VISUAL, "notifications.individual.unlockMessageFormat");
        moved(map, COMMON, "individual_stages.useActionbar",
                Target.VISUAL, "notifications.individual.useActionbar");
        moved(map, COMMON, "individual_stages.useSounds",
                Target.VISUAL, "notifications.individual.useSounds");
        moved(map, COMMON, "individual_stages.useToasts",
                Target.VISUAL, "notifications.individual.useToasts");

        unchanged(map, COMMON, Target.VISUAL, "lock_messages",
                "dimensionUnknown", "mobUnknown", "itemLocked", "blockLocked", "entityItemLocked",
                "enchantmentLocked");
        unchanged(map, COMMON, Target.VISUAL, "scroll_tooltip",
                "lines", "hideFulfilledDependencies");
        unchanged(map, COMMON, Target.VISUAL, "open_scroll",
                "chapters", "lockedDisplay", "overviewBlocks", "showSearch", "showEntryIds",
                "entrySort", "inkHeading", "inkBody", "inkFaint");

        // --- historystages-common.toml -> gameplay.toml ---
        moved(map, COMMON, "messages.showDebugErrors",
                Target.GAMEPLAY, "logging.showDebugErrors");
        moved(map, COMMON, "messages.enableRuntimeLogging",
                Target.GAMEPLAY, "logging.enableRuntimeLogging");

        unchanged(map, COMMON, Target.GAMEPLAY, "gameplay",
                "lockMobLoot", "lockBlockBreaking", "lockedBlockBreakSpeedMultiplier",
                "lockItemUsage", "lockEntityItems", "lockBlockInteraction",
                "lockContainerInteraction", "lockEnchanting");
        unchanged(map, COMMON, Target.GAMEPLAY, "research",
                "researchTimeInSeconds", "researchBoosters", "defaultScrollCompletion",
                "enableScrollResealing");
        unchanged(map, COMMON, Target.GAMEPLAY, "loot_replacements",
                "useReplacements", "replacementItems", "replacementTags");
        unchanged(map, COMMON, Target.GAMEPLAY, "individual_stages",
                "lockItemPickup", "lockLoot", "dropOnRevoke", "lockBlockBreaking",
                "lockedBlockBreakSpeedMultiplier", "lockItemUsage", "lockBlockInteraction",
                "lockEnchanting");
        unchanged(map, COMMON, Target.GAMEPLAY, "structure_lock",
                "checkInterval", "messageEnabled", "messageFormat", "showInChat", "damageEnabled",
                "damageAmount", "damageInterval", "lockPadding", "clusterDistance",
                "blockRightClick", "blockLeftClick", "blockProjectiles");
        unchanged(map, COMMON, Target.GAMEPLAY, "biome_lock",
                "checkInterval", "effectsEnabled", "effects", "clearEffectsOnLeave",
                "messageEnabled", "messageFormat", "showInChat", "damageEnabled", "damageAmount",
                "damageInterval", "blockRightClick", "blockLeftClick", "blockProjectiles");

        return map;
    }

    private static Map<String, String> buildDropped() {
        Map<String, String> map = new LinkedHashMap<>();

        // Both were editable, synced and saved, and read by nothing at all. Carrying a value
        // nobody reads into the new file would only make it look like it still did something.
        String unread = "removed in 6.0 — it was saved and synced but never read by anything";
        map.put(key(COMMON, "research.lockScrollWhileResearching"), unread);
        map.put(key(COMMON, "research.showDependencyScreenInPedestal"), unread);

        // The [graph] block has its own migration into graph.toml. Claiming it here would migrate
        // it twice; reporting it as unknown would send pack authors looking for a setting that is
        // perfectly well looked after.
        for (String key : new String[]{"enabled", "respectHiddenDisplay", "showIndividualStages",
                "showStageElements", "showTriggers", "visibility"}) {
            map.put(key(COMMON, "graph." + key), "owned by graph.toml — see GraphConfigMigration");
        }

        return map;
    }

    /** Same block, same key, different file. */
    private static void unchanged(Map<String, Destination> map, String oldFile, Target target,
                                  String block, String... keys) {
        for (String key : keys) {
            String path = block + "." + key;
            map.put(key(oldFile, path), new Destination(target, path));
        }
    }

    /** A setting whose path changed, spelled out both ways so the move reads off the line. */
    private static void moved(Map<String, Destination> map, String oldFile, String oldPath,
                              Target target, String newPath) {
        map.put(key(oldFile, oldPath), new Destination(target, newPath));
    }
}
