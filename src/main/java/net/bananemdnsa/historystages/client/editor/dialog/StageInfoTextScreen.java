package net.bananemdnsa.historystages.client.editor.dialog;

import net.bananemdnsa.historystages.client.editor.widget.dialog.AbstractInputScreen;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputField;
import net.bananemdnsa.historystages.client.editor.widget.dialog.InputValues;
import net.bananemdnsa.historystages.data.graph.GraphStageData;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.serverbound.SaveStageGraphInfoPacket;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Edits one stage's hand-written graph description ({@code graph_stages.json}), opened from the
 * canvas's right-click context menu. Shaped exactly like {@code CountInputScreen} — a single
 * field, one packet on confirm.
 */
public class StageInfoTextScreen extends AbstractInputScreen {

    private final Screen parent;
    private final String stageId;
    private final boolean individual;

    public StageInfoTextScreen(Screen parent, String stageId, boolean individual) {
        super(parent, Component.translatable("editor.historystages.graph.info.title"));
        this.parent = parent;
        this.stageId = stageId;
        this.individual = individual;
    }

    @Override
    protected Component subtitle() {
        return Component.literal(stageId);
    }

    @Override
    protected List<InputField> fields() {
        String current = GraphStageData.get().description(stageId, individual);
        return List.of(InputField.text("description")
                .maxLength(512)
                .hint(Component.translatable("editor.historystages.graph.info.hint"))
                .initial(current == null ? "" : current));
    }

    @Override
    protected void onConfirm(InputValues values) {
        String description = values.getString("description");
        PacketHandler.sendToServer(new SaveStageGraphInfoPacket(stageId, individual, description));
        // Optimistic local update: the detail panel reads GraphStageData directly on every
        // render, and on a dedicated server the just-typed text would otherwise stay invisible
        // until the broadcasted SyncStageDefinitionsPacket reply lands.
        GraphStageData.set(GraphStageData.get().withDescription(stageId, individual, description));
        this.minecraft.setScreen(parent);
    }
}
