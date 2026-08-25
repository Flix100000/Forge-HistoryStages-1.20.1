package net.bananemdnsa.historystages.client.editor.trigger;

import net.bananemdnsa.historystages.api.editor.TriggerEditor;

import net.bananemdnsa.historystages.data.auto.conditions.AdvancementTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BiomeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockBreakTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.BlockPlaceTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.DimensionTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.EntityTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.ItemTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.PlaytimeTrigger;
import net.bananemdnsa.historystages.data.auto.conditions.StructureTrigger;
import net.bananemdnsa.historystages.api.trigger.TriggerCondition;
import net.bananemdnsa.historystages.data.auto.conditions.UnknownTrigger;
import net.minecraft.network.chat.Component;

/**
 * How a trigger is written out in a list — the type in one column, its value in the next.
 *
 * <p>Lived twice, once in the auto-trigger editor and once in the graph's detail panel, which is
 * how an addon's trigger came to render its raw lang key: the fix would have had to be made in
 * both. One place now, client only, because the labels are only ever read by a screen.
 */
public final class TriggerLabels {

    private TriggerLabels() {}

    /**
     * The type column. A built-in has a lang key derived from its id; an addon's type is
     * namespaced, so no such key can exist and the label comes from whatever editor was
     * registered for it — or from the bare type when nothing was.
     */
    public static String typeLabel(TriggerCondition t) {
        if (t instanceof UnknownTrigger) {
            return Component.translatable("editor.historystages.auto_trigger.type.unknown").getString();
        }
        TriggerEditor editor = TriggerEditors.byType(t.type());
        if (editor != null) {
            return Component.translatable(editor.labelLangKey()).getString();
        }
        // Namespaced means it is not one of ours, so there is nothing to translate it with.
        if (t.type().indexOf(':') >= 0) return t.type();
        return Component.translatable("editor.historystages.auto_trigger.type." + t.type()).getString();
    }

    /** The value column: what this particular trigger is waiting for. */
    public static String valueText(TriggerCondition t) {
        return switch (t) {
            case BiomeTrigger b -> b.id();
            case StructureTrigger s -> s.id();
            case DimensionTrigger d -> d.id();
            case ItemTrigger i -> i.id();
            case EntityTrigger e -> e.id() + " ("
                    + Component.translatable("editor.historystages.auto_trigger.entity."
                            + e.resolvedSubMode().serialize()).getString()
                    + ")";
            case BlockPlaceTrigger bp -> bp.id();
            case BlockBreakTrigger bb -> bb.id();
            case AdvancementTrigger a -> a.id();
            case PlaytimeTrigger p -> Component.translatable(
                    "editor.historystages.auto_trigger.playtime.days", p.days()).getString();
            // An addon's trigger can say what it holds; one from a mod that is not loaded cannot,
            // and then the type is the only informative half there is.
            default -> {
                TriggerEditor editor = TriggerEditors.byType(t.type());
                String value = editor == null ? "" : editor.valueText(t);
                yield value.isEmpty() ? t.type() : value;
            }
        };
    }
}
