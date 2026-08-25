package net.bananemdnsa.historystages.data.config;

import net.bananemdnsa.historystages.network.CommonConfigSync;

/**
 * Hands {@link AddonConfigSections}' COMMON entries to {@link CommonConfigSync}.
 *
 * <p>This lives apart from the registry because {@code CommonConfigSync} pulls in NeoForge
 * through {@code Config}, and {@link AddonConfigSections} is unit-tested on a classpath that
 * carries neither Minecraft nor NeoForge. The registry decides what would be published, as plain
 * data; this class is the one place in {@code data/config} allowed to touch {@code
 * CommonConfigSync}, and it does nothing but hand that data over. There is nothing here worth
 * testing, which is the point.
 *
 * <p>Does not mint wire keys — {@link AddonConfigSections#commonEntries()} already minted them,
 * so the registry and the config screen cannot drift apart about what a value is called.
 */
public final class AddonConfigPublisher {

    private AddonConfigPublisher() {}

    /** Registers every COMMON section's fields into {@link CommonConfigSync}. Call after the registry freezes. */
    public static void publishCommonSections() {
        for (AddonConfigSections.CommonEntry entry : AddonConfigSections.commonEntries()) {
            CommonConfigSync.register(entry.wireKey(), entry.read(), entry.write());
        }
    }
}
