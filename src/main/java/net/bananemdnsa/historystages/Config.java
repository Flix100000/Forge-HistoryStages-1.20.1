package net.bananemdnsa.historystages;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_STRUCTURE_LOCK_MESSAGE = "&cYou cannot enter &e{structure}&c yet!";
    private static final Path CONFIG_FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("historystages")
            .resolve("config.json");

    public static final Common COMMON = new Common();
    public static final Client CLIENT = new Client();

    private Config() {
    }

    public static void load() {
        if (!Files.exists(CONFIG_FILE)) {
            save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
            PersistedConfig persisted = GSON.fromJson(reader, PersistedConfig.class);
            if (persisted == null) {
                save();
                return;
            }
            if (persisted.common != null) {
                copyCommon(persisted.common, COMMON);
            }
            if (persisted.client != null) {
                copyClient(persisted.client, CLIENT);
            }
            if (normalizeLegacyStructureDefaults()) {
                save();
            }
        } catch (IOException ignored) {
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE, StandardCharsets.UTF_8)) {
                PersistedConfig persisted = new PersistedConfig();
                copyCommon(COMMON, persisted.common);
                copyClient(CLIENT, persisted.client);
                GSON.toJson(persisted, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static void copyCommon(Common from, Common to) {
        to.showWelcomeMessage = from.showWelcomeMessage;
        to.showDebugErrors = from.showDebugErrors;
        to.enableRuntimeLogging = from.enableRuntimeLogging;
        to.lockMobLoot = from.lockMobLoot;
        to.lockItemUsage = from.lockItemUsage;
        to.lockEntityItems = from.lockEntityItems;
        to.individualLockItemUsage = from.individualLockItemUsage;
        to.lockBlockInteraction = from.lockBlockInteraction;
        to.individualLockBlockInteraction = from.individualLockBlockInteraction;
        to.lockBlockBreaking = from.lockBlockBreaking;
        to.individualLockBlockBreaking = from.individualLockBlockBreaking;
        to.lockedBlockBreakSpeedMultiplier = from.lockedBlockBreakSpeedMultiplier;
        to.individualLockedBlockBreakSpeedMultiplier = from.individualLockedBlockBreakSpeedMultiplier;
        to.researchTimeInSeconds = from.researchTimeInSeconds;
        to.showDependencyScreenInPedestal = from.showDependencyScreenInPedestal;
        to.broadcastChat = from.broadcastChat;
        to.unlockMessageFormat = from.unlockMessageFormat;
        to.useActionbar = from.useActionbar;
        to.useSounds = from.useSounds;
        to.useToasts = from.useToasts;
        to.defaultStageIcon = from.defaultStageIcon;
        to.useReplacements = from.useReplacements;
        to.replacementItems = java.util.List.copyOf(from.replacementItems);
        to.replacementTag = java.util.List.copyOf(from.replacementTag);
        to.individualLockItemPickup = from.individualLockItemPickup;
        to.individualDropOnRevoke = from.individualDropOnRevoke;
        to.lockContainerInteraction = from.lockContainerInteraction;
        to.lockEnchanting = from.lockEnchanting;
        to.individualLockEnchanting = from.individualLockEnchanting;
        to.individualBroadcastChat = from.individualBroadcastChat;
        to.individualUnlockMessageFormat = from.individualUnlockMessageFormat;
        to.individualUseActionbar = from.individualUseActionbar;
        to.individualUseSounds = from.individualUseSounds;
        to.individualUseToasts = from.individualUseToasts;
        to.structureMessageEnabled = from.structureMessageEnabled;
        to.structureLockInChat = from.structureLockInChat;
        to.structureDamageEnabled = from.structureDamageEnabled;
        to.structureDamageAmount = from.structureDamageAmount;
        to.structureDamageInterval = from.structureDamageInterval;
        to.structureCheckInterval = from.structureCheckInterval;
        to.structureLockMessageFormat = from.structureLockMessageFormat;
    }

    private static void copyClient(Client from, Client to) {
        to.showTooltips = from.showTooltips;
        to.showIndividualTooltips = from.showIndividualTooltips;
        to.showStageName = from.showStageName;
        to.showAllUntilComplete = from.showAllUntilComplete;
        to.jadeShowInfo = from.jadeShowInfo;
        to.jadeStageName = from.jadeStageName;
        to.jadeShowAllUntilComplete = from.jadeShowAllUntilComplete;
        to.showDependenciesOnScroll = from.showDependenciesOnScroll;
        to.hideFulfilledDependencies = from.hideFulfilledDependencies;
        to.showLockIcons = from.showLockIcons;
        to.showSilverLockIcons = from.showSilverLockIcons;
        to.dimUseActionbar = from.dimUseActionbar;
        to.dimShowChat = from.dimShowChat;
        to.dimShowStagesInChat = from.dimShowStagesInChat;
        to.mobUseActionbar = from.mobUseActionbar;
        to.mobShowChat = from.mobShowChat;
        to.mobShowStagesInChat = from.mobShowStagesInChat;
    }

    private static boolean normalizeLegacyStructureDefaults() {
        boolean changed = false;
        if (COMMON.structureDamageEnabled
                && COMMON.structureDamageInterval == 40
                && COMMON.structureCheckInterval == 20) {
            COMMON.structureDamageEnabled = false;
            COMMON.structureDamageInterval = 20;
            COMMON.structureCheckInterval = 10;
            changed = true;
        }

        String message = COMMON.structureLockMessageFormat;
        if (message == null || message.isBlank()
                || message.startsWith("This structure is locked by")
                || message.contains("{sta_")) {
            COMMON.structureLockMessageFormat = DEFAULT_STRUCTURE_LOCK_MESSAGE;
            changed = true;
        }

        if (COMMON.structureCheckInterval <= 0) {
            COMMON.structureCheckInterval = 10;
            changed = true;
        }
        if (COMMON.structureDamageInterval <= 0) {
            COMMON.structureDamageInterval = 20;
            changed = true;
        }
        if (COMMON.structureDamageAmount <= 0.0F) {
            COMMON.structureDamageAmount = 1.0F;
            changed = true;
        }

        return changed;
    }

    private static final class PersistedConfig {
        private final Common common = new Common();
        private final Client client = new Client();
    }

    public static Map<String, String> snapshotCommon() {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put("showWelcomeMessage", Boolean.toString(COMMON.showWelcomeMessage));
        values.put("showDebugErrors", Boolean.toString(COMMON.showDebugErrors));
        values.put("enableRuntimeLogging", Boolean.toString(COMMON.enableRuntimeLogging));
        values.put("lockMobLoot", Boolean.toString(COMMON.lockMobLoot));
        values.put("lockItemUsage", Boolean.toString(COMMON.lockItemUsage));
        values.put("lockEntityItems", Boolean.toString(COMMON.lockEntityItems));
        values.put("individualLockItemUsage", Boolean.toString(COMMON.individualLockItemUsage));
        values.put("individualLockItemPickup", Boolean.toString(COMMON.individualLockItemPickup));
        values.put("individualDropOnRevoke", Boolean.toString(COMMON.individualDropOnRevoke));
        values.put("lockBlockInteraction", Boolean.toString(COMMON.lockBlockInteraction));
        values.put("individualLockBlockInteraction", Boolean.toString(COMMON.individualLockBlockInteraction));
        values.put("lockBlockBreaking", Boolean.toString(COMMON.lockBlockBreaking));
        values.put("individualLockBlockBreaking", Boolean.toString(COMMON.individualLockBlockBreaking));
        values.put("lockedBlockBreakSpeedMultiplier", Float.toString(COMMON.lockedBlockBreakSpeedMultiplier));
        values.put("individualLockedBlockBreakSpeedMultiplier", Float.toString(COMMON.individualLockedBlockBreakSpeedMultiplier));
        values.put("researchTimeInSeconds", Integer.toString(COMMON.researchTimeInSeconds));
        values.put("showDependencyScreenInPedestal", Boolean.toString(COMMON.showDependencyScreenInPedestal));
        values.put("broadcastChat", Boolean.toString(COMMON.broadcastChat));
        values.put("unlockMessageFormat", String.valueOf(COMMON.unlockMessageFormat));
        values.put("useActionbar", Boolean.toString(COMMON.useActionbar));
        values.put("useSounds", Boolean.toString(COMMON.useSounds));
        values.put("useToasts", Boolean.toString(COMMON.useToasts));
        values.put("defaultStageIcon", String.valueOf(COMMON.defaultStageIcon));
        values.put("useReplacements", Boolean.toString(COMMON.useReplacements));
        values.put("lockContainerInteraction", Boolean.toString(COMMON.lockContainerInteraction));
        values.put("lockEnchanting", Boolean.toString(COMMON.lockEnchanting));
        values.put("individualLockEnchanting", Boolean.toString(COMMON.individualLockEnchanting));
        values.put("individualBroadcastChat", Boolean.toString(COMMON.individualBroadcastChat));
        values.put("individualUnlockMessageFormat", String.valueOf(COMMON.individualUnlockMessageFormat));
        values.put("individualUseActionbar", Boolean.toString(COMMON.individualUseActionbar));
        values.put("individualUseSounds", Boolean.toString(COMMON.individualUseSounds));
        values.put("individualUseToasts", Boolean.toString(COMMON.individualUseToasts));
        values.put("structureMessageEnabled", Boolean.toString(COMMON.structureMessageEnabled));
        values.put("structureLockInChat", Boolean.toString(COMMON.structureLockInChat));
        values.put("structureDamageEnabled", Boolean.toString(COMMON.structureDamageEnabled));
        values.put("structureDamageAmount", Float.toString(COMMON.structureDamageAmount));
        values.put("structureDamageInterval", Integer.toString(COMMON.structureDamageInterval));
        values.put("structureCheckInterval", Integer.toString(COMMON.structureCheckInterval));
        values.put("structureLockMessageFormat", String.valueOf(COMMON.structureLockMessageFormat));
        return values;
    }

    public static void applyEditorValues(Map<String, String> clientValues, Map<String, String> commonValues) {
        if (clientValues != null) {
            clientValues.forEach((key, value) -> {
                switch (key) {
                    case "showTooltips" -> CLIENT.showTooltips = parseBool(value, CLIENT.showTooltips);
                    case "showStageName" -> CLIENT.showStageName = parseBool(value, CLIENT.showStageName);
                    case "showAllUntilComplete" -> CLIENT.showAllUntilComplete = parseBool(value, CLIENT.showAllUntilComplete);
                    case "jadeShowInfo" -> CLIENT.jadeShowInfo = parseBool(value, CLIENT.jadeShowInfo);
                    case "jadeStageName" -> CLIENT.jadeStageName = parseBool(value, CLIENT.jadeStageName);
                    case "jadeShowAllUntilComplete" -> CLIENT.jadeShowAllUntilComplete = parseBool(value, CLIENT.jadeShowAllUntilComplete);
                    case "showIndividualTooltips" -> CLIENT.showIndividualTooltips = parseBool(value, CLIENT.showIndividualTooltips);
                    case "showDependenciesOnScroll" -> CLIENT.showDependenciesOnScroll = parseBool(value, CLIENT.showDependenciesOnScroll);
                    case "hideFulfilledDependencies" -> CLIENT.hideFulfilledDependencies = parseBool(value, CLIENT.hideFulfilledDependencies);
                    case "showLockIcons" -> CLIENT.showLockIcons = parseBool(value, CLIENT.showLockIcons);
                    case "showSilverLockIcons" -> CLIENT.showSilverLockIcons = parseBool(value, CLIENT.showSilverLockIcons);
                    case "dimUseActionbar" -> CLIENT.dimUseActionbar = parseBool(value, CLIENT.dimUseActionbar);
                    case "dimShowChat" -> CLIENT.dimShowChat = parseBool(value, CLIENT.dimShowChat);
                    case "dimShowStagesInChat" -> CLIENT.dimShowStagesInChat = parseBool(value, CLIENT.dimShowStagesInChat);
                    case "mobUseActionbar" -> CLIENT.mobUseActionbar = parseBool(value, CLIENT.mobUseActionbar);
                    case "mobShowChat" -> CLIENT.mobShowChat = parseBool(value, CLIENT.mobShowChat);
                    case "mobShowStagesInChat" -> CLIENT.mobShowStagesInChat = parseBool(value, CLIENT.mobShowStagesInChat);
                }
            });
        }
        if (commonValues != null) {
            commonValues.forEach((key, value) -> {
                switch (key) {
                    case "showWelcomeMessage" -> COMMON.showWelcomeMessage = parseBool(value, COMMON.showWelcomeMessage);
                    case "showDebugErrors" -> COMMON.showDebugErrors = parseBool(value, COMMON.showDebugErrors);
                    case "enableRuntimeLogging" -> COMMON.enableRuntimeLogging = parseBool(value, COMMON.enableRuntimeLogging);
                    case "lockMobLoot" -> COMMON.lockMobLoot = parseBool(value, COMMON.lockMobLoot);
                    case "lockItemUsage" -> COMMON.lockItemUsage = parseBool(value, COMMON.lockItemUsage);
                    case "lockEntityItems" -> COMMON.lockEntityItems = parseBool(value, COMMON.lockEntityItems);
                    case "individualLockItemUsage" -> COMMON.individualLockItemUsage = parseBool(value, COMMON.individualLockItemUsage);
                    case "individualLockItemPickup" -> COMMON.individualLockItemPickup = parseBool(value, COMMON.individualLockItemPickup);
                    case "individualDropOnRevoke" -> COMMON.individualDropOnRevoke = parseBool(value, COMMON.individualDropOnRevoke);
                    case "lockBlockInteraction" -> COMMON.lockBlockInteraction = parseBool(value, COMMON.lockBlockInteraction);
                    case "individualLockBlockInteraction" -> COMMON.individualLockBlockInteraction = parseBool(value, COMMON.individualLockBlockInteraction);
                    case "lockBlockBreaking" -> COMMON.lockBlockBreaking = parseBool(value, COMMON.lockBlockBreaking);
                    case "individualLockBlockBreaking" -> COMMON.individualLockBlockBreaking = parseBool(value, COMMON.individualLockBlockBreaking);
                    case "lockedBlockBreakSpeedMultiplier" -> COMMON.lockedBlockBreakSpeedMultiplier = parseFloat(value, COMMON.lockedBlockBreakSpeedMultiplier);
                    case "individualLockedBlockBreakSpeedMultiplier" -> COMMON.individualLockedBlockBreakSpeedMultiplier = parseFloat(value, COMMON.individualLockedBlockBreakSpeedMultiplier);
                    case "researchTimeInSeconds" -> COMMON.researchTimeInSeconds = parseInt(value, COMMON.researchTimeInSeconds);
                    case "showDependencyScreenInPedestal" -> COMMON.showDependencyScreenInPedestal = parseBool(value, COMMON.showDependencyScreenInPedestal);
                    case "broadcastChat" -> COMMON.broadcastChat = parseBool(value, COMMON.broadcastChat);
                    case "unlockMessageFormat" -> COMMON.unlockMessageFormat = value;
                    case "useActionbar" -> COMMON.useActionbar = parseBool(value, COMMON.useActionbar);
                    case "useSounds" -> COMMON.useSounds = parseBool(value, COMMON.useSounds);
                    case "useToasts" -> COMMON.useToasts = parseBool(value, COMMON.useToasts);
                    case "defaultStageIcon" -> COMMON.defaultStageIcon = value;
                    case "useReplacements" -> COMMON.useReplacements = parseBool(value, COMMON.useReplacements);
                    case "lockContainerInteraction" -> COMMON.lockContainerInteraction = parseBool(value, COMMON.lockContainerInteraction);
                    case "lockEnchanting" -> COMMON.lockEnchanting = parseBool(value, COMMON.lockEnchanting);
                    case "individualLockEnchanting" -> COMMON.individualLockEnchanting = parseBool(value, COMMON.individualLockEnchanting);
                    case "individualBroadcastChat" -> COMMON.individualBroadcastChat = parseBool(value, COMMON.individualBroadcastChat);
                    case "individualUnlockMessageFormat" -> COMMON.individualUnlockMessageFormat = value;
                    case "individualUseActionbar" -> COMMON.individualUseActionbar = parseBool(value, COMMON.individualUseActionbar);
                    case "individualUseSounds" -> COMMON.individualUseSounds = parseBool(value, COMMON.individualUseSounds);
                    case "individualUseToasts" -> COMMON.individualUseToasts = parseBool(value, COMMON.individualUseToasts);
                    case "structureMessageEnabled" -> COMMON.structureMessageEnabled = parseBool(value, COMMON.structureMessageEnabled);
                    case "structureLockInChat" -> COMMON.structureLockInChat = parseBool(value, COMMON.structureLockInChat);
                    case "structureDamageEnabled" -> COMMON.structureDamageEnabled = parseBool(value, COMMON.structureDamageEnabled);
                    case "structureDamageAmount" -> COMMON.structureDamageAmount = parseFloat(value, COMMON.structureDamageAmount);
                    case "structureDamageInterval" -> COMMON.structureDamageInterval = parseInt(value, COMMON.structureDamageInterval);
                    case "structureCheckInterval" -> COMMON.structureCheckInterval = parseInt(value, COMMON.structureCheckInterval);
                    case "structureLockMessageFormat" -> COMMON.structureLockMessageFormat = value;
                }
            });
        }
    }

    private static boolean parseBool(String value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public static final class Common {
        public boolean showWelcomeMessage = true;
        public boolean showDebugErrors = true;
        public boolean enableRuntimeLogging = false;
        public boolean lockMobLoot = true;
        public boolean lockItemUsage = true;
        public boolean lockEntityItems = true;
        public boolean individualLockItemUsage = true;
        public boolean individualLockItemPickup = true;
        public boolean individualDropOnRevoke = true;
        public boolean lockBlockInteraction = true;
        public boolean individualLockBlockInteraction = true;
        public boolean lockBlockBreaking = true;
        public boolean individualLockBlockBreaking = true;
        public float lockedBlockBreakSpeedMultiplier = 0.05F;
        public float individualLockedBlockBreakSpeedMultiplier = 0.05F;
        public int researchTimeInSeconds = 20;
        public boolean showDependencyScreenInPedestal = true;
        public boolean broadcastChat = true;
        public String unlockMessageFormat = "&fThe world has entered the &b{stage}&f!";
        public boolean useActionbar = false;
        public boolean useSounds = true;
        public boolean useToasts = true;
        public String defaultStageIcon = "historystages:research_scroll";
        public boolean useReplacements = false;
        public java.util.List<String> replacementItems = new java.util.ArrayList<>(java.util.List.of("minecraft:cobblestone", "minecraft:dirt"));
        public java.util.List<String> replacementTag = new java.util.ArrayList<>();
        public boolean lockContainerInteraction = true;
        public boolean lockEnchanting = true;
        public boolean individualLockEnchanting = true;
        public boolean individualBroadcastChat = true;
        public String individualUnlockMessageFormat = "&fYou have unlocked &b{stage}&f!";
        public boolean individualUseActionbar = false;
        public boolean individualUseSounds = true;
        public boolean individualUseToasts = true;
        public boolean structureMessageEnabled = true;
        public boolean structureLockInChat = false;
        public boolean structureDamageEnabled = false;
        public float structureDamageAmount = 1.0F;
        public int structureDamageInterval = 20;
        public int structureCheckInterval = 10;
        public String structureLockMessageFormat = DEFAULT_STRUCTURE_LOCK_MESSAGE;
    }

    public static final class Client {
        public boolean showTooltips = true;
        public boolean showIndividualTooltips = true;
        public boolean showStageName = true;
        public boolean showAllUntilComplete = true;
        public boolean jadeShowInfo = true;
        public boolean jadeStageName = true;
        public boolean jadeShowAllUntilComplete = true;
        public boolean showDependenciesOnScroll = true;
        public boolean hideFulfilledDependencies = false;
        public boolean showLockIcons = true;
        public boolean showSilverLockIcons = true;
        public boolean dimUseActionbar = true;
        public boolean dimShowChat = false;
        public boolean dimShowStagesInChat = true;
        public boolean mobUseActionbar = true;
        public boolean mobShowChat = false;
        public boolean mobShowStagesInChat = true;
    }
}
