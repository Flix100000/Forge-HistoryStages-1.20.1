package net.bananemdnsa.historystages.client.editor.dialog;

import net.bananemdnsa.historystages.client.editor.widget.dialog.FormattedTextScreen;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.SaveStageGraphInfoPacket;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Edits one stage's hand-written graph description ({@code graph_stages.json}), opened from the
 * canvas's right-click context menu.
 *
 * <p>Built on {@link FormattedTextScreen} rather than a plain input field: a description may run
 * to 512 characters, which a one-line box shows perhaps a fifth of, and it carries {@code &}
 * colour codes like the mod's other display strings. It takes no placeholders, so the dialog
 * simply shows no placeholder chips.
 */
public class StageInfoTextScreen extends FormattedTextScreen {

    private final String stageId;

    public StageInfoTextScreen(Screen parent, String stageId, boolean individual) {
        super(parent,
                Component.translatable("editor.historystages.graph.info.title"),
                currentDescription(stageId, individual),
                Component.translatable("editor.historystages.graph.info.hint").getString(),
                List.of(),
                text -> save(stageId, individual, text));
        this.stageId = stageId;
    }

    @Override
    protected Component subtitle() {
        return Component.literal(stageId);
    }

    private static String currentDescription(String stageId, boolean individual) {
        String current = GraphStageData.get().description(stageId, individual);
        return current == null ? "" : current;
    }

    private static void save(String stageId, boolean individual, String description) {
        PacketHandler.sendToServer(new SaveStageGraphInfoPacket(stageId, individual, description));
        // Optimistic local update: the detail panel reads GraphStageData directly on every
        // render, and on a dedicated server the just-typed text would otherwise stay invisible
        // until the broadcasted SyncStageDefinitionsPacket reply lands.
        GraphStageData.set(GraphStageData.get().withDescription(stageId, individual, description));
    }
}
