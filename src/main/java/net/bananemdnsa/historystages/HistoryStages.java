package net.bananemdnsa.historystages;

import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.client.LockDecorator;
import net.bananemdnsa.historystages.commands.StageCommand;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.init.*;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SyncConfigPacket;
import net.bananemdnsa.historystages.network.SyncIndividualStagesPacket;
import net.bananemdnsa.historystages.network.SyncStageDefinitionsPacket;
import net.bananemdnsa.historystages.network.SyncStagesPacket;
import net.bananemdnsa.historystages.screen.ResearchPedestalScreen;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.IndividualStageData;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.List;

@Mod(HistoryStages.MOD_ID)
public class HistoryStages {
    public static final String MOD_ID = "historystages";
    private static final Logger LOGGER = LogUtils.getLogger();

    public HistoryStages() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        modEventBus.addListener(this::clientSetup);

        modEventBus.addListener(this::addCreative);
        // Hier fügen wir den Decorator hinzu:
        modEventBus.addListener(this::onRegisterItemDecorators);

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);

        PacketHandler.register();
        ConfigHandler.setupConfig();
        StageManager.load();

        if (ModList.get().isLoaded("ftbquests")) {
            try {
                net.bananemdnsa.historystages.ftbquests.FTBQuestsIntegration.init();
                LOGGER.info("[HistoryStages] FTB Quests integration loaded.");
            } catch (Exception e) {
                LOGGER.error("[HistoryStages] Failed to load FTB Quests integration.", e);
            }
        }

        modEventBus.addListener(this::addCreative);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onRegisterItemDecorators(RegisterItemDecorationsEvent event) {
        // ForgeRegistries.ITEMS.forEach ist gut, aber manche Mods registrieren Items später.
        // Wir registrieren den Decorator für absolut jedes Item.
        for (Item item : ForgeRegistries.ITEMS) {
            event.register(item, new LockDecorator());
        }
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Wir fügen die Maschine bei den Funktions-Blöcken hinzu
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.RESEARCH_PEDESTAL_ITEM);
        }

        // Generate a research scroll for every stage (global + individual)
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            for (String stageId : StageManager.getStages().keySet()) {
                ItemStack book = new ItemStack(ModItems.RESEARCH_SCROLL.get());
                CompoundTag nbt = book.getOrCreateTag();
                nbt.putString("StageResearch", stageId);
                event.accept(book);
            }
            for (String stageId : StageManager.getIndividualStages().keySet()) {
                ItemStack book = new ItemStack(ModItems.RESEARCH_SCROLL.get());
                CompoundTag nbt = book.getOrCreateTag();
                nbt.putString("StageResearch", stageId);
                event.accept(book);
            }
        }
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.RESEARCH_MENU.get(), ResearchPedestalScreen::new);
        });
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

            // Sync individual stages for this player
            IndividualStageData individualData = IndividualStageData.get(player.serverLevel());
            PacketHandler.sendIndividualStagesToPlayer(
                    new SyncIndividualStagesPacket(individualData.getUnlockedStages(player.getUUID())),
                    player
            );

            DebugLogger.runtime("Player Login", player.getName().getString(),
                    "Synced " + StageManager.getStages().size() + " stage definitions, " + data.getUnlockedStages().size() + " unlocked stages"
                    + ", " + individualData.getUnlockedStages(player.getUUID()).size() + " individual stages");

            // Log locked items in player inventory
            logLockedInventoryItems(player);

            // player.server.getPlayerList().reloadResources(); // Entfernt: verursacht Crash mit SerializerDebug (null-Player im OnDatapackSyncEvent)

            // Welcome message
            if (Config.COMMON.showWelcomeMessage.get()) {
                int stageCount = StageManager.getStages().size();
                player.sendSystemMessage(Component.literal("§8§m                                                §r"));
                player.sendSystemMessage(Component.literal("  §b§lHistory Stages §7— §fWelcome!"));
                player.sendSystemMessage(Component.literal("  §7Loaded §f" + stageCount + " §7stage" + (stageCount != 1 ? "s" : "") + " from §fconfig/historystages/"));
                player.sendSystemMessage(Component.literal("  §7Settings: §fhistorystages-common.toml §7& §fhistorystages-client.toml"));
                player.sendSystemMessage(Component.literal("  §8(Disable this message in the common config)"));
                player.sendSystemMessage(Component.literal("§8§m                                                §r"));
            }

            // Debug error/warning messages (INFO only in log file, not in chat)
            if (Config.COMMON.showDebugErrors.get()) {
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

            // Only run once per server session (onWorldLoad fires for each dimension)
            if (!serverInitialized) {
                serverInitialized = true;
                LOGGER.info("[HistoryStages] Server cache initialized.");

                // Registry validation (registries are now fully loaded)
                StageManager.validateAgainstRegistries();
                DebugLogger.writeLogFile(StageManager.getStages());

                DebugLogger.initRuntimeSession();
                DebugLogger.runtime("Server", "Server started — cache initialized with " + data.getUnlockedStages().size() + " unlocked stages, " + StageManager.getStages().size() + " stages loaded");
            }
        }
    }

    @SubscribeEvent
    public void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        serverInitialized = false;
        DebugLogger.runtime("Server", "Server stopping — flushing runtime log");
        DebugLogger.flushRuntimeBuffer();
    }

    private static int tickCounter = 0;
    private static final int FLUSH_INTERVAL = 600; // every 30 seconds (20 ticks/s * 30s)
    private static final int CLEANUP_INTERVAL = 6000; // every 5 minutes

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        tickCounter++;

        if (tickCounter % FLUSH_INTERVAL == 0) {
            DebugLogger.flushRuntimeBuffer();
        }
        if (tickCounter % CLEANUP_INTERVAL == 0) {
            DebugLogger.cleanupThrottleMap();
        }
    }

    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItem().getItem();
        if (stack.isEmpty()) return;

        // Individual stages: prevent pickup of individually-locked items
        if (net.bananemdnsa.historystages.util.StageLockHelper.isItemLockedByIndividualStage(stack, player.getUUID())) {
            event.setCanceled(true);
            ResourceLocation itemRL = ForgeRegistries.ITEMS.getKey(stack.getItem());
            DebugLogger.runtimeThrottled("Inventory", "pickup_blocked_" + player.getUUID() + "_" + itemRL,
                    "<" + player.getName().getString() + "> Pickup of individually-locked item blocked: " + itemRL);
            return;
        }

        // Global stages: log only (existing behavior)
        if (StageManager.isItemLockedForServer(stack)) {
            ResourceLocation itemRL = ForgeRegistries.ITEMS.getKey(stack.getItem());
            DebugLogger.runtimeThrottled("Inventory", "pickup_" + player.getUUID() + "_" + itemRL,
                    "<" + player.getName().getString() + "> Picked up locked item: " + itemRL + " x" + stack.getCount());
        }
    }

    private static void logLockedInventoryItems(ServerPlayer player) {
        java.util.List<String> lockedItems = new java.util.ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (StageManager.isItemLockedForServer(stack)) {
                ResourceLocation itemRL = ForgeRegistries.ITEMS.getKey(stack.getItem());
                lockedItems.add(itemRL + " x" + stack.getCount());
            }
        }
        if (!lockedItems.isEmpty()) {
            DebugLogger.runtime("Inventory", player.getName().getString(),
                    "Has " + lockedItems.size() + " locked item stack(s) in inventory: " + String.join(", ", lockedItems));
        }
    }
}
