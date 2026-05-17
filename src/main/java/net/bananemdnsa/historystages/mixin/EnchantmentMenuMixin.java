package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(EnchantmentMenu.class)
public class EnchantmentMenuMixin {
    @Shadow @Final public int[] enchantClue;
    @Shadow @Final public int[] levelClue;

    private static final Map<UUID, Long> MESSAGE_COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_MS = 2000L;

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void historystages$clickMenuButton(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().isClientSide()
                || (!Config.COMMON.lockEnchanting && !Config.COMMON.individualLockEnchanting) || buttonId < 0 || buttonId > 2) {
            return;
        }

        int enchantId = enchantClue[buttonId];
        int level = levelClue[buttonId];
        if (enchantId < 0) {
            return;
        }

        Registry<Enchantment> registry = serverPlayer.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Enchantment enchantment = registry.byId(enchantId);
        if (enchantment == null) {
            return;
        }
        ResourceLocation enchantmentId = registry.getKey(enchantment);
        if (enchantmentId == null) {
            return;
        }

        if (StageLockHelper.isEnchantmentLockedForPlayer(enchantmentId.toString(), level, serverPlayer.getUUID())) {
            cir.setReturnValue(false);
            DebugLogger.runtimeThrottled("Enchantment Lock", "enchant_" + serverPlayer.getUUID(),
                    "<" + serverPlayer.getName().getString() + "> Enchantment '" + enchantmentId + "' blocked");
            long now = System.currentTimeMillis();
            Long last = MESSAGE_COOLDOWNS.get(serverPlayer.getUUID());
            if (last == null || now - last >= COOLDOWN_MS) {
                MESSAGE_COOLDOWNS.put(serverPlayer.getUUID(), now);
                serverPlayer.displayClientMessage(Component.translatable("message.historystages.enchantment_locked")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
            }
        }
    }
}
