package net.bananemdnsa.historystages.data.lock.category;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;

/**
 * The eleven categories the mod ships with, in editor tab order.
 *
 * <p>Each one is a thin adapter onto the typed accessors {@link StageEntry} already has. The
 * point is not to move data — it is to stop every consumer from naming all eleven fields.
 */
final class BuiltInLockCategories {

    private BuiltInLockCategories() {}

    static List<LockCategory<?>> create() {
        List<LockCategory<?>> categories = new ArrayList<>();

        categories.add(new Simple<>("items", "items",
                StageEntry::getItemEntries, StageEntry::setItemEntries,
                StageEntry::getAllItemIds));

        categories.add(new Simple<>("tags", "tags",
                StageEntry::getTagEntries, StageEntry::setTagEntries,
                StageEntry::getNbtFreeTags));

        categories.add(new Simple<>("mods", "mods",
                StageEntry::getModEntries, StageEntry::setModEntries,
                StageEntry::getMods));

        // Mod exceptions carve holes in the mods category; an overlap between a global and an
        // individual exception is not a dual-phase lock, so this one opts out.
        categories.add(new Simple<>("mod_exceptions", "exceptions",
                StageEntry::getModExceptionEntries, StageEntry::setModExceptionEntries,
                stage -> List.of()));

        // Recipes were never part of dual-phase detection. Preserved as-is.
        categories.add(new Simple<>("recipes", "recipes",
                StageEntry::getRecipes, StageEntry::setRecipes,
                stage -> List.of()));

        categories.add(new Simple<>("dimensions", "dimensions",
                StageEntry::getDimensions, StageEntry::setDimensions,
                StageEntry::getDimensions));

        categories.add(new AttackLock());
        categories.add(new SpawnLock());
        categories.add(new InteractionLock());

        categories.add(new Simple<>("structures", "structures",
                StageEntry::getStructures, StageEntry::setStructures,
                StageEntry::getStructures));

        categories.add(new Simple<>("biomes", "biomes",
                StageEntry::getBiomes, StageEntry::setBiomes,
                StageEntry::getBiomes));

        return categories;
    }

    /** Everything whose entries are a plain list on {@link StageEntry}. */
    private record Simple<T>(String name, String tabName,
                             Function<StageEntry, List<T>> reader,
                             BiConsumer<StageEntry, List<T>> writer,
                             Function<StageEntry, List<String>> dualPhase)
            implements LockCategory<T> {

        @Override public String id() { return "historystages:" + name; }
        @Override public String tabLangKey() { return "editor.historystages.tab." + tabName; }
        @Override public String tooltipLangKey() { return "editor.historystages.tooltip." + tabName; }
        @Override public List<T> read(StageEntry stage) { return reader.apply(stage); }
        @Override public void write(StageEntry stage, List<T> entries) { writer.accept(stage, entries); }
        @Override public List<String> dualPhaseIds(StageEntry stage) { return dualPhase.apply(stage); }
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

        @Override public List<String> dualPhaseIds(StageEntry stage) {
            List<String> ids = new ArrayList<>(stage.getEntities().getAttacklock());
            for (EntitySpawnLockEntry spawn : stage.getEntities().getSpawnlock()) {
                if (!spawn.hasLockSources()) ids.add(spawn.getId());
            }
            return ids;
        }
    }

    /** Spawn locks. Never took part in dual-phase detection; that is preserved. */
    private static final class SpawnLock implements LockCategory<EntitySpawnLockEntry> {
        @Override public String id() { return "historystages:spawnlock"; }
        @Override public String tabLangKey() { return "editor.historystages.tab.spawn"; }
        @Override public String tooltipLangKey() { return "editor.historystages.tooltip.spawn"; }

        @Override public List<EntitySpawnLockEntry> read(StageEntry stage) {
            return stage.getEntities().getSpawnlock();
        }

        @Override public void write(StageEntry stage, List<EntitySpawnLockEntry> entries) {
            stage.getEntities().setSpawnlock(entries);
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

        @Override public List<String> dualPhaseIds(StageEntry stage) {
            List<String> ids = new ArrayList<>();
            for (EntityInteractionLockEntry entry : stage.getEntities().getInteractionlock()) {
                ids.add(entry.getId());
            }
            return ids;
        }
    }
}
