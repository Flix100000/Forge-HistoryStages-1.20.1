package net.bananemdnsa.historystages;

import net.bananemdnsa.historystages.commands.StageCommand;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.events.GameplayEvents;
import net.bananemdnsa.historystages.events.MobSpawnLockEvents;
import net.bananemdnsa.historystages.init.ModBlockEntities;
import net.bananemdnsa.historystages.init.ModBlocks;
import net.bananemdnsa.historystages.init.ModCreativeTabs;
import net.bananemdnsa.historystages.init.ModItems;
import net.bananemdnsa.historystages.init.ModMenuTypes;
import net.bananemdnsa.historystages.network.Networking;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoryStagesFabric implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger(HistoryStages.MOD_ID);

    @Override
    public void onInitialize() {
        Config.load();
        ModBlocks.register();
        ModBlockEntities.register();
        ModItems.register();
        ModCreativeTabs.register();
        ModMenuTypes.register();
        Networking.registerCommon();
        StageManager.load();
        GameplayEvents.register();
        MobSpawnLockEvents.register();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                StageCommand.register(dispatcher));

        ServerWorldEvents.LOAD.register((server, world) -> {
            var data = net.bananemdnsa.historystages.util.StageData.get(world);
            net.bananemdnsa.historystages.util.StageData.refreshCache(data.getUnlockedStages());
            net.bananemdnsa.historystages.util.IndividualStageData.get(world).refreshCache();
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            StageManager.validateAgainstRegistries();
            Networking.syncAll(server);
            LOGGER.info("Loaded {} global stages and {} individual stages.",
                    StageManager.getStages().size(), StageManager.getIndividualStages().size());
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> DebugLogger.flushRuntimeBuffer());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!Config.COMMON.showWelcomeMessage) {
                return;
            }

            int stageCount = StageManager.getStages().size();
            handler.player.sendSystemMessage(Component.literal("§8§m                                                §r"));
            handler.player.sendSystemMessage(Component.literal("  §b§lHistory Stages §7— §fWelcome!"));
            handler.player.sendSystemMessage(Component.literal("  §7Loaded §f" + stageCount + " §7stage"
                    + (stageCount != 1 ? "s" : "") + " from §fconfig/historystages/"));
            handler.player.sendSystemMessage(Component.literal("  §7Settings: §fconfig/historystages/config.json"));
            handler.player.sendSystemMessage(Component.literal("  §8(Disable this message in the common config)"));
            handler.player.sendSystemMessage(Component.literal("§8§m                                                §r"));
        });
    }
}
