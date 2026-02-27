package net.bananemdnsa.historystages;

import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.commands.StageCommand;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.init.*;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SyncStagesPacket;
import net.bananemdnsa.historystages.screen.ResearchPedestialScreen;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
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

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);

        PacketHandler.register();
        ConfigHandler.setupConfig();
        StageManager.load();

        modEventBus.addListener(this::addCreative);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // Wir fügen die Maschine bei den Funktions-Blöcken hinzu
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModItems.RESEARCH_PEDESTIAL_ITEM);
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
            MenuScreens.register(ModMenuTypes.RESEARCH_MENU.get(), ResearchPedestialScreen::new);
        });
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        StageCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StageData data = StageData.get(player.serverLevel());
            PacketHandler.sendToPlayer(new SyncStagesPacket(data.getUnlockedStages()), player);
            player.server.getPlayerList().reloadResources();

            // NEU: Nachricht an ALLE Spieler senden (Permission-Check entfernt)
            if (Config.COMMON.showDebugErrors.get()) {
                List<String> errors = StageManager.getLoadingErrors();
                if (!errors.isEmpty()) {
                    player.sendSystemMessage(Component.literal("§7[HistoryStages] §cDebug Info: Issues in JSON configs found!"));
                    for (String error : errors) {
                        player.sendSystemMessage(Component.literal(error));
                    }
                    player.sendSystemMessage(Component.literal("§8(This is a debug message. Disable it in the common config for players)"));
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