package net.bananemdnsa.historystages.client.scroll;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Locale;

/**
 * Display names for the open scroll's text rows.
 *
 * <p>Until now the screen showed the raw registry path — {@code zombie_villager} instead of the
 * mob's name. {@link net.bananemdnsa.historystages.data.scroll.OpenScrollWorldGroup} has promised
 * "turned into display names by the screen" since the first commit; this is where that gets paid.
 *
 * <p>Entities answer through their own description. Biomes, structures and dimensions have no
 * guaranteed vanilla translation key, so the key is tried and {@link #prettify} catches the miss.
 * Modded content usually ships its keys and lands on the first branch.
 */
public final class OpenScrollNames {

    private OpenScrollNames() {}

    /** The entity's name, or a prettified id when the type is not registered. */
    public static String creature(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        EntityType<?> type = key == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(key);
        return type == null ? prettify(id) : type.getDescription().getString();
    }

    public static String biome(String id) {
        return translated("biome", id);
    }

    public static String structure(String id) {
        return translated("structure", id);
    }

    public static String dimension(String id) {
        return translated("dimension", id);
    }

    /** {@code <prefix>.<namespace>.<path>} when that key exists, a prettified path otherwise. */
    private static String translated(String prefix, String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key != null) {
            String candidate = prefix + "." + key.getNamespace() + "." + key.getPath();
            if (I18n.exists(candidate)) return I18n.get(candidate);
        }
        return prettify(id);
    }

    /** {@code minecraft:old_growth_taiga} becomes {@code Old Growth Taiga}. */
    public static String prettify(String id) {
        if (id == null || id.isBlank()) return "";
        int colon = id.indexOf(':');
        String path = colon < 0 ? id : id.substring(colon + 1);

        StringBuilder out = new StringBuilder(path.length());
        for (String word : path.split("_")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }
}
