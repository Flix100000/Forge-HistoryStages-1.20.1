package net.bananemdnsa.historystages.commands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Set;

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

    private DebugNbtCommand() {
    }

    public static int handlePreset(CommandSourceStack source) {
        ServerPlayer player = resolvePlayer(source);
        if (player == null) {
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("You are not holding an item."));
            return 0;
        }

        CompoundTag tag = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        source.sendSuccess(() -> Component.literal("--- NBT Preset Fields ---"), false);
        source.sendSuccess(() -> Component.literal("Item: " + held.getItemHolder().getRegisteredName()), false);
        for (String key : PRESET_KEYS) {
            if (tag.contains(key)) {
                source.sendSuccess(() -> Component.literal(" - " + key + ": " + tag.get(key)), false);
            } else {
                source.sendSuccess(() -> Component.literal(" - " + key + ": (not set)"), false);
            }
        }
        return 1;
    }

    public static int handleCustom(CommandSourceStack source) {
        ServerPlayer player = resolvePlayer(source);
        if (player == null) {
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("You are not holding an item."));
            return 0;
        }

        CompoundTag tag = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        source.sendSuccess(() -> Component.literal("--- Custom NBT Entries ---"), false);
        if (tag.isEmpty()) {
            source.sendSuccess(() -> Component.literal("(item has no custom data)"), false);
            return 1;
        }

        boolean any = false;
        for (String key : tag.getAllKeys()) {
            if (PRESET_KEYS.contains(key)) {
                continue;
            }
            any = true;
            source.sendSuccess(() -> Component.literal(" - " + key + ": " + tag.get(key)), false);
        }

        if (!any) {
            source.sendSuccess(() -> Component.literal("(no custom NBT outside preset keys)"), false);
        }
        return 1;
    }

    private static ServerPlayer resolvePlayer(CommandSourceStack source) {
        try {
            return source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return null;
        }
    }
}
