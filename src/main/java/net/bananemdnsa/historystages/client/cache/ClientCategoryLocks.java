package net.bananemdnsa.historystages.client.cache;

import java.util.List;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.lock.category.CategoryLockResolver;
import net.bananemdnsa.historystages.data.lock.category.LockCategories;
import net.bananemdnsa.historystages.data.lock.category.LockCategory;

/**
 * The client-side counterpart of {@code CategoryLocks}, for addons that need to grey something
 * out or write a tooltip before the server has been asked.
 *
 * <p>Separate from {@code CategoryLocks} on purpose: that class is reachable from server-only
 * code, and pulling the client caches into it is how the crash fixed in commit 0469f73 happened.
 */
public final class ClientCategoryLocks {

    private ClientCategoryLocks() {}

    /** Is this subject gated for the local player, in either scope? */
    public static boolean isLocked(String categoryId, Object subject) {
        LockCategory<?> category = LockCategories.byId(categoryId);
        if (category == null) return false;

        return CategoryLockResolver.isLocked(category, subject,
                        StageManager.getStages(), ClientStageStates.global())
                || CategoryLockResolver.isLocked(category, subject,
                        StageManager.getIndividualStages(), ClientStageStates.individual());
    }

    /** The stages the local player still needs, global ones first. */
    public static List<String> missingStages(String categoryId, Object subject) {
        LockCategory<?> category = LockCategories.byId(categoryId);
        if (category == null) return List.of();

        return CategoryLockResolver.join(
                CategoryLockResolver.missingStages(category, subject,
                        StageManager.getStages(), ClientStageStates.global()),
                CategoryLockResolver.missingStages(category, subject,
                        StageManager.getIndividualStages(), ClientStageStates.individual()));
    }
}
