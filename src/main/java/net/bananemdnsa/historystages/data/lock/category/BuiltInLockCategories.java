package net.bananemdnsa.historystages.data.lock.category;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.engine.StageScope;
import net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.engine.LockSubjects;

/**
 * The eleven categories the mod ships with, in editor tab order.
 *
 * <p>Each one is a thin adapter onto the typed accessors {@link StageEntry} already has. The
 * point is not to move data — it is to stop every consumer from naming all eleven fields.
 */
final class BuiltInLockCategories {

    private BuiltInLockCategories() {}

    /**
     * The shape four built-ins share: the stored entry <em>is</em> the gated id.
     *
     * <p>The {@code instanceof} is not defensive noise. {@link LockCategory#matches} takes an
     * {@code Object} because a category cannot constrain what it is asked about, and a
     * multi-category pass over one stage hands the same subject to every category in turn — so a
     * category that cannot make sense of it has to answer "no" rather than throw.
     */
    private static final BiPredicate<String, Object> ID_EQUALS =
            (entry, subject) -> subject instanceof String id && entry.equals(id);

    /**
     * For categories whose entries never gate anything by themselves: mod exceptions carve holes
     * in the mods category rather than locking, and items, tags and mods answer through matchers
     * that need Minecraft — those are wired separately.
     */
    private static final BiPredicate<Object, Object> NEVER = (entry, subject) -> false;

    /** {@link #NEVER} at whatever entry type the caller needs; it looks at neither argument. */
    @SuppressWarnings("unchecked")
    private static <T> BiPredicate<T, Object> never() {
        return (BiPredicate<T, Object>) NEVER;
    }

    static List<LockCategory<?>> create() {
        List<LockCategory<?>> categories = new ArrayList<>();

        categories.add(new Simple<>("items", "items", "item",
                StageEntry::getItemEntries, StageEntry::setItemEntries,
                StageEntry::getAllItemIds,
                (entry, subject) -> subject instanceof LockSubjects.ItemSubject item
                        && BuiltInLockMatching.itemEntryMatches(entry, item)));

        categories.add(new Simple<>("tags", "tags", "tag",
                StageEntry::getTagEntries, StageEntry::setTagEntries,
                StageEntry::getNbtFreeTags,
                (entry, subject) -> subject instanceof LockSubjects.ItemSubject item
                        && BuiltInLockMatching.tagEntryMatches(entry, item)));

        categories.add(new ModLock());

        // Mod exceptions carve holes in the mods category; an overlap between a global and an
        // individual exception is not a dual-phase lock, so this one opts out.
        categories.add(new Simple<>("mod_exceptions", "exceptions", "",
                StageEntry::getModExceptionEntries, StageEntry::setModExceptionEntries,
                stage -> List.of(), never()));

        // Recipes were never part of dual-phase detection. Preserved as-is, and global-only:
        // there is no per-player recipe gate to write to.
        categories.add(new GlobalOnly<>("recipes", "recipes", "",
                StageEntry::getRecipes, StageEntry::setRecipes,
                stage -> List.of(), ID_EQUALS));

        categories.add(new Simple<>("dimensions", "dimensions", "dimension",
                StageEntry::getDimensions, StageEntry::setDimensions,
                StageEntry::getDimensions, ID_EQUALS));

        categories.add(new AttackLock());
        categories.add(new SpawnLock());
        categories.add(new InteractionLock());

        categories.add(new Simple<>("structures", "structures", "structure",
                StageEntry::getStructures, StageEntry::setStructures,
                StageEntry::getStructures, ID_EQUALS));

        categories.add(new Simple<>("biomes", "biomes", "biome",
                StageEntry::getBiomes, StageEntry::setBiomes,
                StageEntry::getBiomes, ID_EQUALS));

        return categories;
    }

    /** Everything whose entries are a plain list on {@link StageEntry}. */
    private record Simple<T>(String name, String tabName, String dualPhaseLabel,
                             Function<StageEntry, List<T>> reader,
                             BiConsumer<StageEntry, List<T>> writer,
                             Function<StageEntry, List<String>> dualPhase,
                             BiPredicate<T, Object> matcher)
            implements LockCategory<T> {

        @Override public String id() { return "historystages:" + name; }
        @Override public String tabLangKey() { return "editor.historystages.tab." + tabName; }
        @Override public String tooltipLangKey() { return "editor.historystages.tooltip." + tabName; }
        @Override public List<T> read(StageEntry stage) { return reader.apply(stage); }
        @Override public void write(StageEntry stage, List<T> entries) { writer.accept(stage, entries); }
        @Override public List<String> globalDualPhaseIds(StageEntry stage) { return dualPhase.apply(stage); }
        @Override public List<String> individualDualPhaseIds(StageEntry stage) { return dualPhase.apply(stage); }
        @Override public boolean matches(T entry, Object subject) { return matcher.test(entry, subject); }
    }

    /** A {@link Simple} category that only means anything on a global stage. */
    private static final class GlobalOnly<T> implements LockCategory<T> {
        private final Simple<T> delegate;

        GlobalOnly(String name, String tabName, String dualPhaseLabel,
                   java.util.function.Function<StageEntry, List<T>> reader,
                   java.util.function.BiConsumer<StageEntry, List<T>> writer,
                   java.util.function.Function<StageEntry, List<String>> dualPhase,
                   BiPredicate<T, Object> matcher) {
            this.delegate = new Simple<>(name, tabName, dualPhaseLabel, reader, writer, dualPhase, matcher);
        }

        @Override public java.util.Set<StageScope> supportedScopes() {
            return java.util.EnumSet.of(StageScope.GLOBAL);
        }

        @Override public String id() { return delegate.id(); }
        @Override public String tabLangKey() { return delegate.tabLangKey(); }
        @Override public String tooltipLangKey() { return delegate.tooltipLangKey(); }
        @Override public String dualPhaseLabel() { return delegate.dualPhaseLabel(); }
        @Override public List<T> read(StageEntry stage) { return delegate.read(stage); }
        @Override public void write(StageEntry stage, List<T> entries) { delegate.write(stage, entries); }
        @Override public List<String> globalDualPhaseIds(StageEntry stage) { return delegate.globalDualPhaseIds(stage); }
        @Override public List<String> individualDualPhaseIds(StageEntry stage) { return delegate.individualDualPhaseIds(stage); }
        @Override public boolean matches(T entry, Object subject) { return delegate.matches(entry, subject); }
    }

    /**
     * Mod locks. Needs {@link LockCategory#gates} rather than only {@link LockCategory#matches}
     * because the veto lives on the stage: a mod entry gates every item of that mod
     * <em>except</em> the ones the stage's exception list carves out, and an entry on its own
     * cannot see that list.
     */
    private static final class ModLock implements LockCategory<NamedLockEntry> {
        @Override public String id() { return "historystages:mods"; }
        @Override public String tabLangKey() { return "editor.historystages.tab.mods"; }
        @Override public String tooltipLangKey() { return "editor.historystages.tooltip.mods"; }
        @Override public String dualPhaseLabel() { return "mod"; }

        @Override public List<NamedLockEntry> read(StageEntry stage) { return stage.getModEntries(); }

        @Override public void write(StageEntry stage, List<NamedLockEntry> entries) {
            stage.setModEntries(entries);
        }

        @Override public List<String> globalDualPhaseIds(StageEntry stage) { return stage.getMods(); }
        @Override public List<String> individualDualPhaseIds(StageEntry stage) { return stage.getMods(); }

        /**
         * Not reached through {@link #gates}, which replaces the entry loop. Kept honest for a
         * caller asking about one entry directly: without a stage there is no exception list, so
         * the answer is the un-vetoed one.
         */
        @Override public boolean matches(NamedLockEntry entry, Object subject) {
            return subject instanceof LockSubjects.ItemSubject item
                    && entry.getId().equals(item.modId());
        }

        @Override public boolean gates(StageEntry stage, Object subject) {
            if (!(subject instanceof LockSubjects.ItemSubject item)) return false;
            for (NamedLockEntry entry : stage.getModEntries()) {
                if (BuiltInLockMatching.modEntryMatches(entry, stage, item)) return true;
            }
            return false;
        }
    }

    /**
     * Attack locks. Dual-phase also counts spawn-lock entries that block every source, because
     * such an entry implies an attack lock — the same rule the old detectOverlaps applied.
     */
    private static final class AttackLock implements LockCategory<String> {
        @Override public String id() { return "historystages:attacklock"; }
        @Override public String tabLangKey() { return "editor.historystages.tab.attack"; }
        @Override public String tooltipLangKey() { return "editor.historystages.tooltip.attack"; }

        @Override public List<String> read(StageEntry stage) {
            return stage.getEntities().getAttacklock();
        }

        @Override public void write(StageEntry stage, List<String> entries) {
            stage.getEntities().setAttacklock(entries);
        }

        @Override public String dualPhaseLabel() { return "attacklock entity"; }

        @Override public boolean matches(String entry, Object subject) {
            return subject instanceof String entityId && entry.equals(entityId);
        }

        /**
         * Also gates when a spawn lock on the same stage blocks every source, because such an
         * entry implies an attack lock. Reading a neighbouring category is exactly what the
         * entry loop cannot do, and the reason this override exists.
         *
         * <p>No scope parameter, deliberately. Globally the old code absorbed spawn locks and
         * individually it did not — but that asymmetry is a property of the data, not of the
         * question: {@code StageManager.stripUnsupportedIndividualCategories} clears spawn locks
         * out of individual stages at load time, so the second loop finds nothing there anyway.
         */
        @Override public boolean gates(StageEntry stage, Object subject) {
            if (!(subject instanceof String entityId)) return false;
            if (stage.getEntities().getAttacklock().contains(entityId)) return true;
            for (EntitySpawnLockEntry spawn : stage.getEntities().getSpawnlock()) {
                if (spawn.getId().equals(entityId) && !spawn.hasLockSources()) return true;
            }
            return false;
        }

        /** A spawn lock that blocks every source implies an attack lock — but only globally. */
        @Override public List<String> globalDualPhaseIds(StageEntry stage) {
            List<String> ids = new ArrayList<>(stage.getEntities().getAttacklock());
            for (EntitySpawnLockEntry spawn : stage.getEntities().getSpawnlock()) {
                if (!spawn.hasLockSources()) ids.add(spawn.getId());
            }
            return ids;
        }

        /** The individual side has never absorbed spawn locks. Preserved deliberately. */
        @Override public List<String> individualDualPhaseIds(StageEntry stage) {
            return stage.getEntities().getAttacklock();
        }
    }

    /**
     * Spawn locks. Never took part in dual-phase detection, and global-only: the data model
     * has no per-player spawn gate.
     */
    private static final class SpawnLock implements LockCategory<EntitySpawnLockEntry> {
        @Override public java.util.Set<StageScope> supportedScopes() {
            return java.util.EnumSet.of(StageScope.GLOBAL);
        }

        @Override public String id() { return "historystages:spawnlock"; }
        @Override public String tabLangKey() { return "editor.historystages.tab.spawn"; }
        @Override public String tooltipLangKey() { return "editor.historystages.tooltip.spawn"; }

        @Override public List<EntitySpawnLockEntry> read(StageEntry stage) {
            return stage.getEntities().getSpawnlock();
        }

        @Override public void write(StageEntry stage, List<EntitySpawnLockEntry> entries) {
            stage.getEntities().setSpawnlock(entries);
        }

        /**
         * A null source narrows the question to "is there an entry for this entity in this
         * dimension at all" — the {@code EntityJoinLevel} fallback, which fires where no spawn
         * reason is available. With a source it is the real question.
         */
        @Override public boolean matches(EntitySpawnLockEntry entry, Object subject) {
            if (!(subject instanceof LockSubjects.SpawnSubject spawn)) return false;
            if (!entry.getId().equals(spawn.entityId())) return false;
            if (!entry.blocksDimension(spawn.dimension())) return false;
            return spawn.source() == null || entry.blocksSource(spawn.source());
        }
    }

    private static final class InteractionLock implements LockCategory<EntityInteractionLockEntry> {
        @Override public String id() { return "historystages:interactionlock"; }
        @Override public String tabLangKey() { return "editor.historystages.tab.interaction"; }
        @Override public String tooltipLangKey() { return "editor.historystages.tooltip.interaction"; }

        @Override public List<EntityInteractionLockEntry> read(StageEntry stage) {
            return stage.getEntities().getInteractionlock();
        }

        @Override public void write(StageEntry stage, List<EntityInteractionLockEntry> entries) {
            stage.getEntities().setInteractionlock(entries);
        }

        @Override public String dualPhaseLabel() { return "interactionlock entity"; }

        /**
         * Unlike attack locks, this one is standalone: a spawn lock does not imply it. The entry
         * decides on its own, so the plain entry loop is the whole answer.
         */
        @Override public boolean matches(EntityInteractionLockEntry entry, Object subject) {
            if (!(subject instanceof LockSubjects.InteractionSubject interaction)) return false;
            return entry.getId().equals(interaction.entityId())
                    && entry.blocksAction(interaction.action())
                    && entry.matchesItem(interaction.held());
        }

        @Override public List<String> globalDualPhaseIds(StageEntry stage) {
            return ids(stage);
        }

        @Override public List<String> individualDualPhaseIds(StageEntry stage) {
            return ids(stage);
        }

        private static List<String> ids(StageEntry stage) {
            List<String> ids = new ArrayList<>();
            for (EntityInteractionLockEntry entry : stage.getEntities().getInteractionlock()) {
                ids.add(entry.getId());
            }
            return ids;
        }
    }
}
