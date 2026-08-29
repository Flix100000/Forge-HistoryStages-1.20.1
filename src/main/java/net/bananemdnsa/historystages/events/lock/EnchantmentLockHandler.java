package net.bananemdnsa.historystages.events.lock;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.LockGate;
import net.bananemdnsa.historystages.util.lock.LockMessages;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;

@EventBusSubscriber(modid = HistoryStages.MOD_ID)
public class EnchantmentLockHandler {

    private static final String FEEDBACK_CATEGORY = "enchant";

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!Config.GAMEPLAY.lockEnchanting.get() && !Config.GAMEPLAY.individualLockEnchanting.get()) return;

        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ItemStack right = event.getRight();
        if (right.isEmpty()) return;

        boolean locked = LockGate.isItemLockedServer(
                right, serverPlayer,
                Config.GAMEPLAY.lockEnchanting,
                Config.GAMEPLAY.individualLockEnchanting);

        if (locked) {
            event.setCanceled(true);

            DebugLogger.runtimeThrottled("Enchantment Lock", "anvil_" + serverPlayer.getUUID(),
                    "<" + serverPlayer.getName().getString() + "> Anvil use blocked: right slot contains locked item '"
                            + BuiltInRegistries.ITEM.getKey(right.getItem()) + "'");

            LockFeedback.sendActionbar(serverPlayer, FEEDBACK_CATEGORY, LockMessages.enchantmentLocked());
        }
    }
}
