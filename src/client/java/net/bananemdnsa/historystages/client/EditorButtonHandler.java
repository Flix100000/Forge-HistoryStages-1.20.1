package net.bananemdnsa.historystages.client;

import net.bananemdnsa.historystages.client.editor.StageOverviewScreen;
import net.bananemdnsa.historystages.client.editor.EditorBlurController;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;

public final class EditorButtonHandler {
    private EditorButtonHandler() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof PauseScreen)) {
                return;
            }
            if (client.options != null && client.options.menuBackgroundBlurriness().get() == 0) {
                client.options.menuBackgroundBlurriness().set(5);
            }
            if (client.player == null || !client.player.hasPermissions(2)) {
                return;
            }

            Screens.getButtons(screen).add(Button.builder(
                    Component.translatable("editor.historystages.title"),
                    button -> {
                        client.setScreen(new StageOverviewScreen());
                        EditorBlurController.enter(client);
                    }
            ).bounds(width - 110, 5, 100, 20).build());
        });
    }
}
