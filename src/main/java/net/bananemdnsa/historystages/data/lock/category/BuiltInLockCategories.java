package net.bananemdnsa.historystages.data.lock.category;

import net.bananemdnsa.historystages.api.lock.LockCategory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.api.stage.StageScope;
import net.bananemdnsa.historystages.data.lock.EntityInteractionLockEntry;
import net.bananemdnsa.historystages.data.lock.NamedLockEntry;
import net.bananemdnsa.historystages.data.lock.EntitySpawnLockEntry;
import net.bananemdnsa.historystages.data.lock.engine.LockSubjects;

/**
 * The fifteen categories the mod ships with, in editor tab order.
 *
 * <p>Each one is a thin adapter onto the typed accessors {@link StageEntry} already has. The
 * point is not to move data — it is to stop every consumer from naming all twelve fields.
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

    /** Four built-ins are asked about a bare id, so the subject is its own index key. */
    private static final Function<Object, String> SUBJECT_IS_THE_KEY =
            subject -> subject instanceof String id ? id : null;

    /** No index: the category is scanned in full, which is correct and only slower. */
    private static final Function<StageEntry, List<String>> NO_INDEX = stage -> List.of();

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
                        && BuiltInLockMatching.itemEntryMatches(entry, item),
                // Items, mods and tags are narrowed by LockRelevanceIndex instead, which serves
                // all three key kinds at once — an id, a namespace and tag membership.
                NO_INDEX));

        categories.add(new FluidLock());

        categories.add(new Simple<>("tags", "tags", "tag",
                StageEntry::getTagEntries, StageEntry::setTagEntries,
                StageEntry::getNbtFreeTags,
                (entry, subject) -> subject instanceof LockSubjects.ItemSubject item
                        && BuiltInLockMatching.tagEntryMatches(entry, item),
                NO_INDEX));

        categories.add(new ModLock());

        // Mod exceptions carve holes in the mods category; an overlap between a global and an
        // individual exception is not a dual-phase lock, so this one opts out.
        categories.add(new Simple<>("mod_exceptions", "exceptions", "",
                StageEntry::getModExceptionEntries, StageEntry::setModExceptionEntries,
                stage -> List.of(), never(), NO_INDEX));

        // Both scopes since 6.0.0. A station with a player standing at it sets a
        // RecipeCraftContext around its one resolution, so an individual stage finally has a gate
        // to write to. Dual-phase came along with it: the same recipe can now be gated globally
        // and individually at once, which is exactly the overlap that check exists to spot.
        categories.add(new Simple<>("recipes", "recipes", "recipe",
                StageEntry::getRecipes, StageEntry::setRecipes,
                StageEntry::getRecipes, ID_EQUALS, StageEntry::getRecipes));

        categories.add(new Simple<>("dimensions", "dimensions", "dimension",
                StageEntry::getDimensions, StageEntry::setDimensions,
                StageEntry::getDimensions, ID_EQUALS, StageEntry::getDimensions));

        categories.add(new AttackLock());
        categories.add(new SpawnLock());
        categories.add(new InteractionLock());

        // Three categories, one editor tab. They answer one question between them — which offers
        // a merchant shows this player — but they are asked separately and hold different things,
        // and a single list of a mixed type would make every reader ask which kind it is holding.
        categories.add(new TradeOfferLock());
        categories.add(new TradeProfessionLock());
        categories.add(new Simple<>("trade_levels", "trades", "trade level",
                StageEntry::getTradeLevels, StageEntry::setTradeLevels,
                StageEntry::getTradeLevels, ID_EQUALS, NO_INDEX));

        categories.add(new Simple<>("structures", "structures", "structure",
                StageEntry::getStructures, StageEntry::setStructures,
                StageEntry::getStructures, ID_EQUALS, StageEntry::getStructures));

        categories.add(new Simple<>("biomes", "biomes", "biome",
                StageEntry::getBiomes, StageEntry::setBiomes,
                StageEntry::getBiomes, ID_EQUALS, StageEntry::getBiomes));

        return categories;
    }

    /** Everything whose entries are a plain list on {@link StageEntry}. */
    private record Simple<T>(String name, String tabName, String dualPhaseLabel,
                             Function<StageEntry, List<T>> reader,
                             BiConsumer<StageEntry, List<T>> writer,
                             Function<StageEntry, List<String>> dualPhase,
                             BiPredicate<T, Object> matcher,
                             Function<StageEntry, List<String>> indexKeys)
            implements LockCategory<T> {

        @Override public String id() { return "historystages:" + name; }
        @Override public String tabLangKey() { return "editor.historystages.tab." + tabName; }
        @Override public String tooltipLangKey() { return "editor.historystages.tooltip." + tabName; }
        @Override public List<T> read(StageEntry stage) { return reader.apply(stage); }
        @Override public void write(StageEntry stage, List<T> entries) { writer.accept(stage, entries); }
        @Override public List<String> globalDualPhaseIds(StageEntry stage) { return dualPhase.apply(stage); }
        @Override public List<String> individualDualPhaseIds(StageEntry stage) { return dualPhase.apply(stage); }
        @Override public boolean matches(T entry, Object subject) { return matcher.test(entry, subject); }
        @Override public List<String> indexKeys(StageEntry stage) { return indexKeys.apply(stage); }
        @Override public String lookupKey(Object subject) { return SUBJECT_IS_THE_KEY.apply(subject); }
    }

    /**
     * Fluid locks. Asked about the same {@link LockSubjects.ItemSubject} as items, but reading a
     * different part of it: what the stack is <em>carrying</em> rather than what it is. That is
     * what lets one entry cover the vanilla bucket, every modded bucket and every tank item
     * without a single item id being listed.
     *
     * <p>A class of its own rather than a {@link Simple}, because it differs from that shape in
     * two ways at once. Its action vocabulary is shorter — a fluid is never worn, swung or mined
     * — and its index is keyed by the fluid the subject carries, where {@code Simple} keys on
     * subjects that <em>are</em> their own id. Filing under {@link #indexKeys} without a matching
     * {@link #lookupKey} would build an index nothing ever reads.
     */
    private static final class FluidLock
            implements LockCategory<net.bananemdnsa.historystages.data.FluidEntry> {

        @Override public String id() { return "historystages:fluids"; }
        @Override public String tabLangKey() { return "editor.historystages.tab.fluids"; }
        @Override public String tooltipLangKey() { return "editor.historystages.tooltip.fluids"; }
        @Override public String dualPhaseLabel() { return "fluid"; }

        @Override public List<String> lockActions() {
            return net.bananemdnsa.historystages.api.lock.LockActions.FLUID;
        }

        @Override
        public List<net.bananemdnsa.historystages.data.FluidEntry> read(StageEntry stage) {
            return stage.getFluidEntries();
        }

        @Override
        public void write(StageEntry stage,
                          List<net.bananemdnsa.historystages.data.FluidEntry> entries) {
            stage.setFluidEntries(entries);
        }

        @Override public List<String> globalDualPhaseIds(StageEntry stage) {
            return stage.getAllFluidIds();
        }

        @Override public List<String> individualDualPhaseIds(StageEntry stage) {
            return stage.getAllFluidIds();
        }

        @Override
        public boolean matches(net.bananemdnsa.historystages.data.FluidEntry entry, Object subject) {
            return subject instanceof LockSubjects.ItemSubject item
                    && BuiltInLockMatching.fluidEntryMatches(entry, item);
        }

        /** Exact: a fluid entry carries no criterion, so a keyed stage always really matches. */
        @Override public List<String> indexKeys(StageEntry stage) {
            return stage.getAllFluidIds();
        }

        @Override public String lookupKey(Object subject) {
            return subject instanceof LockSubjects.ItemSubject item ? item.fluidId() : null;
        }
    }

    /**
     * Gated single offers — one of the three categories behind the "Handel" tab.
     *
     * <p>Names one trade: this merchant, at this level, handing this over for that. What it is
     * <em>not</em> is a way to gate an item wherever it turns up in a trade — that is the item
     * action {@code trade} on an ordinary item entry, and it lived there first. Two tools for one
     * job is how a pack author ends up wondering which of them is in force.
     *
     * <p>No action vocabulary of its own, so it keeps the default and is never asked to narrow.
     * There is no half of a single trade to narrow to: either the offer is on the list or it is
     * not, and a player who may not make it may not make either side of it.
     *
     * <p>No {@link #indexKeys}, deliberately. {@code LockRelevanceIndex} narrows
     * {@code isItemActionLocked}; a trade question never travels that path, it is asked through
     * {@code CategoryLockResolver} directly. Filing keys here would build an index nothing reads.
     */
    private static final class TradeOfferLock
            implements LockCategory<net.bananemdnsa.historystages.data.TradeOfferEntry> {

        @Override public String id() { return "historystages:trades"; }
        @Override public String tabLangKey() { return "editor.historystages.tab.trades"; }
        @Override public String tooltipLangKey() { return "editor.historystages.tooltip.trades"; }
        @Override public String dualPhaseLabel() { return "trade"; }

        @Override
        public List<net.bananemdnsa.historystages.data.TradeOfferEntry> read(StageEntry stage) {
            return stage.getTradeOffers();
        }

        @Override
        public void write(StageEntry stage,
                          List<net.bananemdnsa.historystages.data.TradeOfferEntry> entries) {
            stage.setTradeOffers(entries);
        }

        @Override public List<String> globalDualPhaseIds(StageEntry stage) {
            return stage.getAllTradeItemIds();
        }

        @Override public List<String> individualDualPhaseIds(StageEntry stage) {
            return stage.getAllTradeItemIds();
        }

        /**
         * Asked about a {@link net.bananemdnsa.historystages.data.lock.TradeOfferSubject} rather
         * than the {@code ItemSubject} every other item-shaped category uses. That record names
         * Minecraft types and cannot be built by a unit test, which would have put the whole
         * decision table out of reach of anything but a running game.
         *
         * <p>The criterion is the one part that still needs a live stack, and the call into
         * {@link BuiltInLockMatching} for it is only reached when an entry carries one — a method
         * call across the class boundary is resolved lazily, so a test that uses no criterion
         * never loads Minecraft through this line.
         */
        @Override
        public boolean matches(net.bananemdnsa.historystages.data.TradeOfferEntry entry,
                               Object subject) {
            if (!(subject instanceof net.bananemdnsa.historystages.data.lock.TradeOfferSubject offer)) {
                return false;
            }
            if (!entry.gates(offer.merchantKey(), offer.level(), offer.givesId(),
                    offer.takesAId(), offer.takesBId())) {
                return false;
            }
            if (!entry.hasNbt()) return true;
            return BuiltInLockMatching.tradeCriterionMatches(entry.nbt(), offer.givesStack());
        }

        @Override public List<String> indexKeys(StageEntry stage) { return List.of(); }
        @Override public String lookupKey(Object subject) { return null; }
    }

    /**
     * Gated professions — the second of the three categories behind the "Handel" tab.
     *
     * <p>A {@link Simple} until an entry could narrow itself to some of the merchant's levels.
     * That narrowing is what makes it its own class: {@code Simple} compares the subject to an
     * id, and the question here is about a profession <em>and</em> a level at once. Splitting it
     * into two questions would push the joining onto every caller, and the two would drift.
     *
     * <p>No index. Professions are few and a merchant is asked about once per window opened, so
     * the reverse index would cost memory to save nothing measurable.
     */
    private static final class TradeProfessionLock
            implements LockCategory<net.bananemdnsa.historystages.data.TradeProfessionEntry> {

        @Override public String id() { return "historystages:trade_professions"; }
        @Override public String tabLangKey() { return "editor.historystages.tab.trades"; }
        @Override public String tooltipLangKey() { return "editor.historystages.tooltip.trades"; }
        @Override public String dualPhaseLabel() { return "trade profession"; }

        @Override
        public List<net.bananemdnsa.historystages.data.TradeProfessionEntry> read(StageEntry stage) {
            return stage.getTradeProfessionEntries();
        }

        @Override
        public void write(StageEntry stage,
                          List<net.bananemdnsa.historystages.data.TradeProfessionEntry> entries) {
            stage.setTradeProfessionEntries(entries);
        }

        @Override public List<String> globalDualPhaseIds(StageEntry stage) {
            return stage.getTradeProfessions();
        }

        @Override public List<String> individualDualPhaseIds(StageEntry stage) {
            return stage.getTradeProfessions();
        }

        /**
         * Asked about a {@link net.bananemdnsa.historystages.data.lock.MerchantSubject}: the
         * profession decides whether this entry is about this merchant at all, the level decides
         * whether it is about this merchant <em>now</em>.
         */
        @Override
        public boolean matches(net.bananemdnsa.historystages.data.TradeProfessionEntry entry,
                               Object subject) {
            return subject instanceof net.bananemdnsa.historystages.data.lock.MerchantSubject merchant
                    && entry.gates(merchant.professionId(), merchant.level());
        }

        @Override public List<String> indexKeys(StageEntry stage) { return List.of(); }
        @Override public String lookupKey(Object subject) { return null; }
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

        /**
         * Both lists, because {@link #gates} reads both. Filing this stage under its attack locks
         * alone would hide it from every entity gated only by a source-less spawn lock, and that
         * entity would become attackable — the exact shape of failure the contract on
         * {@link LockCategory#indexKeys} warns about.
         */
        @Override public List<String> indexKeys(StageEntry stage) {
            List<String> keys = new ArrayList<>(stage.getEntities().getAttacklock());
            for (EntitySpawnLockEntry spawn : stage.getEntities().getSpawnlock()) {
                keys.add(spawn.getId());
            }
            return keys;
        }

        @Override public String lookupKey(Object subject) {
            return subject instanceof String entityId ? entityId : null;
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

        /**
         * The entity ids only. Source and dimension filters narrow further, but they are settled
         * by the exact check afterwards — the index is allowed to name a stage that turns out not
         * to match, never to omit one that does.
         */
        @Override public List<String> indexKeys(StageEntry stage) {
            List<String> keys = new ArrayList<>();
            for (EntitySpawnLockEntry entry : stage.getEntities().getSpawnlock()) {
                keys.add(entry.getId());
            }
            return keys;
        }

        @Override public String lookupKey(Object subject) {
            return subject instanceof LockSubjects.SpawnSubject spawn ? spawn.entityId() : null;
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

        /** The action filter and the held-item filter are settled by the exact check afterwards. */
        @Override public List<String> indexKeys(StageEntry stage) {
            return ids(stage);
        }

        @Override public String lookupKey(Object subject) {
            return subject instanceof LockSubjects.InteractionSubject interaction
                    ? interaction.entityId() : null;
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
