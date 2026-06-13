package net.bananemdnsa.historystages.mixin.toast;

import net.minecraft.client.gui.components.toasts.Toast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Read access to the private {@code ToastComponent.ToastInstance} fields so {@link
 * net.bananemdnsa.historystages.client.ToastLayout} can map each occupied slot to the height of
 * the toast in it.
 */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public interface ToastInstanceAccessor {

    @Accessor("index")
    int historystages$getIndex();

    @Accessor("toast")
    Toast historystages$getToast();
}
