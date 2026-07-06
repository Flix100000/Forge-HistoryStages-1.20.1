package net.bananemdnsa.historystages.mixin.toast;

import net.bananemdnsa.historystages.client.toast.ToastLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Makes notifications stack by their real height rather than on the vanilla 32px slot grid.
 *
 * <p>{@link #historystages$computeOffsets} rebuilds the per-slot pixel offset table from the
 * currently visible toasts' actual heights, before any of them render this frame.
 * {@link ToastInstanceMixin} then applies those offsets to the y translation, so a toast sits
 * flush below the one above it regardless of either toast's height.</p>
 *
 * <p>Custom toasts that want to stay within a single grid slot (and thus keep up to five
 * notifications visible) only need to override {@code slotCount()} to return 1; the offsets here
 * keep them from overlapping.</p>
 */
@Mixin(ToastComponent.class)
public abstract class ToastComponentMixin {

    @Shadow @Final private List<?> visible;

    @Inject(method = "render", at = @At("HEAD"))
    private void historystages$computeOffsets(GuiGraphics guiGraphics, CallbackInfo ci) {
        int[] heightBySlot = new int[ToastLayout.SLOT_COUNT];
        for (Object instance : visible) {
            if (instance == null) {
                continue;
            }
            ToastInstanceAccessor accessor = (ToastInstanceAccessor) instance;
            int index = accessor.historystages$getIndex();
            if (index >= 0 && index < heightBySlot.length) {
                heightBySlot[index] = accessor.historystages$getToast().height();
            }
        }
        ToastLayout.recompute(heightBySlot);
    }
}
