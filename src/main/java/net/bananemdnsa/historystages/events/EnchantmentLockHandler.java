package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.LockFeedback;
import net.bananemdnsa.historystages.util.LockGate;
import net.bananemdnsa.historystages.util.LockMessages;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.AnvilUpdateEvent;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EnchantmentLockHandler {

    private static final String FEEDBACK_CATEGORY = "enchant";

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!Config.COMMON.lockEnchanting.get() && !Config.COMMON.individualLockEnchanting.get()) return;

        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ItemStack right = event.getRight();
        if (right.isEmpty()) return;

        boolean locked = LockGate.isItemLockedServer(
                right, serverPlayer,
                Config.COMMON.lockEnchanting,
                Config.COMMON.individualLockEnchanting);

        if (locked) {
            event.setCanceled(true);

            DebugLogger.runtimeThrottled("Enchantment Lock", "anvil_" + serverPlayer.getUUID(),
                    "<" + serverPlayer.getName().getString() + "> Anvil use blocked: right slot contains locked item '"
                            + ForgeRegistries.ITEMS.getKey(right.getItem()) + "'");

            LockFeedback.sendActionbar(serverPlayer, FEEDBACK_CATEGORY, LockMessages.enchantmentLocked());
        }
    }
}
