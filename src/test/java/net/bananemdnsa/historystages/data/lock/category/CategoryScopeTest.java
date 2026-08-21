package net.bananemdnsa.historystages.data.lock.category;

import java.util.EnumSet;
import java.util.Set;

import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which scopes a category means anything in is a fact about the data, so it lives on the category
 * — the editor and the runtime both read it from there rather than being told separately.
 */
class CategoryScopeTest {

    record Relic(String id) {}

    private static AddonLockCategory.Builder<Relic> builder() {
        return AddonLockCategory.<Relic>builder("mymod:relics")
                .tabLangKey("editor.mymod.tab.relics")
                .tooltipLangKey("editor.mymod.tooltip.relics")
                .storage(CategoryStorage.gson(Relic.class));
    }

    @Test
    void aCategoryMeansSomethingInBothScopesUnlessItSaysOtherwise() {
        assertEquals(EnumSet.allOf(StageScope.class), builder().build().supportedScopes());
    }

    @Test
    void anAddonCanDeclareItselfGlobalOnly() {
        Set<StageScope> scopes = builder().supportedScopes(StageScope.GLOBAL).build().supportedScopes();

        assertTrue(scopes.contains(StageScope.GLOBAL));
        assertFalse(scopes.contains(StageScope.INDIVIDUAL));
    }

    @Test
    void anAddonCanDeclareItselfIndividualOnly() {
        Set<StageScope> scopes = builder().supportedScopes(StageScope.INDIVIDUAL).build().supportedScopes();

        assertFalse(scopes.contains(StageScope.GLOBAL));
        assertTrue(scopes.contains(StageScope.INDIVIDUAL));
    }

    @Test
    void supportingNoScopeIsRejectedRatherThanSilentlyUseless() {
        assertThrows(IllegalArgumentException.class, () -> builder().supportedScopes());
    }

    @Test
    void recipesAndSpawnLocksAreGlobalOnly() {
        for (String id : new String[]{"historystages:recipes", "historystages:spawnlock"}) {
            Set<StageScope> scopes = LockCategories.byId(id).supportedScopes();
            assertEquals(Set.of(StageScope.GLOBAL), scopes, id + " should be global-only");
        }
    }

    @Test
    void everyOtherBuiltInMeansSomethingInBothScopes() {
        for (LockCategory<?> category : LockCategories.builtIns()) {
            if (category.id().equals("historystages:recipes")
                    || category.id().equals("historystages:spawnlock")) continue;
            assertEquals(EnumSet.allOf(StageScope.class), category.supportedScopes(),
                    category.id() + " changed scope unexpectedly");
        }
    }

    @Test
    void theResolverAgreesWithWhatTheCategoryDeclares() {
        LockCategory<?> globalOnly = builder().supportedScopes(StageScope.GLOBAL).build();

        assertTrue(CategoryLockResolver.supports(globalOnly, StageScope.GLOBAL));
        assertFalse(CategoryLockResolver.supports(globalOnly, StageScope.INDIVIDUAL));
    }
}
