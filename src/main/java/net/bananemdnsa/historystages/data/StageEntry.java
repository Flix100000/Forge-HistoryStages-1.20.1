package net.bananemdnsa.historystages.data;

import com.google.gson.GsonBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class StageEntry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("display_name")
    private String displayName;

    @SerializedName("research_time")
    private int researchTime; // 0 = use global config default

    @SerializedName("icon")
    private String icon; // item id, null = use default (research scroll)

    @JsonAdapter(ItemEntryListAdapter.class)
    private List<ItemEntry> items;
    private List<String> tags;
    private List<String> mods;

    @SerializedName("mod_exceptions")
    @JsonAdapter(ItemEntryListAdapter.class)
    private List<ItemEntry> modExceptions;

    private List<String> recipes;
    private List<String> dimensions;
    private List<String> structures;
    private EntityLocks entities;
    private List<DependencyGroup> dependencies;

    public StageEntry() {
        this.items = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.mods = new ArrayList<>();
        this.modExceptions = new ArrayList<>();
        this.recipes = new ArrayList<>();
        this.dimensions = new ArrayList<>();
        this.structures = new ArrayList<>();
        this.entities = new EntityLocks();
    }

    public String getDisplayName() {
        return displayName != null ? displayName : "Unknown Stage";
    }

    public int getResearchTime() {
        return researchTime; // 0 means "use global default from config"
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

    public List<String> getTags() { return tags != null ? tags : new ArrayList<>(); }
    public List<String> getMods() { return mods != null ? mods : new ArrayList<>(); }

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
        return structures != null ? structures : new ArrayList<>();
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

    public void setTags(List<String> tags) {
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }

    public void setMods(List<String> mods) {
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
        this.structures = structures != null ? new ArrayList<>(structures) : new ArrayList<>();
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
        copy.setIcon(icon);
        copy.setItemEntries(getItemEntries().stream().map(ItemEntry::copy).collect(Collectors.toList()));
        copy.setTags(getTags());
        copy.setMods(getMods());
        copy.setModExceptionEntries(getModExceptionEntries().stream().map(ItemEntry::copy).collect(Collectors.toList()));
        copy.setRecipes(getRecipes());
        copy.setDimensions(getDimensions());
        copy.setStructures(getStructures());
        EntityLocks locksCopy = new EntityLocks();
        locksCopy.setAttacklock(getEntities().getAttacklock());
        locksCopy.setSpawnlock(getEntities().getSpawnlock());
        locksCopy.setModLinked(getEntities().getModLinked());
        copy.setEntities(locksCopy);
        copy.setDependencies(getDependencies().stream().map(DependencyGroup::copy).collect(Collectors.toList()));
        return copy;
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
