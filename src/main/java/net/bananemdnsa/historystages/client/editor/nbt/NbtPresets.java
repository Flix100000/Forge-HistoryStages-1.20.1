package net.bananemdnsa.historystages.client.editor.nbt;

import java.util.List;
import java.util.Map;

/**
 * The two lookup tables the editor needs on top of the raw component registry.
 *
 * <p>{@link #common()} is the picker's first tab: the handful of components people reach for by
 * name rather than by id. {@link #isLegacyKey} is the other direction — the pre-1.20.5 key names
 * that used to mean those same things and now only match if a pack literally writes them into
 * {@code custom_data}.
 */
public final class NbtPresets {

    /**
     * @param nameKey        lang key for the friendly label
     * @param descriptionKey lang key for the line under it
     * @param componentId    the component this writes to
     * @param valueKind      how its value is edited, and therefore what JSON gets written
     * @param defaultValue   pre-filled JSON, "" for none
     */
    public record Preset(String nameKey, String descriptionKey, String componentId,
                         ValueKind valueKind, String defaultValue) {}

    public static final String LORE_COMPONENT = "minecraft:lore";

    private static final List<Preset> COMMON = List.of(
            new Preset("editor.historystages.nbt.preset.custom_name",
                    "editor.historystages.nbt.preset.custom_name.desc",
                    "minecraft:custom_name", ValueKind.TEXT, ""),
            new Preset("editor.historystages.nbt.preset.lore",
                    "editor.historystages.nbt.preset.lore.desc",
                    LORE_COMPONENT, ValueKind.TEXT_LIST, ""),
            new Preset("editor.historystages.nbt.preset.unbreakable",
                    "editor.historystages.nbt.preset.unbreakable.desc",
                    "minecraft:unbreakable", ValueKind.PRESENCE, "{}"),
            // The potion codec has an alternative form, so what it encodes to is not something to
            // assume from here. Raw JSON until that is checked in game.
            new Preset("editor.historystages.nbt.preset.potion",
                    "editor.historystages.nbt.preset.potion.desc",
                    "minecraft:potion_contents", ValueKind.JSON, ""),
            new Preset("editor.historystages.nbt.preset.custom_model_data",
                    "editor.historystages.nbt.preset.custom_model_data.desc",
                    "minecraft:custom_model_data", ValueKind.NUMBER, ""),
            new Preset("editor.historystages.nbt.preset.repair_cost",
                    "editor.historystages.nbt.preset.repair_cost.desc",
                    "minecraft:repair_cost", ValueKind.NUMBER, ""));

    /**
     * Pre-1.20.5 key names and the component that carries the same thing today. Only used to offer
     * a hint next to a loaded criterion — nothing is rewritten on its own, because a pack may
     * legitimately keep a key called "Unbreakable" in its custom data.
     */
    private static final Map<String, String> LEGACY_KEYS = Map.of(
            "Unbreakable", "minecraft:unbreakable",
            "CustomModelData", "minecraft:custom_model_data",
            "RepairCost", "minecraft:repair_cost",
            "Potion", "minecraft:potion_contents",
            "display", "minecraft:custom_name");

    private NbtPresets() {}

    public static List<Preset> common() {
        return COMMON;
    }

    /** The preset that writes to this component, or null when it is not a preset. */
    public static Preset byComponentId(String componentId) {
        for (Preset preset : COMMON) {
            if (preset.componentId().equals(componentId)) return preset;
        }
        return null;
    }

    /**
     * How this component's value should be edited. Anything the preset table does not cover stays
     * raw JSON — a mod component can encode to any shape, and a wrong guess produces a criterion
     * that silently never matches.
     */
    public static ValueKind valueKindFor(String componentId) {
        Preset preset = byComponentId(componentId);
        return preset == null ? ValueKind.JSON : preset.valueKind();
    }

    public static final String ENCHANTMENTS = "Enchantments";
    public static final String STORED_ENCHANTMENTS = "StoredEnchantments";

    /**
     * Friendly name for one of the two enchantment lists.
     *
     * <p>They are told apart here rather than at each call site because the two look nearly
     * identical everywhere they appear — picker row, card heading, card description — and a single
     * spot that forgets the difference makes them indistinguishable, which is exactly what
     * happened when both cards described themselves as "enchantments on the item".
     */
    public static String enchantmentNameKey(String topLevelKey) {
        return STORED_ENCHANTMENTS.equals(topLevelKey)
                ? "editor.historystages.nbt.enchantments.stored"
                : "editor.historystages.nbt.enchantments.active";
    }

    public static String enchantmentDescriptionKey(String topLevelKey) {
        return STORED_ENCHANTMENTS.equals(topLevelKey)
                ? "editor.historystages.nbt.desc.stored_enchantments"
                : "editor.historystages.nbt.desc.enchantments";
    }

    public static boolean isLegacyKey(String key) {
        return LEGACY_KEYS.containsKey(key);
    }

    /** The component a legacy key most likely meant, or null when the key is not a legacy one. */
    public static String componentForLegacyKey(String key) {
        return LEGACY_KEYS.get(key);
    }
}
