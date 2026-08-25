package net.bananemdnsa.historystages.data.config;

import net.bananemdnsa.historystages.api.config.AddonConfigField;
import net.bananemdnsa.historystages.api.config.AddonConfigField.AddonConfigKind;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every field kind must be reachable through a public factory.
 *
 * <p>This exists because it once was not. {@code CUSTOM_SCREEN} sat in the enum, the config screen
 * mapped it and routed it, and {@code RegisterCustomFieldScreensEvent} promised it in its javadoc —
 * but no factory produced one and {@code Builder}'s constructor is private, so an addon could
 * register a screen for a field it had no way to declare. Nothing failed; the kind was simply
 * unreachable, and the class javadoc counting "eleven" made that look intended.
 *
 * <p>A per-kind test would not have caught it, because the missing kind is exactly the one nobody
 * writes a test for. Comparing the enum against what the public surface can actually build does.
 */
class AddonConfigKindReachabilityTest {

    @Test
    void everyFieldKindIsReachableThroughAPublicFactory() throws Exception {
        Set<AddonConfigKind> reachable = EnumSet.noneOf(AddonConfigKind.class);

        for (Method factory : AddonConfigField.class.getDeclaredMethods()) {
            int modifiers = factory.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) continue;
            if (factory.getReturnType() != AddonConfigField.Builder.class) continue;
            if (factory.getParameterCount() != 1 || factory.getParameterTypes()[0] != String.class) continue;

            AddonConfigField.Builder builder = (AddonConfigField.Builder) factory.invoke(null, "probe");
            reachable.add(complete(builder).kind());
        }

        assertEquals(EnumSet.allOf(AddonConfigKind.class), reachable,
                "Every AddonConfigKind needs a public AddonConfigField factory. A kind listed here as "
                        + "missing can be stored, rendered and routed, but no addon can declare one.");
    }

    /**
     * Fills in everything {@code build()} demands, for any kind.
     *
     * <p>{@code "0"} is the one default that satisfies the numeric kinds as well as the rest, and
     * the lone option satisfies {@code CHOICE} — which rejects both an empty option set and a
     * default that is not among the options. Other kinds ignore it.
     */
    private static AddonConfigField complete(AddonConfigField.Builder builder) {
        return builder
                .labelLangKey("l.probe")
                .defaultValue("0")
                .option("0", "l.probe.option")
                .read(() -> "0")
                .write(value -> { })
                .build();
    }
}
