package net.bananemdnsa.historystages.api.editor;

/**
 * What one recipe type looks like in the editor.
 *
 * <p>The workstation is an item id rather than an {@code ItemStack} on purpose. A Minecraft type
 * in this signature would drag the whole game onto the unit-test classpath, which on this project
 * carries no Minecraft at all — a trap that has caught four separate classes here. The renderer
 * resolves the id when it draws.
 *
 * @param typeId            registry id of the recipe type, e.g. {@code minecraft:crafting}
 * @param workstationItemId item that stands for the station, or {@code ""} for none
 * @param accentColor       ARGB accent, drawn as the bar down the left of a card
 * @param nameLangKey       lang key for the type's display name, or {@code ""} to fall back to
 *                          {@link #displayFallback()}
 */
public record RecipeTypeMeta(String typeId, String workstationItemId, int accentColor,
                             String nameLangKey) {

    public RecipeTypeMeta {
        typeId = typeId == null ? "" : typeId;
        workstationItemId = workstationItemId == null ? "" : workstationItemId;
        nameLangKey = nameLangKey == null ? "" : nameLangKey;
    }

    /**
     * What to show when there is no lang key: the type's own registry id.
     *
     * <p>The old table answered {@code "Recipe"} for everything it did not know, so every modded
     * recipe in the editor was called the same thing. An id is not pretty, but it is the truth
     * and it tells two mod recipes apart.
     */
    public String displayFallback() {
        return typeId;
    }
}
