package net.bananemdnsa.historystages.client.editor.anim;

/**
 * Easing curves applied to a linear 0..1 progress at the point it is drawn.
 *
 * <p>Linear motion is what makes an interface read as mechanical: everything starts and stops
 * at full speed. Running the progress through one of these before mapping it to a colour,
 * offset or scale is what actually makes the editor feel finished.
 */
public final class Ease {

    private Ease() {
    }

    public static float clamp01(float t) {
        return t < 0.0f ? 0.0f : (t > 1.0f ? 1.0f : t);
    }

    /**
     * Decelerating curve — fast at the start, settling gently. The default for hover, fades
     * and anything responding to the cursor, because the response looks immediate while the
     * arrival stays soft.
     */
    public static float outCubic(float t) {
        float x = 1.0f - clamp01(t);
        return 1.0f - x * x * x;
    }

    /**
     * Accelerates then decelerates. Use for movement between two resting places — a panel
     * sliding in, a list changing folder — where both ends should look deliberate.
     */
    public static float inOutCubic(float t) {
        float x = clamp01(t);
        if (x < 0.5f) {
            return 4.0f * x * x * x;
        }
        float f = -2.0f * x + 2.0f;
        return 1.0f - f * f * f / 2.0f;
    }

    /**
     * A 0..1..0 round trip, for one-shot attention pulses such as a drop target confirming a
     * move. Peaks at {@code t = 0.5}.
     */
    public static float pulse(float t) {
        float x = clamp01(t);
        return (float) Math.sin(x * Math.PI);
    }

    /** Continuous breathing between 0 and 1 for looping indicators such as the unsaved dot. */
    public static float breathe(float t) {
        return 0.5f + 0.5f * (float) Math.sin(t * Math.PI * 2.0);
    }

    /** Linear interpolation, kept here so call sites read in the same vocabulary. */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
