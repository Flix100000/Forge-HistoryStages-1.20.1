package net.bananemdnsa.historystages.mixin.toast;

import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Read access to {@code ToastComponent}'s private list of visible toasts so {@code
 * ToastClickHandler} can hit-test the toast under the cursor. Elements are the private inner
 * {@code ToastInstance}; read their fields via {@link ToastInstanceAccessor}.
 */
@Mixin(ToastComponent.class)
public interface ToastComponentAccessor {

    @Accessor("visible")
    List<?> historystages$getVisible();
}
