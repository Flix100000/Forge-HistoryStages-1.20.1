package net.bananemdnsa.historystages.client.editor.nbt;

/**
 * One row of NBT match criteria, as the editor holds it while it is being edited.
 *
 * <p>Deliberately free of {@code net.minecraft} types: the codec, the validator and the preset
 * tables are all unit-tested, and a Minecraft import anywhere in this hierarchy would drag the
 * whole game onto the test classpath.
 */
public sealed interface NbtCriterion
        permits EnchantmentListCriterion, ComponentCriterion, TextListCriterion, CustomDataCriterion {

    CriterionKind kind();

    /**
     * Identity for the picker's "already added" check. Top-level criteria answer with their key,
     * component-backed ones with {@code components.<id>}.
     */
    String identity();

    /** True when the criterion carries nothing worth writing. Empty criteria are dropped on save. */
    boolean isEmpty();
}
