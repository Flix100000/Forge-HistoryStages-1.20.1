package net.bananemdnsa.historystages.client.toast;

import net.minecraft.Util;

/**
 * Shared layout state that lets the two {@code ToastComponent} mixins stack notifications by
 * their real pixel height instead of Minecraft's fixed 32px slot grid.
 *
 * <p>Vanilla positions every toast at {@code index * 32} and reserves
 * {@code ceil(height / 32)} slots per toast. Toasts that aren't an exact multiple of 32px tall
 * therefore leave a gap below them, and once a toast spans two slots only two of the five slots
 * remain usable. {@code ToastComponentMixin} instead recomputes, each frame, the cumulative
 * y-offset of every occupied slot from the toasts' actual {@code height()}; {@code
 * ToastInstanceMixin} reads that offset back to override the y translation so toasts sit flush.</p>
 *
 * <p>When a toast disappears the slots below it would snap upward to close the gap. To avoid that
 * jump the rendered offset is eased toward its target each frame; a slot that has just become
 * occupied snaps instead, so newly arriving toasts don't slide in from the top (their horizontal
 * slide-in is handled by vanilla).</p>
 *
 * <p>There is exactly one {@code ToastComponent} and it is only ever touched on the render
 * thread, so a single static table is safe.</p>
 */
public final class ToastLayout {

    /** Mirrors {@code ToastComponent.SLOT_COUNT}. */
    public static final int SLOT_COUNT = 5;

    /** Slot height of the vanilla grid; used as the fallback offset. */
    private static final int SLOT_HEIGHT = 32;

    /** Time constant (ms) of the ease toward the target offset; smaller is snappier. */
    private static final float EASE_TAU = 60.0F;

    private static final float[] target = new float[SLOT_COUNT];
    private static final float[] animated = new float[SLOT_COUNT];
    private static final boolean[] occupied = new boolean[SLOT_COUNT];
    private static long lastMillis = -1L;

    private ToastLayout() {}

    /**
     * Rebuilds the offset table from the height of the toast occupying each slot
     * ({@code 0} for empty slots). Each target becomes the summed height of all slots above it;
     * the rendered offset then eases toward that target so gap-closing is animated.
     */
    public static void recompute(int[] heightBySlot) {
        long now = Util.getMillis();
        float dt = lastMillis < 0L ? 0.0F : (now - lastMillis);
        lastMillis = now;
        float factor = dt <= 0.0F ? 1.0F : (float) (1.0 - Math.exp(-dt / EASE_TAU));

        int running = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            target[i] = running;
            running += heightBySlot[i];
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            boolean nowOccupied = heightBySlot[i] > 0;
            // Only ease a slot that stays occupied (a toast above it left and it should glide up).
            // Snap on (re)appearance or while empty, so entries use vanilla's horizontal slide-in.
            if (!occupied[i] || !nowOccupied) {
                animated[i] = target[i];
            } else {
                animated[i] += (target[i] - animated[i]) * factor;
            }
            occupied[i] = nowOccupied;
        }
    }

    /** Pixel y-offset for the toast in the given slot, falling back to the vanilla grid. */
    public static int offset(int index) {
        return (index >= 0 && index < SLOT_COUNT) ? Math.round(animated[index]) : index * SLOT_HEIGHT;
    }
}
