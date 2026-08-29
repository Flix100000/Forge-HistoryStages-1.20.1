package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.DebugLogger;
import net.bananemdnsa.historystages.util.lock.LockFeedback;
import net.bananemdnsa.historystages.util.lock.LockMessages;
import net.bananemdnsa.historystages.util.lock.StageLockHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.IdMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
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

@Mixin(EnchantmentMenu.class)
public class EnchantmentMenuMixin {

    @Shadow @Final public int[] enchantClue;
    @Shadow @Final public int[] levelClue;

    private static final String FEEDBACK_CATEGORY = "enchant_table";

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true)
    private void onClickMenuButton(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        if (!Config.GAMEPLAY.lockEnchanting.get() && !Config.GAMEPLAY.individualLockEnchanting.get()) return;
        if (player.level().isClientSide()) return;
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (buttonId < 0 || buttonId > 2) return;

        int enchantId = enchantClue[buttonId];
        int level = levelClue[buttonId];

        if (enchantId < 0) return;

        // In MC 1.21, enchantments are data-driven. enchantClue stores the id from
        // registryAccess().registryOrThrow(Registries.ENCHANTMENT).asHolderIdMap()
        var enchantRegistry = serverPlayer.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        IdMap<Holder<Enchantment>> idmap = enchantRegistry.asHolderIdMap();
        Holder<Enchantment> holder = idmap.byId(enchantId);
        if (holder == null) return;

        ResourceKey<Enchantment> key = holder.unwrapKey().orElse(null);
        if (key == null) return;

        ResourceLocation enchantRL = key.location();

        if (StageLockHelper.isEnchantmentLockedForPlayer(enchantRL.toString(), level, serverPlayer.getUUID())) {
            cir.setReturnValue(false);

            DebugLogger.runtimeThrottled("Enchantment Lock", "enchant_table_" + serverPlayer.getUUID(),
                    "<" + serverPlayer.getName().getString() + "> Enchanting table blocked: enchantment '"
                            + enchantRL + "' level " + level + " is locked");

            LockFeedback.sendActionbar(serverPlayer, FEEDBACK_CATEGORY, LockMessages.enchantmentLocked());
        }
    }
}
