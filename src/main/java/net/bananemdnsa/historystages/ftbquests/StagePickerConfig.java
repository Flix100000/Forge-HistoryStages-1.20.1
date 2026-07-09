package net.bananemdnsa.historystages.ftbquests;

import dev.ftb.mods.ftblibrary.config.ConfigCallback;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.icon.Icon;
import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * A clickable config row for choosing a History Stage in an FTB Quests task/reward.
 * Replaces the old cycling {@code addEnum} button plus the separate {@code individual}
 * checkbox. The value is a prefixed string ("global:&lt;id&gt;" / "individual:&lt;id&gt;",
 * or "" for none); the consumer on the task/reward splits it back into the existing
 * {@code stage} + {@code individual} fields, so NBT/network serialization is unchanged.
 */
public class StagePickerConfig extends ConfigValue<String> {

    public static final String GLOBAL_PREFIX = "global:";
    public static final String INDIVIDUAL_PREFIX = "individual:";

    /** Build the prefixed GUI value from the task/reward's stored fields. */
    public static String toPrefixed(String stage, boolean individual) {
        if (stage == null || stage.isEmpty()) return "";
        return (individual ? INDIVIDUAL_PREFIX : GLOBAL_PREFIX) + stage;
    }

    /** The bare stage id without the type prefix. Unprefixed legacy values pass through. */
    public static String stripPrefix(String prefixed) {
        if (prefixed == null || prefixed.isEmpty()) return "";
        if (prefixed.startsWith(INDIVIDUAL_PREFIX)) return prefixed.substring(INDIVIDUAL_PREFIX.length());
        if (prefixed.startsWith(GLOBAL_PREFIX)) return prefixed.substring(GLOBAL_PREFIX.length());
        return prefixed; // legacy: stored as a bare id, treated as global
    }

    /** Whether the prefixed value refers to an individual stage. */
    public static boolean isIndividual(String prefixed) {
        return prefixed != null && prefixed.startsWith(INDIVIDUAL_PREFIX);
    }

    private static Map<String, StageEntry> sourceFor(String prefixed) {
        return isIndividual(prefixed) ? StageManager.getIndividualStages() : StageManager.getStages();
    }

    @Override
    public void onClicked(Widget clickedWidget, MouseButton button, ConfigCallback callback) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen parent = mc.screen;
        mc.setScreen(new net.bananemdnsa.historystages.ftbquests.client.StagePickerScreen(
                parent, getValue(), chosen -> {
                    setValue(chosen);
                    mc.setScreen(parent);
                    callback.save(true);
                }));
    }

    @Override
    public Component getStringForGUI(String value) {
        if (value == null || value.isEmpty()) {
            return Component.translatable("ftbquests.historystages.picker.none").withStyle(ChatFormatting.GRAY);
        }
        String id = stripPrefix(value);
        StageEntry entry = sourceFor(value).get(id);
        String name = entry != null ? entry.getDisplayName() : id;
        String markerKey = isIndividual(value)
                ? "ftbquests.historystages.picker.marker.individual"
                : "ftbquests.historystages.picker.marker.global";
        return Component.literal(name + " ")
                .append(Component.translatable(markerKey).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public Icon getIcon() {
        String value = getValue();
        if (value == null || value.isEmpty()) return super.getIcon();
        StageEntry entry = sourceFor(value).get(stripPrefix(value));
        String iconId = (entry != null && !entry.getIcon().isEmpty())
                ? entry.getIcon() : Config.COMMON.defaultStageIcon.get();
        if (iconId == null || iconId.isEmpty()) return super.getIcon();
        return ItemIcon.getItemIcon(iconId);
    }
}
