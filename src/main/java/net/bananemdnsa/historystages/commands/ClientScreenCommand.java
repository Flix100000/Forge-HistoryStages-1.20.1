package net.bananemdnsa.historystages.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.StageGraphScreen;
import net.bananemdnsa.historystages.client.editor.StageOverviewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * The two screens that never need the server: {@code /history editor} and {@code /history graph}
 * only swap the local screen, so they sit on the client dispatcher next to
 * {@link ClientDebugCommand} rather than on {@code StageCommand}.
 */
@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public final class ClientScreenCommand {

    private ClientScreenCommand() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("history")
                .then(Commands.literal("editor")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> openEditor(ctx.getSource())))
                .then(Commands.literal("graph")
                        .executes(ctx -> openGraph(ctx.getSource()))));
    }

    private static int openEditor(CommandSourceStack source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            source.sendFailure(Component.translatable("command.historystages.player_only"));
            return 0;
        }
        mc.tell(() -> mc.setScreen(new StageOverviewScreen()));
        return 1;
    }

    private static int openGraph(CommandSourceStack source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            source.sendFailure(Component.translatable("command.historystages.player_only"));
            return 0;
        }
        // The same door the pause-screen button is: with the graph switched off players have none.
        // An operator keeps it either way, so a pack author can look at the player view before
        // turning it on for everyone. The check sits here rather than in a requires() because
        // requires() is evaluated once while the command tree is merged, and at that point the
        // server has not necessarily pushed graph.toml to us yet.
        if (!GraphConfig.GRAPH.enabled.get() && !source.hasPermission(2)) {
            source.sendFailure(Component.translatable("command.historystages.graph_disabled"));
            return 0;
        }
        // By the time this runs the chat has closed, so the parent is null and ESC drops straight
        // back into the world instead of the pause screen the button opens it from.
        mc.tell(() -> mc.setScreen(StageGraphScreen.forPlayer(mc.screen)));
        return 1;
    }
}
