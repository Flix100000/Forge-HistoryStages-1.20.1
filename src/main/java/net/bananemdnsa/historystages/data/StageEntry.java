package net.bananemdnsa.historystages.data;

import com.google.gson.GsonBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StageEntry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("display_name")
    private String displayName;

    @SerializedName("research_time")
    private int researchTime; // 0 = use global config default

    @JsonAdapter(ItemEntryListAdapter.class)
    private List<ItemEntry> items;
    private List<String> tags;
    private List<String> mods;
    private List<String> recipes;
    private List<String> dimensions;
    private EntityLocks entities;

    public StageEntry() {
        this.items = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.mods = new ArrayList<>();
        this.recipes = new ArrayList<>();
        this.dimensions = new ArrayList<>();
        this.entities = new EntityLocks();
    }

    public String getDisplayName() {
        return displayName != null ? displayName : "Unknown Stage";
    }

    public int getResearchTime() {
        return researchTime; // 0 means "use global default from config"
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
    public List<String> getRecipes() { return recipes != null ? recipes : new ArrayList<>(); }

    public List<String> getDimensions() {
        return dimensions != null ? dimensions : new ArrayList<>();
    }

    public EntityLocks getEntities() {
        return entities != null ? entities : new EntityLocks();
    }

    // --- Setters ---

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setResearchTime(int researchTime) {
        this.researchTime = researchTime;
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

    public void setRecipes(List<String> recipes) {
        this.recipes = recipes != null ? new ArrayList<>(recipes) : new ArrayList<>();
    }

    public void setDimensions(List<String> dimensions) {
        this.dimensions = dimensions != null ? new ArrayList<>(dimensions) : new ArrayList<>();
    }

    public void setEntities(EntityLocks entities) {
        this.entities = entities != null ? entities : new EntityLocks();
    }

    public StageEntry copy() {
        StageEntry copy = new StageEntry();
        copy.setDisplayName(getDisplayName());
        copy.setResearchTime(researchTime);
        copy.setItemEntries(getItemEntries().stream().map(ItemEntry::copy).collect(Collectors.toList()));
        copy.setTags(getTags());
        copy.setMods(getMods());
        copy.setRecipes(getRecipes());
        copy.setDimensions(getDimensions());
        EntityLocks locksCopy = new EntityLocks();
        locksCopy.setAttacklock(getEntities().getAttacklock());
        locksCopy.setSpawnlock(getEntities().getSpawnlock());
        locksCopy.setModLinked(getEntities().getModLinked());
        copy.setEntities(locksCopy);
        return copy;
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
