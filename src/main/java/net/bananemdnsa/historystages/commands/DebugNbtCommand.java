package net.bananemdnsa.historystages.commands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

/**
 * /history debug nbt preset  — shows the NBT editor's preset fields for the held item
 * /history debug nbt custom  — shows the remaining (unrecognized) NBT keys that would go
 *                              into the "Custom NBT" field of the editor.
 *
 * Preset key list mirrors NbtItemEditScreen.buildPropertyTree(). Keep in sync.
 */
public final class DebugNbtCommand {

    private static final Set<String> PRESET_KEYS = Set.of(
            "Enchantments",
            "StoredEnchantments",
            "CustomModelData",
            "display",
            "Potion",
            "Unbreakable",
            "RepairCost"
    );

    private DebugNbtCommand() {}

    public static int handlePreset(CommandSourceStack source) {
        ServerPlayer player = resolvePlayer(source);
        if (player == null) return 0;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("You are not holding an item."));
            return 0;
        }

        String itemId = ForgeRegistries.ITEMS.getKey(held.getItem()) + "";
        CompoundTag tag = held.getTag();

        source.sendSuccess(() -> Component.literal("§6--- NBT Preset Fields ---"), false);
        source.sendSuccess(() -> Component.literal("§7Item: §f" + itemId), false);

        printPreset(source, "Enchantments",      formatEnchantmentList(tag, "Enchantments"));
        printPreset(source, "StoredEnchantments", formatEnchantmentList(tag, "StoredEnchantments"));
        printPreset(source, "CustomModelData",   formatInt(tag, "CustomModelData"));
        printPreset(source, "display.Name",      formatDisplayChild(tag, "Name"));
        printPreset(source, "display.Lore",      formatDisplayLore(tag));
        printPreset(source, "Potion",            formatString(tag, "Potion"));
        printPreset(source, "Unbreakable",       formatBool(tag, "Unbreakable"));
        printPreset(source, "RepairCost",        formatInt(tag, "RepairCost"));

        return 1;
    }

    public static int handleCustom(CommandSourceStack source) {
        ServerPlayer player = resolvePlayer(source);
        if (player == null) return 0;

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("You are not holding an item."));
            return 0;
        }

        String itemId = ForgeRegistries.ITEMS.getKey(held.getItem()) + "";
        CompoundTag tag = held.getTag();

        source.sendSuccess(() -> Component.literal("§6--- Custom NBT Entries ---"), false);
        source.sendSuccess(() -> Component.literal("§7Item: §f" + itemId), false);
        source.sendSuccess(() -> Component.literal("§8(keys not recognized by the NBT editor presets — add these via '+ Custom NBT Key')"), false);

        if (tag == null || tag.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  §8(item has no NBT)"), false);
            return 1;
        }

        boolean any = false;
        for (String key : tag.getAllKeys()) {
            if (PRESET_KEYS.contains(key)) continue;
            any = true;
            Tag value = tag.get(key);
            String valueStr = value == null ? "" : value.toString();
            source.sendSuccess(() -> Component.literal("  §8• §bkey: §f" + key), false);
            source.sendSuccess(() -> Component.literal("    §8  §bvalue: §f" + valueStr), false);
        }

        if (!any) {
            source.sendSuccess(() -> Component.literal("  §a(no custom NBT — all keys are preset-recognized or item has only preset NBT)"), false);
        }
        return 1;
    }

    // ---------- helpers ----------

    private static ServerPlayer resolvePlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return null;
        }
    }

    private static void printPreset(CommandSourceStack source, String label, String value) {
        boolean set = value != null;
        String color = set ? "§a" : "§8";
        String val = set ? "§f" + value : "§8(not set)";
        source.sendSuccess(() -> Component.literal("  " + color + "• §b" + label + "§7: " + val), false);
    }

    private static String formatInt(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key)) return null;
        return String.valueOf(tag.getInt(key));
    }

    private static String formatString(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key)) return null;
        return tag.getString(key);
    }

    private static String formatBool(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key)) return null;
        return tag.getBoolean(key) ? "true" : "false";
    }

    private static String formatDisplayChild(CompoundTag tag, String childKey) {
        if (tag == null || !tag.contains("display", Tag.TAG_COMPOUND)) return null;
        CompoundTag display = tag.getCompound("display");
        if (!display.contains(childKey)) return null;
        return display.getString(childKey);
    }

    private static String formatDisplayLore(CompoundTag tag) {
        if (tag == null || !tag.contains("display", Tag.TAG_COMPOUND)) return null;
        CompoundTag display = tag.getCompound("display");
        if (!display.contains("Lore", Tag.TAG_LIST)) return null;
        ListTag lore = display.getList("Lore", Tag.TAG_STRING);
        if (lore.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < lore.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(lore.getString(i));
        }
        sb.append("]");
        return sb.toString();
    }

    private static String formatEnchantmentList(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key, Tag.TAG_LIST)) return null;
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        if (list.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag ench = list.getCompound(i);
            String id = ench.getString("id");
            int lvl = ench.getInt("lvl");
            if (i > 0) sb.append(", ");
            sb.append(normalizeEnchantmentId(id)).append(" ").append(lvl);
        }
        return sb.toString();
    }

    private static String normalizeEnchantmentId(String id) {
        if (id == null || id.isEmpty()) return id;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? id : rl.toString();
    }
}
