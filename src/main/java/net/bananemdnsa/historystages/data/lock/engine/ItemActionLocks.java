package net.bananemdnsa.historystages.data.lock.engine;

import java.util.List;

import net.bananemdnsa.historystages.data.ItemEntry;
import net.bananemdnsa.historystages.data.NbtMatcher;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;
import net.bananemdnsa.historystages.data.lock.category.BuiltInLockMatching;

/**
 * Whether one specific action on an item is blocked — the one lock question that is deliberately
 * not a {@link net.bananemdnsa.historystages.data.lock.category.LockCategory#gates} question.
 *
 * <p>{@code gates} answers "does this stage lock this?". This asks something narrower and
 * order-dependent: <em>which entry matches first</em> — items, then mods, then tags — and what
 * does <em>that</em> entry say about this action. The first match decides even when it says
 * "allowed", so a later entry that would have blocked never gets a vote. Lifting that precedence
 * into the category contract would impose it on every addon, and no addon needs it.
 */
public final class ItemActionLocks {

    private ItemActionLocks() {}

    /**
     * True when {@code action} is blocked for this subject by this stage. False when the item
     * does not match the stage at all, and equally false when the first matching entry allows
     * the action.
     */
    public static boolean isBlockedBy(StageEntry stage, LockSubjects.ItemSubject subject, String action) {
        for (ItemEntry entry : stage.getItemEntries()) {
            if (!entry.getId().equals(subject.itemId())) continue;
            boolean nbtMatch = !entry.hasNbt()
                    || (subject.stack() != null && NbtMatcher.matches(subject.stack(), entry.getNbt()));
            if (nbtMatch) return isActionInList(entry.getLockActions(), action);
        }

        for (NamedLockEntry modEntry : stage.getModEntries()) {
            if (modEntry.getId().equals(subject.modId())
                    && !stage.isModExcepted(subject.itemId(), subject.stack())) {
                return isActionInList(modEntry.getLockActions(), action);
            }
        }

        if (subject.item() != null) {
            for (NamedLockEntry tagEntry : stage.getTagEntries()) {
                if (BuiltInLockMatching.tagEntryMatches(tagEntry, subject)) {
                    return isActionInList(tagEntry.getLockActions(), action);
                }
            }
        }

        return false;
    }

    /**
     * null = every action is locked, which is the default and what a missing {@code unlock_actions}
     * in the JSON means. An empty list = nothing is locked. A non-empty list = only the listed
     * actions are locked.
     */
    private static boolean isActionInList(List<String> lockActions, String action) {
        if (lockActions == null) return true;
        return lockActions.contains(action);
    }
}
