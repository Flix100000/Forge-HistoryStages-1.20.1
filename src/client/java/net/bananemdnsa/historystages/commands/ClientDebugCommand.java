package net.bananemdnsa.historystages.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.bananemdnsa.historystages.client.editor.StageOverviewScreen;
import net.bananemdnsa.historystages.network.RequestStructureDebugPayload;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Set;

public final class ClientDebugCommand {
    private static final Set<String> PRESET_KEYS = Set.of(
            "Enchantments",
            "StoredEnchantments",
            "CustomModelData",
            "display",
            "Potion",
            "Unbreakable",
            "RepairCost"
    );

    private ClientDebugCommand() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommandManager.literal("history")
                .then(ClientCommandManager.literal("debug")
                        .then(ClientCommandManager.literal("editor")
                                .executes(context -> openEditor(context.getSource())))
                        .then(ClientCommandManager.literal("structure")
                                .executes(context -> requestStructure(context.getSource())))
                        .then(ClientCommandManager.literal("nbt")
                                .then(ClientCommandManager.literal("preset")
                                        .executes(context -> handlePreset(context.getSource())))
                                .then(ClientCommandManager.literal("custom")
                                        .executes(context -> handleCustom(context.getSource()))))));
    }

    private static int openEditor(FabricClientCommandSource source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            source.sendError(Component.literal("This command can only be run by a player."));
            return 0;
        }
        minecraft.tell(() -> minecraft.setScreen(new StageOverviewScreen()));
        return 1;
    }

    private static int requestStructure(FabricClientCommandSource source) {
        if (Minecraft.getInstance().player == null) {
            source.sendError(Component.literal("This command can only be run by a player."));
            return 0;
        }
        ClientPlayNetworking.send(new RequestStructureDebugPayload());
        return 1;
    }

    private static int handlePreset(FabricClientCommandSource source) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            source.sendError(Component.literal("This command can only be run by a player."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendError(Component.literal("You are not holding an item."));
            return 0;
        }

        CompoundTag tag = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());

        source.sendFeedback(Component.literal("\u00A76--- NBT Preset Fields ---"));
        source.sendFeedback(Component.literal("\u00A77Item: \u00A7f" + itemId));

        printPreset(source, "Enchantments", formatEnchantmentList(tag, "Enchantments"));
        printPreset(source, "StoredEnchantments", formatEnchantmentList(tag, "StoredEnchantments"));
        printPreset(source, "CustomModelData", formatInt(tag, "CustomModelData"));
        printPreset(source, "display.Name", formatDisplayChild(tag, "Name"));
        printPreset(source, "display.Lore", formatDisplayLore(tag));
        printPreset(source, "Potion", formatString(tag, "Potion"));
        printPreset(source, "Unbreakable", formatBool(tag, "Unbreakable"));
        printPreset(source, "RepairCost", formatInt(tag, "RepairCost"));

        return 1;
    }

    private static int handleCustom(FabricClientCommandSource source) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            source.sendError(Component.literal("This command can only be run by a player."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendError(Component.literal("You are not holding an item."));
            return 0;
        }

        CompoundTag tag = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(held.getItem());

        source.sendFeedback(Component.literal("\u00A76--- Custom NBT Entries ---"));
        source.sendFeedback(Component.literal("\u00A77Item: \u00A7f" + itemId));
        source.sendFeedback(Component.literal("\u00A78(keys not recognized by the NBT editor presets - add these via '+ Custom NBT Key')"));

        if (tag.isEmpty()) {
            source.sendFeedback(Component.literal("  \u00A78(item has no NBT)"));
            return 1;
        }

        boolean any = false;
        for (String key : tag.getAllKeys()) {
            if (PRESET_KEYS.contains(key)) {
                continue;
            }
            any = true;
            Tag value = tag.get(key);
            String valueText = value == null ? "" : value.toString();
            source.sendFeedback(Component.literal("  \u00A78\u2022 \u00A7bkey: \u00A7f" + key));
            source.sendFeedback(Component.literal("    \u00A78  \u00A7bvalue: \u00A7f" + valueText));
        }

        if (!any) {
            source.sendFeedback(Component.literal("  \u00A7a(no custom NBT - all keys are preset-recognized or item has only preset NBT)"));
        }
        return 1;
    }

    private static void printPreset(FabricClientCommandSource source, String label, String value) {
        boolean set = value != null;
        String color = set ? "\u00A7a" : "\u00A78";
        String formattedValue = set ? "\u00A7f" + value : "\u00A78(not set)";
        source.sendFeedback(Component.literal("  " + color + "\u2022 \u00A7b" + label + "\u00A77: " + formattedValue));
    }

    private static String formatInt(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key)) {
            return null;
        }
        return String.valueOf(tag.getInt(key));
    }

    private static String formatString(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key)) {
            return null;
        }
        return tag.getString(key);
    }

    private static String formatBool(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key)) {
            return null;
        }
        return tag.getBoolean(key) ? "true" : "false";
    }

    private static String formatDisplayChild(CompoundTag tag, String childKey) {
        if (tag == null || !tag.contains("display", Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag display = tag.getCompound("display");
        if (!display.contains(childKey)) {
            return null;
        }
        return display.getString(childKey);
    }

    private static String formatDisplayLore(CompoundTag tag) {
        if (tag == null || !tag.contains("display", Tag.TAG_COMPOUND)) {
            return null;
        }
        CompoundTag display = tag.getCompound("display");
        if (!display.contains("Lore", Tag.TAG_LIST)) {
            return null;
        }
        ListTag lore = display.getList("Lore", Tag.TAG_STRING);
        if (lore.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < lore.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(lore.getString(i));
        }
        builder.append("]");
        return builder.toString();
    }

    private static String formatEnchantmentList(CompoundTag tag, String key) {
        if (tag == null || !tag.contains(key, Tag.TAG_LIST)) {
            return null;
        }
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag enchantment = list.getCompound(i);
            String id = enchantment.getString("id");
            int level = enchantment.getInt("lvl");
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(normalizeEnchantmentId(id)).append(" ").append(level);
        }
        return builder.toString();
    }

    private static String normalizeEnchantmentId(String id) {
        if (id == null || id.isEmpty()) {
            return id;
        }
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? id : location.toString();
    }
}
