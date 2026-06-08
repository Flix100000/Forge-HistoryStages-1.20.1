package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.auto.CombineMode;
import net.bananemdnsa.historystages.data.auto.conditions.AdvancementTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BiomeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockBreakTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockPlaceTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.DimensionTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EntityTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.ItemTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.PlaytimeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.StructureTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * Screen for editing an {@link AutoTrigger}: combine-mode + list of trigger conditions.
 * Modifies the supplied AutoTrigger in place (mutable backing list), and notifies the
 * parent via callback so the parent can mark hasChanges.
 */
public class AutoTriggerEditorScreen extends Screen {

    private final Screen parent;
    private final AutoTrigger trigger;
    private final Consumer<AutoTrigger> onChanged;

    public AutoTriggerEditorScreen(Screen parent, AutoTrigger trigger, Consumer<AutoTrigger> onChanged) {
        super(Component.literal("Auto-Trigger Editor"));
        this.parent = parent;
        this.trigger = trigger;
        this.onChanged = onChanged;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        // Combine-mode toggle (any/all)
        this.addRenderableWidget(StyledButton.of(
                Component.literal(combineLabel(trigger.resolvedMode())),
                btn -> {
                    CombineMode current = trigger.resolvedMode();
                    CombineMode next = (current == CombineMode.ANY) ? CombineMode.ALL : CombineMode.ANY;
                    trigger.setMode(next.serialize());
                    btn.setMessage(Component.literal(combineLabel(next)));
                    if (onChanged != null) onChanged.accept(trigger);
                },
                cx - 100, 30, 200, 20));

        // + Add Trigger
        this.addRenderableWidget(StyledButton.of(
                Component.literal("+ Add Trigger"),
                btn -> this.minecraft.setScreen(new AddTriggerPopup(this, trigger, onChanged)),
                cx - 100, 56, 200, 20));

        // Render existing triggers
        int y = 86;
        List<TriggerCondition> triggers = trigger.getTriggers();
        for (int i = 0; i < triggers.size(); i++) {
            final int idx = i;
            TriggerCondition t = triggers.get(idx);
            String summary = formatSummary(t);

            this.addRenderableWidget(StyledButton.of(
                    Component.literal(summary),
                    btn -> {
                        // Edit not implemented in v1 — no-op
                    },
                    cx - 200, y, 350, 18));

            this.addRenderableWidget(StyledButton.of(
                    Component.literal("X"),
                    btn -> {
                        trigger.getTriggers().remove(idx);
                        if (onChanged != null) onChanged.accept(trigger);
                        rebuild();
                    },
                    cx + 155, y, 30, 18));

            y += 20;
            if (y > this.height - 50) break; // stop drawing if we'd overflow into the Save row
        }

        this.addRenderableWidget(StyledButton.of(
                Component.literal("Save"),
                btn -> this.minecraft.setScreen(parent),
                cx - 100, this.height - 25, 90, 20));
        this.addRenderableWidget(StyledButton.of(
                Component.literal("Cancel"),
                btn -> this.minecraft.setScreen(parent),
                cx + 10, this.height - 25, 90, 20));
    }

    private static String combineLabel(CombineMode m) {
        return m == CombineMode.ALL ? "All of these" : "Any of these";
    }

    private String formatSummary(TriggerCondition t) {
        return switch (t) {
            case BiomeTrigger b -> "[biome] " + b.id();
            case StructureTrigger s -> "[structure] " + s.id();
            case DimensionTrigger d -> "[dimension] " + d.id();
            case ItemTrigger i -> "[item] " + i.id();
            case EntityTrigger e -> "[entity] " + e.id() + " (" + e.resolvedSubMode().serialize() + ")";
            case BlockPlaceTrigger bp -> "[block_place] " + bp.id();
            case BlockBreakTrigger bb -> "[block_break] " + bb.id();
            case AdvancementTrigger a -> "[advancement] " + a.id();
            case PlaytimeTrigger p -> "[playtime] " + p.days() + " days";
        };
    }

    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    /** Called by AddTriggerPopup after returning, to refresh the trigger list display. */
    public void refreshList() {
        rebuild();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // No-op — we draw our own background
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0xC0000000);
        super.render(g, mx, my, pt);
        g.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
