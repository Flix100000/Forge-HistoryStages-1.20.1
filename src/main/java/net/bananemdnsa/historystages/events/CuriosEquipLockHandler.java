package net.bananemdnsa.historystages.events;

import net.astr0.historystages.api.HistoryStagesAPI;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.RuntimeStageManager;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.RegistryHelper;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.event.CurioEquipEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CuriosEquipLockHandler extends AbstractHandlerGroup {

    private static final Map<UUID, Long> MESSAGE_COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 2000;

    @SubscribeEvent
    public static void onCurioEquip(CurioEquipEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!Config.COMMON.lockItemUsage.get() && !Config.COMMON.individualLockItemUsage.get()) return;

        ItemStack stack = event.getStack();
        if (stack.isEmpty()) return;

        boolean isClient = player.level().isClientSide();
        boolean locked = isItemLocked(stack, player);

        if (locked) {
            event.setResult(Event.Result.DENY);
            if (!isClient) {
                ResourceLocation itemRL = RegistryHelper.getResourceLocationFromRegistry(event.getStack().getItem());
                DebugLogger.runtime("Curios Lock", player.getName().getString(),
                        "Equipping locked item '" + itemRL + "' in curio slot '"
                                + event.getSlotContext().identifier() + "' — blocked");
                showMessage((ServerPlayer) player);
            }
        }
    }

    // TODO(Bug): this technically causes a bug in the current state as it no longer respects the individual settings
    // for global vs individual item locks. For now we will intentionally cause this issue just so we can get to testing
    // all TODO tags will need to be cleaned up prior to main merge
    private static boolean isItemLocked(ItemStack item, Player player) {
        if (Config.COMMON.lockItemUsage.get()) {
             return stageManager.isLocked(HistoryStagesAPI.ITEMS, item, player);
        }
        if (Config.COMMON.individualLockItemUsage.get()) {
            return stageManager.isLocked(HistoryStagesAPI.ITEMS, item, player);
        }

        return false;
    }

    private static void showMessage(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = MESSAGE_COOLDOWNS.get(player.getUUID());
        if (last != null && (now - last) < COOLDOWN_MS) return;
        MESSAGE_COOLDOWNS.put(player.getUUID(), now);

        player.displayClientMessage(
                Component.translatable("message.historystages.item_locked")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                true
        );
    }
}
