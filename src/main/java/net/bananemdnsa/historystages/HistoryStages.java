package net.bananemdnsa.historystages;

import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.client.LockDecorator;
import net.bananemdnsa.historystages.commands.StageCommand;
import net.bananemdnsa.historystages.compat.ScrollVariants;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StageMode;
import net.bananemdnsa.historystages.data.auto.AutoTriggerManager;
import net.bananemdnsa.historystages.init.*;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.clientbound.SyncConfigPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStageDefinitionsPacket;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;
import net.bananemdnsa.historystages.screen.ResearchPedestalScreen;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.data.saveddata.IndividualStageData;
import net.bananemdnsa.historystages.data.saveddata.StageData;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.util.List;

@Mod(HistoryStages.MOD_ID)
public class HistoryStages {
    public static final String MOD_ID = "historystages";
    private static final Logger LOGGER = LogUtils.getLogger();

    public static net.minecraft.resources.ResourceLocation location(String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public HistoryStages(IEventBus modEventBus, ModContainer modContainer) {
        // Must run before either config spec is registered below — see the class comment on
        // GraphConfigMigration for why capture and apply are two separate steps.
        net.bananemdnsa.historystages.data.graph.GraphConfigMigration.capture();
        // Second, never first: this one renames the old files once it has written their contents
        // into the new ones, and the graph block lives in the same common file. Reading it after
        // the rename would cost the pack its whole stage graph.
        net.bananemdnsa.historystages.data.config.LegacyConfigMigration.capture();

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        net.bananemdnsa.historystages.init.ModRecipes.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        // Hier fügen wir den Decorator hinzu:
        modEventBus.addListener(this::onRegisterItemDecorators);
        modEventBus.addListener(this::registerScreens);
        modEventBus.addListener(this::registerCapabilities);
        modEventBus.addListener(this::onConfigLoad);
        modEventBus.addListener(this::onConfigReload);

        // Type.COMMON, not CLIENT: a dedicated server never loads a CLIENT spec, so it could not
        // own these values — and owning them is the whole point of sending them to every player.
        // Not Type.SERVER either: that stores per world under saves/<world>/serverconfig/, which
        // is exactly not the one shared place under config/historystages/settings/.
        // Both specs must name their own file. NeoForge derives the default name from modid and
        // type, so two COMMON registrations without explicit paths both claim
        // historystages-common.toml and mod loading dies on the conflict.
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.VISUAL_SPEC,
                "historystages/settings/visual.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.GAMEPLAY_SPEC,
                "historystages/settings/gameplay.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, GraphConfig.GRAPH_SPEC,
                "historystages/settings/graph.toml");

        ConfigHandler.setupConfig();
        StageManager.load();

        // Registration window for addon lock categories. StageManager.load() above already
        // parsed every stage's `addons` block into raw JsonElement — that needs no registry at
        // all — so nothing upstream of this point ever needed a category to exist. Firing here,
        // once every mod has been constructed and FMLCommonSetupEvent's own parallel dispatch has
        // fully returned (postEvent is called from the deferred work queue, not from inside that
        // dispatch), lets every mod's RegisterLockCategoriesEvent listener run before the
        // registry closes for good.
        modEventBus.addListener((net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(() -> {
                    net.neoforged.fml.ModLoader.postEvent(
                            new net.bananemdnsa.historystages.api.lock.RegisterLockCategoriesEvent());
                    net.bananemdnsa.historystages.data.lock.category.LockCategories.freeze();
                    net.neoforged.fml.ModLoader.postEvent(
                            new net.bananemdnsa.historystages.api.trigger.RegisterTriggerTypesEvent());
                    net.bananemdnsa.historystages.data.auto.TriggerTypes.freeze();
                    net.neoforged.fml.ModLoader.postEvent(
                            new net.bananemdnsa.historystages.api.dependency.RegisterRequirementTypesEvent());
                    net.bananemdnsa.historystages.data.dependency.RequirementTypes.freeze();
                    net.neoforged.fml.ModLoader.postEvent(
                            new net.bananemdnsa.historystages.api.settings.RegisterStageSettingsGroupsEvent());
                    net.bananemdnsa.historystages.data.settings.StageSettingsGroups.freeze();
                    net.neoforged.fml.ModLoader.postEvent(
                            new net.bananemdnsa.historystages.api.config.RegisterConfigSectionsEvent());
                    // The freeze is what makes the section list safe to read from the config
                    // packets, which now go to AddonConfigSections directly rather than through a
                    // registry the values had to be copied into first.
                    net.bananemdnsa.historystages.data.config.AddonConfigSections.freeze();
                    net.neoforged.fml.ModLoader.postEvent(
                            new net.bananemdnsa.historystages.api.lock.RegisterIndividualRecipeSupportEvent());
                    net.bananemdnsa.historystages.data.lock.IndividualRecipeSupport.freeze();

                    // Logged here rather than inside freeze(): LockCategories is unit-tested, and
                    // the test runtime classpath has no Minecraft or NeoForge on it. This line is
                    // also how an in-game check confirms the event actually fired.
                    var addonCategories =
                            net.bananemdnsa.historystages.data.lock.category.LockCategories.addonIds();
                    LOGGER.info("[HistoryStages] Lock categories closed: {} total, {} from other mods {}",
                            net.bananemdnsa.historystages.data.lock.category.LockCategories.all().size(),
                            addonCategories.size(), addonCategories);
                    LOGGER.info("[HistoryStages] Stage settings groups closed: {} total",
                            net.bananemdnsa.historystages.data.settings.StageSettingsGroups.all().size());
                    LOGGER.info("[HistoryStages] Config sections closed: {} total",
                            net.bananemdnsa.historystages.data.config.AddonConfigSections.all().size());
                }));

        // Conditional FTB Quests integration
        if (ModList.get().isLoaded("ftbquests")) {
            try {
                net.bananemdnsa.historystages.compat.ftbquests.FTBQuestsIntegration.init();
                LOGGER.info("[HistoryStages] FTB Quests integration loaded.");
            } catch (Exception e) {
                LOGGER.error("[HistoryStages] Failed to load FTB Quests integration.", e);
            }
        }

        if (ModList.get().isLoaded("curios")) {
            try {
                NeoForge.EVENT_BUS.register(net.bananemdnsa.historystages.events.lock.CuriosEquipLockHandler.class);
                LOGGER.info("[HistoryStages] Curios integration loaded.");
            } catch (Exception e) {
                LOGGER.error("[HistoryStages] Failed to load Curios integration.", e);
            }
        }

        if (ModList.get().isLoaded("accessories")) {
            try {
                net.bananemdnsa.historystages.events.lock.AccessoriesEquipLockHandler.register();
                LOGGER.info("[HistoryStages] Accessories integration loaded.");
            } catch (Exception e) {
                LOGGER.error("[HistoryStages] Failed to load Accessories integration.", e);
            }
        }

        // Script bridges. Both mods find their own entry point — KubeJS through
        // kubejs.plugins.txt, CraftTweaker by scanning for @ZenRegister — so all that is needed
        // here is the NeoForge-side wiring that turns StageEvent into something scripts hear.
        if (ModList.get().isLoaded("kubejs")) {
            try {
                net.bananemdnsa.historystages.compat.kubejs.StageEventForwarder.register(NeoForge.EVENT_BUS);
                LOGGER.info("[HistoryStages] KubeJS integration loaded.");
            } catch (Exception e) {
                LOGGER.error("[HistoryStages] Failed to load KubeJS integration.", e);
            }
        }

        if (ModList.get().isLoaded("crafttweaker")) {
            try {
                net.bananemdnsa.historystages.compat.crafttweaker.CTScriptReloadHook.register(NeoForge.EVENT_BUS);
                LOGGER.info("[HistoryStages] CraftTweaker integration loaded.");
            } catch (Exception e) {
                LOGGER.error("[HistoryStages] Failed to load CraftTweaker integration.", e);
            }
        }

        // Optional per-mod lock adapters (custom actions that bypass vanilla interaction events).
        net.bananemdnsa.historystages.compat.LockInterceptors.init();

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new net.bananemdnsa.historystages.events.AutoTriggerEventBridge());
    }

    private void onConfigLoad(net.neoforged.fml.event.config.ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == Config.GAMEPLAY_SPEC) {
            net.bananemdnsa.historystages.data.config.ConfigDerivedCaches.rebuildGameplay();
        }
        // Its own branch: the scroll tooltip layout is read out of the visual spec, so hanging the
        // rebuild off the gameplay spec would read a file that may not be loaded yet and would miss
        // every later change to visual.toml.
        if (event.getConfig().getSpec() == Config.VISUAL_SPEC) {
            net.bananemdnsa.historystages.data.config.ConfigDerivedCaches.rebuildVisual();
        }
        // Not in the constructor: registerConfig does not load a COMMON spec, and writing into one
        // before it is loaded throws. Hung off both specs rather than just whichever loads second,
        // so it does not depend on the registration order — apply() waits until both are ready and
        // then runs exactly once.
        if (event.getConfig().getSpec() == Config.VISUAL_SPEC
                || event.getConfig().getSpec() == Config.GAMEPLAY_SPEC) {
            net.bananemdnsa.historystages.data.config.LegacyConfigMigration.apply();
        }
        if (event.getConfig().getSpec() == GraphConfig.GRAPH_SPEC) {
            net.bananemdnsa.historystages.data.graph.GraphConfigMigration.apply();
        }
    }

    private void onConfigReload(net.neoforged.fml.event.config.ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == Config.GAMEPLAY_SPEC) {
            net.bananemdnsa.historystages.data.config.ConfigDerivedCaches.rebuildGameplay();
        }
        if (event.getConfig().getSpec() == Config.VISUAL_SPEC) {
            net.bananemdnsa.historystages.data.config.ConfigDerivedCaches.rebuildVisual();
        }
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.RESEARCH_PEDESTAL_BE.get(),
                (blockEntity, side) -> blockEntity.getItemHandler()
        );
    }

    private void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenuTypes.RESEARCH_MENU.get(), ResearchPedestalScreen::new);
    }

    private void onRegisterItemDecorators(RegisterItemDecorationsEvent event) {
        // ForgeRegistries.ITEMS.forEach ist gut, aber manche Mods registrieren Items später.
        // Wir registrieren den Decorator für absolut jedes Item.
        for (Item item : BuiltInRegistries.ITEM) {
            event.register(item, new LockDecorator());
        }
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Wir fügen die Maschine bei den Funktions-Blöcken hinzu
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.RESEARCH_PEDESTAL_ITEM.get());
        }

        // Generate a research scroll for every stage (global + individual).
        // AUTO/TEMPORARY stages have no scroll (they're unlocked via auto_trigger events).
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            for (var stageEntry : StageManager.getStages().entrySet()) {
                if (stageEntry.getValue().getMode().usesAutoTrigger()) continue;
                event.accept(ScrollVariants.createScroll(stageEntry.getKey()));
            }
            for (var stageEntry : StageManager.getIndividualStages().entrySet()) {
                if (stageEntry.getValue().getMode().usesAutoTrigger()) continue;
                event.accept(ScrollVariants.createScroll(stageEntry.getKey()));
            }
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        StageCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Send stage definitions, unlocked stages, and server config to client
            PacketHandler.sendDefinitionsToPlayer(new SyncStageDefinitionsPacket(StageManager.getStages()), player);
            StageData data = StageData.get(player.serverLevel());
            PacketHandler.sendToPlayer(new SyncStagesPacket(data.getUnlockedStages()), player);
            PacketHandler.sendConfigToPlayer(SyncConfigPacket.fromServerConfig(), player);
            PacketHandler.sendGraphConfigToPlayer(
                    net.bananemdnsa.historystages.network.clientbound.SyncGraphConfigPacket
                            .fromServerConfig(), player);
            PacketHandler.sendVisualConfigToPlayer(
                    net.bananemdnsa.historystages.network.clientbound.SyncVisualConfigPacket
                            .fromServerConfig(), player);

            // Sync individual stages for this player
            IndividualStageData individualData = IndividualStageData.get(player.serverLevel());
            PacketHandler.sendIndividualStagesToPlayer(
                    new SyncIndividualStagesPacket(individualData.getUnlockedStages(player.getUUID())),
                    player
            );

            // Sync structure registry so editor UI can populate the searchable list
            PacketHandler.sendStructureRegistryToPlayer(
                    net.bananemdnsa.historystages.network.clientbound.SyncStructureRegistryPacket.fromServer(player),
                    player);

            DebugLogger.runtime("Player Login", player.getName().getString(),
                    "Synced " + StageManager.getStages().size() + " stage definitions, "
                    + data.getUnlockedStages().size() + " unlocked stages, "
                    + individualData.getUnlockedStages(player.getUUID()).size() + " individual stages");

            // Log locked items in player inventory
            logLockedInventoryItems(player);

            // Welcome message
            if (Config.VISUAL.showWelcomeMessage.get()) {
                int stageCount = StageManager.getStages().size();
                player.sendSystemMessage(Component.literal("§8§m                                                §r"));
                player.sendSystemMessage(Component.literal("  §b§lHistory Stages §7— §fWelcome!"));
                player.sendSystemMessage(Component.literal("  §7Loaded §f" + stageCount + " §7stage" + (stageCount != 1 ? "s" : "") + " from §fconfig/historystages/"));
                player.sendSystemMessage(Component.translatable("message.historystages.welcome.settings"));
                player.sendSystemMessage(Component.translatable("message.historystages.welcome.disable"));
                player.sendSystemMessage(Component.literal("§8§m                                                §r"));
            }

            // Debug error/warning messages (INFO only in log file, not in chat)
            if (Config.GAMEPLAY.showDebugErrors.get()) {
                List<StageManager.LoadingMessage> messages = StageManager.getLoadingMessages();
                List<StageManager.LoadingMessage> chatMessages = messages.stream()
                        .filter(m -> m.level() != StageManager.MessageLevel.INFO)
                        .toList();
                long infoCount = messages.size() - chatMessages.size();

                if (!chatMessages.isEmpty()) {
                    long errorCount = chatMessages.stream().filter(m -> m.level() == StageManager.MessageLevel.ERROR).count();
                    long warnCount = chatMessages.stream().filter(m -> m.level() == StageManager.MessageLevel.WARN).count();

                    // Summary header
                    StringBuilder summary = new StringBuilder("§7[HistoryStages] §fFound ");
                    if (errorCount > 0) summary.append("§c").append(errorCount).append(" error").append(errorCount != 1 ? "s" : "");
                    if (errorCount > 0 && warnCount > 0) summary.append("§f, ");
                    if (warnCount > 0) summary.append("§e").append(warnCount).append(" warning").append(warnCount != 1 ? "s" : "");
                    if (infoCount > 0) summary.append("§f (+ §b").append(infoCount).append(" info §fin log file)");
                    summary.append("§f:");
                    player.sendSystemMessage(Component.literal(summary.toString()));

                    // Show individual messages (max 10, then truncate)
                    int shown = 0;
                    for (StageManager.LoadingMessage msg : chatMessages) {
                        if (shown >= 10) {
                            player.sendSystemMessage(Component.literal("  §8... and " + (chatMessages.size() - 10) + " more (see log file)"));
                            break;
                        }
                        String prefix = switch (msg.level()) {
                            case ERROR -> "  §c[ERROR] §f";
                            case WARN ->  "  §e[WARN]  §f";
                            case INFO ->  "  §b[INFO]  §7";
                        };
                        player.sendSystemMessage(Component.literal(prefix + msg.message()));
                        shown++;
                    }

                    player.sendSystemMessage(Component.literal("  §8Full report: config/historystages/logs/"));
                    player.sendSystemMessage(Component.literal("  §8(Disable debug messages in the common config)"));
                }
            }
        }
    }

    private static boolean serverInitialized = false;

    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel sl) {
            StageData data = StageData.get(sl);
            StageData.refreshCache(data.getUnlockedStages());

            // Initialize individual stage cache
            IndividualStageData individualData = IndividualStageData.get(sl);
            individualData.refreshCache();

            // Drop AUTO-progress entries for stages that no longer exist or are no longer AUTO
            AutoTriggerManager.pruneOrphans(sl);

            // Only run once per server session (onWorldLoad fires for each dimension)
            if (!serverInitialized) {
                serverInitialized = true;
                LOGGER.info("[HistoryStages] Server cache initialized.");

                // Registry validation (registries are now fully loaded)
                StageManager.validateAgainstRegistries();
                DebugLogger.writeLogFile(StageManager.getStages(), StageManager.getIndividualStages());

                DebugLogger.initRuntimeSession();
                DebugLogger.runtime("Server", "Server started — cache initialized with "
                        + data.getUnlockedStages().size() + " unlocked stages, "
                        + StageManager.getStages().size() + " stages loaded");
            }
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        serverInitialized = false;
        DebugLogger.runtime("Server", "Server stopping — flushing runtime log");
        DebugLogger.flushRuntimeBuffer();
    }

    private static int tickCounter = 0;
    private static final int FLUSH_INTERVAL = 600; // every 30 seconds (20 ticks/s * 30s)
    private static final int CLEANUP_INTERVAL = 6000; // every 5 minutes

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        tickCounter++;

        if (tickCounter % FLUSH_INTERVAL == 0) {
            DebugLogger.flushRuntimeBuffer();
        }
        if (tickCounter % CLEANUP_INTERVAL == 0) {
            DebugLogger.cleanupThrottleMap();
        }

        net.bananemdnsa.historystages.events.AutoTriggerEventBridge.pollPlayers(event.getServer(), tickCounter);

        // Deliberately here and not in RecipeManager.apply: KubeJS and CraftTweaker rewrite
        // recipes after that call, so an index built there would miss a script pack entirely.
        // A tick has, by definition, waited for all of them. Costs one boolean read when clean.
        //
        // getOrderedRecipes rather than getRecipes: the latter is gated on the server now, and a
        // fluid-gated recipe is exactly one of the recipes it leaves out — building the index from
        // it would drop that recipe from the index, which would ungate it, which would put it back
        // in the list. This one has to see every recipe there is.
        if (event.getServer() != null) {
            net.bananemdnsa.historystages.data.lock.FluidRecipeIndex.rebuildIfDirty(
                    event.getServer().getRecipeManager().getOrderedRecipes(),
                    event.getServer().registryAccess());
        }

        // Advance temporary-mode re-lock timers / cooldowns.
        var server = event.getServer();
        if (server != null && server.overworld() != null && tickCounter % 20 == 0) {
            net.bananemdnsa.historystages.data.saveddata.TemporaryStageData.get(server.overworld())
                    .tick(server, tickCounter, HistoryStages::resolveTemporaryConfig);
        }

        // Last in the tick on purpose: a stage unlocked anywhere above asks for a resend, and in
        // the case that needs it for a datapack reload, which blocks until it is finished. Running
        // it here rather than where it was asked for turns a bundle of unlocks into one piece of
        // work, and keeps it out of the middle of whatever else was ticking.
        if (server != null) {
            net.bananemdnsa.historystages.network.PacketHandler.runRequestedLockReload(server);
        }
    }

    /** Resolves a stage id to its temporary config, checking global then individual stages. */
    public static net.bananemdnsa.historystages.data.temporary.TemporaryConfig resolveTemporaryConfig(String stageId) {
        var entry = net.bananemdnsa.historystages.data.StageManager.getStages().get(stageId);
        if (entry == null) {
            entry = net.bananemdnsa.historystages.data.StageManager.getIndividualStages().get(stageId);
        }
        return entry != null ? entry.getTemporary() : null;
    }

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer().level().isClientSide()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItemEntity().getItem();
        if (stack.isEmpty()) return;

        // Individual stages: prevent pickup of individually-locked items (respects lock_actions)
        if (Config.GAMEPLAY.individualLockItemPickup.get()
                && StageLockHelper.isActionLockedByIndividualStage(stack, player.getUUID(), "pickup")) {
            event.setCanPickup(TriState.FALSE);
            ResourceLocation itemRL = BuiltInRegistries.ITEM.getKey(stack.getItem());
            DebugLogger.runtimeThrottled("Inventory", "pickup_blocked_" + player.getUUID() + "_" + itemRL,
                    "<" + player.getName().getString() + "> Pickup of '" + itemRL + "' blocked [action: pickup]");
            return;
        }

        // Global stages: log only (existing behavior)
        if (StageLockHelper.isItemLockedForServer(stack)) {
            ResourceLocation itemRL = BuiltInRegistries.ITEM.getKey(stack.getItem());
            DebugLogger.runtimeThrottled("Inventory", "pickup_" + player.getUUID() + "_" + itemRL,
                    "<" + player.getName().getString() + "> Picked up locked '" + itemRL + "' x" + stack.getCount() + " [action: pickup]");
        }
    }

    private static void logLockedInventoryItems(ServerPlayer player) {
        java.util.List<String> lockedItems = new java.util.ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (StageLockHelper.isItemLockedForServer(stack)) {
                ResourceLocation itemRL = BuiltInRegistries.ITEM.getKey(stack.getItem());
                lockedItems.add(itemRL + " x" + stack.getCount());
            }
        }
        if (!lockedItems.isEmpty()) {
            DebugLogger.runtime("Inventory", player.getName().getString(),
                    "Has " + lockedItems.size() + " locked item stack(s) in inventory: " + String.join(", ", lockedItems));
        }
    }
}
