package net.bananemdnsa.historystages.data.lock;

import java.util.Set;

/**
 * Which recipe types an individual stage can actually gate.
 *
 * <p>The gate hangs on the <em>menu</em>: a station only knows who is crafting if a player is
 * standing at it. Furnaces, hoppers and autocrafters resolve recipes with nobody there, so one of
 * their recipes on an individual stage would sit in the file doing nothing at all.
 *
 * <p><strong>The type is a stand-in, not a guarantee.</strong> A mod machine with its own menu
 * that uses {@code minecraft:crafting} internally does not pass through our hooks. It is still the
 * best filter available from a recipe id, and it is documented as "supported as far as we know".
 *
 * <p>One set, two readers: the editor's recipe picker and the load-time audit. Ids rather than
 * {@code RecipeType} constants so the rule stays testable — the test classpath carries no
 * Minecraft.
 */
public final class IndividualRecipeSupport {

    /**
     * Vanilla ships exactly seven recipe types. These three resolve at a station with a player in
     * front of it: the crafting table and the 2x2 inventory grid, the stonecutter, the smithing
     * table. The four cooking types run in a block entity with nobody there.
     *
     * <p>Adding an id here without adding the matching menu hook makes the picker promise a gate
     * that does not exist.
     */
    public static final Set<String> SUPPORTED_TYPE_IDS =
            Set.of("minecraft:crafting", "minecraft:stonecutting", "minecraft:smithing");

    private IndividualRecipeSupport() {}

    /** @param recipeTypeId the registry id of a recipe type, e.g. {@code minecraft:crafting} */
    public static boolean supports(String recipeTypeId) {
        return recipeTypeId != null && SUPPORTED_TYPE_IDS.contains(recipeTypeId);
    }
}
