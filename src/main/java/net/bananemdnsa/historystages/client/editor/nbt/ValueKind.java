package net.bananemdnsa.historystages.client.editor.nbt;

/**
 * How a component's value is edited, and therefore what JSON the editor writes for it.
 *
 * <p>This is about the component's <em>encoded</em> form, not its Java type: {@code NbtMatcher}
 * compares the criterion against what the component's codec produces. A repair cost encodes to a
 * number, so the criterion has to be a number; writing "3" as a string would never match.
 *
 * <p>{@link #JSON} is the honest default for everything not in {@link NbtPresets}: a mod component
 * can encode to anything, and guessing on its behalf is how criteria end up silently unmatchable.
 */
public enum ValueKind {
    /** Plain text, stored as a JSON string. */
    TEXT,
    /** A whole number, or a {@code 1-4} range — the matcher understands both. */
    NUMBER,
    /** No value at all: the criterion only asks whether the component is present. */
    PRESENCE,
    /** One line per entry, stored as a JSON array of strings. */
    TEXT_LIST,
    /** Raw JSON, typed by hand. */
    JSON
}
