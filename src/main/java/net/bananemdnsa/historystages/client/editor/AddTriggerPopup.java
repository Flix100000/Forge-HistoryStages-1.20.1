package net.bananemdnsa.historystages.client.editor;

import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.AdvancementTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BiomeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockBreakTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockPlaceTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.DimensionTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EntitySubMode;
import net.bananemdnsa.historystages.data.auto.conditions.EntityTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.ItemTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.PlaytimeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.StructureTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.TriggerCondition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Popup screen for adding a new {@link TriggerCondition} to an {@link AutoTrigger}.
 */
public class AddTriggerPopup extends Screen {

    private static final String[] TYPES = {
            "biome", "structure", "dimension", "item",
            "entity", "block_place", "block_break", "advancement", "playtime"
    };

    private final AutoTriggerEditorScreen parent;
    private final AutoTrigger trigger;
    private final Consumer<AutoTrigger> onChanged;

    private int currentTypeIdx = 0;
    private EntitySubMode currentSubMode = EntitySubMode.ANY;
    private EditBox idField;
    private EditBox daysField;
    private Button typeBtn;
    private Button subModeBtn;
    private String error = "";

    public AddTriggerPopup(AutoTriggerEditorScreen parent, AutoTrigger trigger,
                           Consumer<AutoTrigger> onChanged) {
        super(Component.literal("Add Trigger"));
        this.parent = parent;
        this.trigger = trigger;
        this.onChanged = onChanged;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        typeBtn = StyledButton.of(
                Component.literal("Type: " + TYPES[currentTypeIdx]),
                btn -> {
                    currentTypeIdx = (currentTypeIdx + 1) % TYPES.length;
                    btn.setMessage(Component.literal("Type: " + TYPES[currentTypeIdx]));
                    rebuildFieldsVisibility();
                },
                cx - 100, 40, 200, 20);
        this.addRenderableWidget(typeBtn);

        idField = new EditBox(this.font, cx - 100, 70, 200, 18, Component.literal("ID"));
        idField.setMaxLength(256);
        idField.setHint(Component.literal("e.g. minecraft:desert"));
        this.addRenderableWidget(idField);

        daysField = new EditBox(this.font, cx - 100, 70, 200, 18, Component.literal("Days"));
        daysField.setMaxLength(6);
        daysField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        daysField.setValue("1");
        daysField.visible = false;
        this.addRenderableWidget(daysField);

        subModeBtn = StyledButton.of(
                Component.literal("Sub-Mode: " + currentSubMode.serialize()),
                btn -> {
                    currentSubMode = nextSubMode(currentSubMode);
                    btn.setMessage(Component.literal("Sub-Mode: " + currentSubMode.serialize()));
                },
                cx - 100, 94, 200, 20);
        subModeBtn.visible = false;
        this.addRenderableWidget(subModeBtn);

        this.addRenderableWidget(StyledButton.of(
                Component.literal("Add"),
                btn -> {
                    TriggerCondition t = buildTrigger();
                    if (t == null) {
                        error = "Invalid input";
                        return;
                    }
                    trigger.getTriggers().add(t);
                    if (onChanged != null) onChanged.accept(trigger);
                    this.minecraft.setScreen(parent);
                    parent.refreshList();
                },
                cx - 100, this.height - 30, 90, 20));

        this.addRenderableWidget(StyledButton.of(
                Component.literal("Cancel"),
                btn -> this.minecraft.setScreen(parent),
                cx + 10, this.height - 30, 90, 20));

        rebuildFieldsVisibility();
    }

    private static EntitySubMode nextSubMode(EntitySubMode m) {
        return switch (m) {
            case ANY -> EntitySubMode.KILL;
            case KILL -> EntitySubMode.INTERACT;
            case INTERACT -> EntitySubMode.ANY;
        };
    }

    private void rebuildFieldsVisibility() {
        String type = TYPES[currentTypeIdx];
        boolean isPlaytime = "playtime".equals(type);
        boolean isEntity = "entity".equals(type);
        if (idField != null) {
            idField.visible = !isPlaytime;
            idField.active = !isPlaytime;
        }
        if (daysField != null) {
            daysField.visible = isPlaytime;
            daysField.active = isPlaytime;
        }
        if (subModeBtn != null) {
            subModeBtn.visible = isEntity;
            subModeBtn.active = isEntity;
        }
    }

    private TriggerCondition buildTrigger() {
        String type = TYPES[currentTypeIdx];
        String id = idField.getValue().trim();
        return switch (type) {
            case "biome" -> id.isEmpty() ? null : new BiomeTrigger(id);
            case "structure" -> id.isEmpty() ? null : new StructureTrigger(id);
            case "dimension" -> id.isEmpty() ? null : new DimensionTrigger(id);
            case "item" -> id.isEmpty() ? null : new ItemTrigger(id);
            case "entity" -> id.isEmpty() ? null : new EntityTrigger(id, currentSubMode.serialize());
            case "block_place" -> id.isEmpty() ? null : new BlockPlaceTrigger(id);
            case "block_break" -> id.isEmpty() ? null : new BlockBreakTrigger(id);
            case "advancement" -> id.isEmpty() ? null : new AdvancementTrigger(id);
            case "playtime" -> {
                try {
                    yield new PlaytimeTrigger(Integer.parseInt(daysField.getValue().trim()));
                } catch (NumberFormatException nfe) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // No-op
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0xC0000000);
        super.render(g, mx, my, pt);
        g.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        if (!error.isEmpty()) {
            g.drawCenteredString(this.font, error, this.width / 2, this.height - 50, 0xFF5555);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
