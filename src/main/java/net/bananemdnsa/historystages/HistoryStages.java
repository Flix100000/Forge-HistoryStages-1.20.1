package net.bananemdnsa.historystages;

import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.client.LockDecorator;
import net.bananemdnsa.historystages.commands.StageCommand;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.init.*;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SyncStageDefinitionsPacket;
import net.bananemdnsa.historystages.network.SyncStagesPacket;
import net.bananemdnsa.historystages.screen.ResearchPedestalScreen;
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
import net.minecraftforge.event.level.LevelEvent;
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

        // Das Besondere: Wir generieren für JEDE Stage aus deinen JSONs ein eigenes Buch!
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            for (String stageId : StageManager.getStages().keySet()) {
                ItemStack book = new ItemStack(ModItems.RESEARCH_SCROLL.get());

                // Wir speichern die Stage-ID im Buch, damit es weiß, was es freischaltet
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
            // Send stage definitions first, then unlocked stages
            PacketHandler.sendDefinitionsToPlayer(new SyncStageDefinitionsPacket(StageManager.getStages()), player);
            StageData data = StageData.get(player.serverLevel());
            PacketHandler.sendToPlayer(new SyncStagesPacket(data.getUnlockedStages()), player);
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

            // Debug error messages
            if (Config.COMMON.showDebugErrors.get()) {
                List<String> errors = StageManager.getLoadingErrors();
                if (!errors.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§7[HistoryStages] §cFound §f" + errors.size() + " §cissue" + (errors.size() != 1 ? "s" : "") + " in stage configs:"));
                    for (String error : errors) {
                        player.sendSystemMessage(Component.literal("  " + error));
                    }
                    player.sendSystemMessage(Component.literal("  §8Full report: config/historystages/logs/"));
                    player.sendSystemMessage(Component.literal("  §8(Disable debug messages in the common config)"));
                }
            }
        }
    }

    @SubscribeEvent
    public void onWorldLoad(LevelEvent.Load event) {
        if (!event.getLevel().isClientSide() && event.getLevel() instanceof ServerLevel sl) {
            StageData data = StageData.get(sl);
            StageData.SERVER_CACHE.clear();
            StageData.SERVER_CACHE.addAll(data.getUnlockedStages());
            LOGGER.info("[HistoryStages] Server cache initialized.");
        }
    }
}
