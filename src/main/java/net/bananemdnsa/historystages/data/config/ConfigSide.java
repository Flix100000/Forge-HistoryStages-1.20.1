package net.bananemdnsa.historystages.data.config;

/**
 * Which config a section's values live in.
 *
 * <p>Not cosmetic: a CLIENT section reads and writes on the client and syncs nothing, while a
 * COMMON section's values travel to the server through the existing save packet and are written
 * there. The two rows would look identical and behave completely differently, which is why a
 * section declares one side for all of its fields rather than mixing them.
 */
public enum ConfigSide {
    CLIENT,
    COMMON
}
