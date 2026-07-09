package net.bananemdnsa.historystages.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Resolves which EMI recipe a given widget belongs to, by walking the active
 * {@code dev.emi.emi.screen.RecipeScreen}'s {@code currentPage} (a list of {@code WidgetGroup}, each
 * with a public {@code recipe} field and {@code widgets} list).
 *
 * <p>Needed because EMI input slots carry no recipe context of their own ({@code SlotWidget.getRecipe()}
 * is null for them), and EMI's screen/group classes are internal — so they are accessed reflectively.
 * Everything is guarded; any failure yields {@code null}. Reflection handles are cached after first use.
 */
public final class EmiRecipeLookup {

    private EmiRecipeLookup() {}

    private static Field currentPageField;
    private static Field groupWidgetsField;
    private static Field groupRecipeField;

    /**
     * @return the recipe whose {@code WidgetGroup} contains {@code widget} on the current recipe
     *         screen, or {@code null} if it can't be determined.
     */
    public static EmiRecipe recipeOwning(Object widget) {
        if (widget == null) return null;
        try {
            Screen screen = Minecraft.getInstance().screen;
            if (screen == null) return null;

            if (currentPageField == null) {
                currentPageField = screen.getClass().getDeclaredField("currentPage");
                currentPageField.setAccessible(true);
            }
            Object pageObj = currentPageField.get(screen);
            if (!(pageObj instanceof List<?> page)) return null;

            for (Object group : page) {
                if (group == null) continue;
                if (groupWidgetsField == null) {
                    groupWidgetsField = group.getClass().getField("widgets");
                }
                Object widgetsObj = groupWidgetsField.get(group);
                if (widgetsObj instanceof List<?> widgets && widgets.contains(widget)) {
                    if (groupRecipeField == null) {
                        groupRecipeField = group.getClass().getField("recipe");
                    }
                    Object recipe = groupRecipeField.get(group);
                    return recipe instanceof EmiRecipe r ? r : null;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
