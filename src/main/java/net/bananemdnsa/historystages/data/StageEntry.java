package net.bananemdnsa.historystages.data;

import com.google.gson.GsonBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import net.bananemdnsa.historystages.data.auto.AutoTrigger;
import net.bananemdnsa.historystages.data.temporary.TemporaryConfig;
import net.bananemdnsa.historystages.research.TierMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class StageEntry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("display_name")
    private String displayName;

    @SerializedName("mode")
    private String mode;   // "default" | "auto" | "external"; null → default

    @SerializedName("auto_trigger")
    private AutoTrigger autoTrigger;

    @SerializedName("temporary")
    private TemporaryConfig temporary; // only used when mode == "temporary"

    @SerializedName("research_time")
    private int researchTime; // 0 = use global config default

    @SerializedName("min_pedestal_tier")
    private int minPedestalTier; // 0/missing → treated as 1 (works on every tier)

    @SerializedName("pedestal_tier_mode")
    private String pedestalTierMode; // "min" or "exact"; null → "min"

    @SerializedName("icon")
    private String icon; // item id, null = use default (research scroll)

    @JsonAdapter(ItemEntryListAdapter.class)
    private List<ItemEntry> items;

    @JsonAdapter(NamedLockEntryListAdapter.class)
    private List<NamedLockEntry> tags;

    @JsonAdapter(NamedLockEntryListAdapter.class)
    private List<NamedLockEntry> mods;

    @SerializedName("mod_exceptions")
    @JsonAdapter(ItemEntryListAdapter.class)
    private List<ItemEntry> modExceptions;

    private List<String> recipes;
    private List<String> dimensions;

    @JsonAdapter(StructureLocksAdapter.class)
    private StructureLocks structures;

    private EntityLocks entities;
    private List<DependencyGroup> dependencies;

    public StageEntry() {
        this.items = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.mods = new ArrayList<>();
        // tags/mods are List<NamedLockEntry>, initialized as empty lists above
        this.modExceptions = new ArrayList<>();
        this.recipes = new ArrayList<>();
        this.dimensions = new ArrayList<>();
        this.structures = new StructureLocks();
        this.entities = new EntityLocks();
    }

    public String getDisplayName() {
        return displayName != null ? displayName : "Unknown Stage";
    }

    public int getResearchTime() {
        return researchTime; // 0 means "use global default from config"
    }

    public int getMinPedestalTier() {
        int t = minPedestalTier;
        if (t < 1) return 1;
        if (t > 4) return 4;
        return t;
    }

    public TierMode getPedestalTierMode() {
        return TierMode.parse(pedestalTierMode, TierMode.MIN);
    }

    /** Returns the stage's mode, defaulting to DEFAULT if unset or unknown. */
    public StageMode getMode() {
        return StageMode.parse(mode);
    }

    /** Raw mode string from JSON — used by StageManager to log warnings on unknown values. */
    public String getRawMode() { return mode; }

    public void setMode(StageMode m) {
        this.mode = (m != null ? m : StageMode.DEFAULT).serialize();
    }

    /** Returns the auto-trigger config, or null if none is set. */
    public AutoTrigger getAutoTrigger() {
        return autoTrigger;
    }

    public void setAutoTrigger(AutoTrigger autoTrigger) {
        this.autoTrigger = autoTrigger;
    }

    /** Returns the temporary-mode config, or null if none is set. */
    public TemporaryConfig getTemporary() {
        return temporary;
    }

    public void setTemporary(TemporaryConfig temporary) {
        this.temporary = temporary;
    }

    /** Returns the custom icon item id, or null if not set (use default). */
    public String getIcon() {
        return (icon != null && !icon.isEmpty()) ? icon : null;
    }

    /** Returns item IDs of entries WITHOUT NBT criteria (simple ID-only locks). */
    public List<String> getItems() {
        if (items == null) return new ArrayList<>();
        return items.stream()
                .filter(e -> !e.hasNbt())
                .map(ItemEntry::getId)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Returns ALL item IDs (with and without NBT) — for display/counting only. */
    public List<String> getAllItemIds() {
        if (items == null) return new ArrayList<>();
        return items.stream().map(ItemEntry::getId).collect(Collectors.toCollection(ArrayList::new));
    }

    /** Returns the full item entries with NBT data. */
    public List<ItemEntry> getItemEntries() {
        return items != null ? items : new ArrayList<>();
    }

    /** Returns tag IDs only (no lock_actions). For backwards-compatible iteration. */
    public List<String> getTags() {
        if (tags == null) return new ArrayList<>();
        return tags.stream().map(NamedLockEntry::getId).collect(Collectors.toCollection(ArrayList::new));
    }

    /** Returns the full tag entries including lock_actions. */
    public List<NamedLockEntry> getTagEntries() {
        return tags != null ? tags : new ArrayList<>();
    }

    /** Returns mod IDs only (no lock_actions). For backwards-compatible iteration. */
    public List<String> getMods() {
        if (mods == null) return new ArrayList<>();
        return mods.stream().map(NamedLockEntry::getId).collect(Collectors.toCollection(ArrayList::new));
    }

    /** Returns the full mod entries including lock_actions. */
    public List<NamedLockEntry> getModEntries() {
        return mods != null ? mods : new ArrayList<>();
    }

    /** Returns item IDs of mod exception entries WITHOUT NBT criteria. */
    public List<String> getModExceptions() {
        if (modExceptions == null) return new ArrayList<>();
        return modExceptions.stream()
                .filter(e -> !e.hasNbt())
                .map(ItemEntry::getId)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Returns ALL mod exception item IDs (with and without NBT) — for display/counting only. */
    public List<String> getAllModExceptionIds() {
        if (modExceptions == null) return new ArrayList<>();
        return modExceptions.stream().map(ItemEntry::getId).collect(Collectors.toCollection(ArrayList::new));
    }

    /** Returns the full mod exception entries with NBT data. */
    public List<ItemEntry> getModExceptionEntries() {
        return modExceptions != null ? modExceptions : new ArrayList<>();
    }

    /**
     * Checks if a specific item is excepted from mod locking in this stage.
     * Returns true if the item should NOT be locked even though its mod is in the mods list.
     */
    public boolean isModExcepted(String itemId, net.minecraft.world.item.ItemStack stack) {
        if (modExceptions == null || modExceptions.isEmpty()) return false;
        for (ItemEntry exEntry : modExceptions) {
            if (exEntry.getId().equals(itemId)) {
                if (exEntry.hasNbt()) {
                    if (stack != null && NbtMatcher.matches(stack, exEntry.getNbt())) {
                        return true;
                    }
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    public List<String> getRecipes() { return recipes != null ? recipes : new ArrayList<>(); }

    public List<String> getDimensions() {
        return dimensions != null ? dimensions : new ArrayList<>();
    }

    public List<String> getStructures() {
        return structures != null ? structures.getStructures() : new ArrayList<>();
    }

    public List<String> getStructureModLinked() {
        return structures != null ? structures.getModLinked() : new ArrayList<>();
    }

    public EntityLocks getEntities() {
        return entities != null ? entities : new EntityLocks();
    }

    public List<DependencyGroup> getDependencies() {
        return dependencies != null ? dependencies : new ArrayList<>();
    }

    public boolean hasDependencies() {
        if (dependencies == null || dependencies.isEmpty()) return false;
        return dependencies.stream().anyMatch(g -> !g.isEmpty());
    }

    // --- Setters ---

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setResearchTime(int researchTime) {
        this.researchTime = researchTime;
    }

    public void setMinPedestalTier(int tier) {
        if (tier < 1) tier = 1;
        if (tier > 4) tier = 4;
        this.minPedestalTier = tier;
    }

    public void setPedestalTierMode(TierMode mode) {
        this.pedestalTierMode = (mode != null ? mode : TierMode.MIN).serialize();
    }

    public void setIcon(String icon) {
        this.icon = (icon != null && !icon.isEmpty()) ? icon : null;
    }

    /** Sets items from simple string IDs (no NBT). */
    public void setItems(List<String> items) {
        if (items == null) {
            this.items = new ArrayList<>();
        } else {
            this.items = items.stream()
                    .map(ItemEntry::new)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    /** Sets items from full ItemEntry list (with NBT support). */
    public void setItemEntries(List<ItemEntry> items) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }

    /** Sets tags from plain IDs (no lock_actions — all actions locked). */
    public void setTags(List<String> tags) {
        if (tags == null) {
            this.tags = new ArrayList<>();
        } else {
            this.tags = tags.stream()
                    .map(NamedLockEntry::new)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    /** Sets tags from full NamedLockEntry list (preserves lock_actions). */
    public void setTagEntries(List<NamedLockEntry> tags) {
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    /** Sets mods from plain IDs (no lock_actions — all actions locked). */
    public void setMods(List<String> mods) {
        if (mods == null) {
            this.mods = new ArrayList<>();
        } else {
            this.mods = mods.stream()
                    .map(NamedLockEntry::new)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    /** Sets mods from full NamedLockEntry list (preserves lock_actions). */
    public void setModEntries(List<NamedLockEntry> mods) {
        this.mods = mods != null ? new ArrayList<>(mods) : new ArrayList<>();
    }

    /** Sets mod exceptions from simple string IDs (no NBT). */
    public void setModExceptions(List<String> modExceptions) {
        if (modExceptions == null) {
            this.modExceptions = new ArrayList<>();
        } else {
            this.modExceptions = modExceptions.stream()
                    .map(ItemEntry::new)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    /** Sets mod exceptions from full ItemEntry list (with NBT support). */
    public void setModExceptionEntries(List<ItemEntry> modExceptions) {
        this.modExceptions = modExceptions != null ? new ArrayList<>(modExceptions) : new ArrayList<>();
    }

    public void setRecipes(List<String> recipes) {
        this.recipes = recipes != null ? new ArrayList<>(recipes) : new ArrayList<>();
    }

    public void setDimensions(List<String> dimensions) {
        this.dimensions = dimensions != null ? new ArrayList<>(dimensions) : new ArrayList<>();
    }

    public void setStructures(List<String> structures) {
        if (this.structures == null) this.structures = new StructureLocks();
        this.structures.setStructures(structures);
    }

    public void setStructureModLinked(List<String> modLinked) {
        if (this.structures == null) this.structures = new StructureLocks();
        this.structures.setModLinked(modLinked);
    }

    public void setEntities(EntityLocks entities) {
        this.entities = entities != null ? entities : new EntityLocks();
    }

    public void setDependencies(List<DependencyGroup> dependencies) {
        this.dependencies = dependencies != null ? new ArrayList<>(dependencies) : new ArrayList<>();
    }

    public StageEntry copy() {
        StageEntry copy = new StageEntry();
        copy.setDisplayName(getDisplayName());
        copy.setResearchTime(researchTime);
        copy.setMinPedestalTier(getMinPedestalTier());
        copy.setPedestalTierMode(getPedestalTierMode());
        copy.setIcon(icon);
        copy.setItemEntries(getItemEntries().stream().map(ItemEntry::copy).collect(Collectors.toList()));
        copy.setTagEntries(getTagEntries().stream().map(NamedLockEntry::copy).collect(Collectors.toList()));
        copy.setModEntries(getModEntries().stream().map(NamedLockEntry::copy).collect(Collectors.toList()));
        copy.setModExceptionEntries(getModExceptionEntries().stream().map(ItemEntry::copy).collect(Collectors.toList()));
        copy.setRecipes(getRecipes());
        copy.setDimensions(getDimensions());
        copy.setStructures(getStructures());
        copy.setStructureModLinked(getStructureModLinked());
        EntityLocks locksCopy = new EntityLocks();
        locksCopy.setAttacklock(getEntities().getAttacklock());
        locksCopy.setSpawnlock(getEntities().getSpawnlock());
        locksCopy.setModLinked(getEntities().getModLinked());
        copy.setEntities(locksCopy);
        copy.setDependencies(getDependencies().stream().map(DependencyGroup::copy).collect(Collectors.toList()));
        copy.mode = this.mode;
        copy.autoTrigger = (this.autoTrigger != null) ? this.autoTrigger.copy() : null;
        copy.temporary = (this.temporary != null) ? this.temporary.copy() : null;
        return copy;
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
