package net.bananemdnsa.historystages.commands;

import com.google.gson.JsonElement;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.StageOverviewScreen;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.serverbound.RequestClusterShapesPacket;
import net.bananemdnsa.historystages.network.serverbound.RequestStructureDebugPacket;
import net.bananemdnsa.historystages.network.serverbound.ToggleStructureVizPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.Set;

@EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
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

    private ClientDebugCommand() {}

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("history")
                .then(Commands.literal("debug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("editor")
                                .executes(ctx -> openEditor(ctx.getSource())))
                        .then(Commands.literal("structure")
                                .executes(ctx -> requestStructure(ctx.getSource())))
                        .then(Commands.literal("viz")
                                .executes(ctx -> toggleViz(ctx.getSource())))
                        .then(Commands.literal("shapes")
                                .executes(ctx -> requestShapes(ctx.getSource())))
                        .then(Commands.literal("nbt")
                                .then(Commands.literal("preset")
                                        .executes(ctx -> handlePreset(ctx.getSource())))
                                .then(Commands.literal("custom")
                                        .executes(ctx -> handleCustom(ctx.getSource())))
                                .then(Commands.literal("components")
                                        .executes(ctx -> handleComponents(ctx.getSource()))))));
    }

    // ---------- editor ----------

    private static int openEditor(CommandSourceStack source) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }
        mc.tell(() -> mc.setScreen(new StageOverviewScreen()));
        return 1;
    }

    // ---------- structure (server round-trip) ----------

    private static int requestStructure(CommandSourceStack source) {
        if (Minecraft.getInstance().player == null) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }
        PacketHandler.sendToServer(new RequestStructureDebugPacket());
        return 1;
    }

    private static int toggleViz(CommandSourceStack source) {
        if (Minecraft.getInstance().player == null) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }
        PacketHandler.sendToServer(new ToggleStructureVizPacket());
        return 1;
    }

    private static int requestShapes(CommandSourceStack source) {
        if (Minecraft.getInstance().player == null) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }
        PacketHandler.sendToServer(new RequestClusterShapesPacket());
        return 1;
    }

    // ---------- nbt (purely client-side, held item is on client) ----------

    private static int handlePreset(CommandSourceStack source) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("You are not holding an item."));
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        CompoundTag tag = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        source.sendSuccess(() -> Component.literal("§6--- NBT Preset Fields ---"), false);
        source.sendSuccess(() -> Component.literal("§7Item: §f" + itemId), false);

        printPreset(source, "Enchantments",       formatEnchantmentList(tag, "Enchantments"));
        printPreset(source, "StoredEnchantments", formatEnchantmentList(tag, "StoredEnchantments"));
        printPreset(source, "CustomModelData",    formatInt(tag, "CustomModelData"));
        printPreset(source, "display.Name",       formatDisplayChild(tag, "Name"));
        printPreset(source, "display.Lore",       formatDisplayLore(tag));
        printPreset(source, "Potion",             formatString(tag, "Potion"));
        printPreset(source, "Unbreakable",        formatBool(tag, "Unbreakable"));
        printPreset(source, "RepairCost",         formatInt(tag, "RepairCost"));

        return 1;
    }

    private static int handleCustom(CommandSourceStack source) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("You are not holding an item."));
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        CompoundTag tag = held.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        source.sendSuccess(() -> Component.literal("§6--- Custom NBT Entries ---"), false);
        source.sendSuccess(() -> Component.literal("§7Item: §f" + itemId), false);
        source.sendSuccess(() -> Component.literal("§8(keys not recognized by the NBT editor presets — add these via '+ Custom NBT Key')"), false);

        if (tag.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  §8(item has no custom data)"), false);
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
            source.sendSuccess(() -> Component.literal("  §a(no custom NBT — all keys are preset-recognized or item has no custom data)"), false);
        }
        return 1;
    }

    // ---------- components (data components from MC 1.20.5+) ----------

    private static int handleComponents(CommandSourceStack source) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            source.sendFailure(Component.literal("This command can only be run by a player."));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            source.sendFailure(Component.literal("You are not holding an item."));
            return 0;
        }

        String itemId = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();

        source.sendSuccess(() -> Component.literal("§6--- Item Components ---"), false);
        source.sendSuccess(() -> Component.literal("§7Item: §f" + itemId), false);
        source.sendSuccess(() -> Component.literal("§8(click [Copy] to put the JSON value on your clipboard — paste it into the NBT editor's component value field)"), false);

        boolean any = false;
        for (TypedDataComponent<?> typed : held.getComponents()) {
            any = true;
            printComponent(source, typed);
        }

        if (!any) {
            source.sendSuccess(() -> Component.literal("  §8(item has no components)"), false);
        }
        return 1;
    }

    private static <T> void printComponent(CommandSourceStack source, TypedDataComponent<T> typed) {
        DataComponentType<T> type = typed.type();
        ResourceLocation id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
        String idStr = id == null ? "<unknown>" : id.toString();

        if (type.codec() == null) {
            source.sendSuccess(() -> Component.literal("  §8• §b" + idStr + " §8(transient — no codec, can't be matched)"), false);
            return;
        }

        DataResult<JsonElement> result = type.codec().encodeStart(JsonOps.INSTANCE, typed.value());
        var maybe = result.result();
        if (maybe.isEmpty()) {
            String err = result.error().map(e -> e.message()).orElse("unknown error");
            source.sendSuccess(() -> Component.literal("  §c• §b" + idStr + " §c(encode failed: " + err + ")"), false);
            return;
        }

        String jsonStr = maybe.get().toString();

        // Header line: bullet + ID + [Copy] button
        Style copyStyle = Style.EMPTY
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, jsonStr))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to copy this component's JSON value to clipboard")));

        MutableComponent copyBtn = Component.literal(" [Copy]").withStyle(copyStyle);
        MutableComponent line = Component.literal("  §8• §b" + idStr).append(copyBtn);
        source.sendSuccess(() -> line, false);

        // Preview line (truncated)
        String preview = jsonStr.length() > 120 ? jsonStr.substring(0, 117) + "..." : jsonStr;
        source.sendSuccess(() -> Component.literal("    §7" + preview), false);
    }

    // ---------- helpers ----------

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
