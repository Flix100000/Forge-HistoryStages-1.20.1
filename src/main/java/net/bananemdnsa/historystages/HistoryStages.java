package net.bananemdnsa.historystages;

import com.mojang.logging.LogUtils;
import net.bananemdnsa.historystages.client.LockDecorator;
import net.bananemdnsa.historystages.commands.StageCommand;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.init.*;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SyncStagesPacket;
import net.bananemdnsa.historystages.screen.ResearchPedestalScreen;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.slf4j.Logger;

import java.util.List;

@Mod(HistoryStages.MOD_ID)
public class HistoryStages {
    public static final String MOD_ID = "historystages";
    private static final Logger LOGGER = LogUtils.getLogger();

    public HistoryStages(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        // Hier fügen wir den Decorator hinzu:
        modEventBus.addListener(this::onRegisterItemDecorators);
        modEventBus.addListener(this::registerScreens);
        modEventBus.addListener(this::registerCapabilities);

        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);

        ConfigHandler.setupConfig();
        StageManager.load();

        NeoForge.EVENT_BUS.register(this);
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

        // Das Besondere: Wir generieren für JEDE Stage aus deinen JSONs ein eigenes Buch!
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            for (String stageId : StageManager.getStages().keySet()) {
                ItemStack book = new ItemStack(ModItems.RESEARCH_SCROLL.get());

                // Wir speichern die Stage-ID im Buch, damit es weiß, was es freischaltet
                CompoundTag tag = new CompoundTag();
                tag.putString("StageResearch", stageId);
                book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

                event.accept(book);
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
            StageData data = StageData.get(player.serverLevel());
            PacketHandler.sendToPlayer(new SyncStagesPacket(data.getUnlockedStages()), player);
            // player.server.getPlayerList().reloadResources(); // Entfernt: verursacht Crash mit SerializerDebug (null-Player im OnDatapackSyncEvent)

            // Welcome message
            if (Config.COMMON.showWelcomeMessage.get()) {
                player.sendSystemMessage(Component.literal("§7[HistoryStages] §fThank you for using §bHistory Stages§f!"));
                player.sendSystemMessage(Component.literal("§7Define your stages in §fconfig/historystages/§7 and adjust settings in §fhistorystages-common.toml §7& §fhistorystages-client.toml§7."));
                player.sendSystemMessage(Component.literal("§8(You can disable this message in the common config)"));
            }

            // Debug error messages
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
