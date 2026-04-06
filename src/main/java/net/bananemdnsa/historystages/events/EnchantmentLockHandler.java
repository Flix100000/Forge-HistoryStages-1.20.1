package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EnchantmentLockHandler {

    private static final Map<UUID, Long> MESSAGE_COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 2000;

    /**
     * Prevents combining locked enchanted books via anvil.
     * If the right slot contains a locked item (e.g. a locked enchanted book), cancel the anvil output.
     */
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!Config.COMMON.lockEnchanting.get() && !Config.COMMON.individualLockEnchanting.get()) return;

        Player player = event.getPlayer();
        if (player == null || player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        ItemStack right = event.getRight();
        if (right.isEmpty()) return;

        boolean locked = false;

        if (Config.COMMON.lockEnchanting.get()) {
            locked = StageLockHelper.isItemLockedForPlayer(right, serverPlayer);
        }
        if (!locked && Config.COMMON.individualLockEnchanting.get()) {
            locked = StageLockHelper.isItemLockedByIndividualStage(right, serverPlayer.getUUID());
        }

        if (locked) {
            event.setCanceled(true);

            DebugLogger.runtimeThrottled("Enchantment Lock", "anvil_" + serverPlayer.getUUID(),
                    "<" + serverPlayer.getName().getString() + "> Anvil use blocked: right slot contains locked item '"
                            + ForgeRegistries.ITEMS.getKey(right.getItem()) + "'");

            long now = System.currentTimeMillis();
            Long last = MESSAGE_COOLDOWNS.get(serverPlayer.getUUID());
            if (last == null || (now - last) >= COOLDOWN_MS) {
                MESSAGE_COOLDOWNS.put(serverPlayer.getUUID(), now);
                serverPlayer.displayClientMessage(
                        Component.translatable("message.historystages.enchantment_locked")
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC),
                        true
                );
            }
        }
    }
}
