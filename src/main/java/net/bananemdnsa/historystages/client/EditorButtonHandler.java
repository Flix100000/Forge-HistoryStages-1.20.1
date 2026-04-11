package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.StageOverviewScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
public class EditorButtonHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.hasPermissions(2)) return;

        int screenWidth = event.getScreen().width;

        event.addListener(Button.builder(
                Component.translatable("editor.historystages.title"),
                btn -> mc.setScreen(new StageOverviewScreen())
        ).bounds(screenWidth - 110, 5, 100, 20).build());
    }
}
