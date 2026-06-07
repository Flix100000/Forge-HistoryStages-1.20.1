package net.bananemdnsa.historystages.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.StageOverviewScreen;
import net.bananemdnsa.historystages.network.PacketHandler;
import net.bananemdnsa.historystages.network.RequestClusterShapesPacket;
import net.bananemdnsa.historystages.network.RequestStructureDebugPacket;
import net.bananemdnsa.historystages.network.ToggleStructureVizPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, value = Dist.CLIENT)
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
                                        .executes(ctx -> handleCustom(ctx.getSource()))))));
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

        String itemId = ForgeRegistries.ITEMS.getKey(held.getItem()) + "";
        CompoundTag tag = held.getTag();

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
