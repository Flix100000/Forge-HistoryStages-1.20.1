package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.client.display.HiddenDisplayResolver;
import net.bananemdnsa.historystages.data.display.DisplayMode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-only: replaces or hides the display name of an item that is locked by a stage
 * whose hidden-display name mode is HIDDEN or REPLACE. Because the first tooltip line is
 * derived from {@code getHoverName()}, this also covers the hover tooltip, JEI, anvil, etc.
 *
 * <p>Guarded to the client render thread so server-side {@code getHoverName} calls (e.g.
 * container titles in singleplayer) are never altered.</p>
 */
@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void historystages$hideOrReplaceName(CallbackInfoReturnable<Component> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !mc.isSameThread() || mc.player == null) return;

        ItemStack self = (ItemStack) (Object) this;
        HiddenDisplayResolver.Resolved resolved = HiddenDisplayResolver.resolve(self);
        if (!resolved.changesName()) return;

        if (resolved.nameMode() == DisplayMode.HIDDEN) {
            cir.setReturnValue(Component.empty());
        } else {
            cir.setReturnValue(Component.literal(resolved.nameText()));
        }
    }
}
