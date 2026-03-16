package net.bananemdnsa.historystages.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;

public class StageEntry {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("display_name")
    private String displayName;

    @SerializedName("research_time")
    private int researchTime; // 0 = use global config default

    private List<String> items;
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

    public List<String> getItems() { return items != null ? items : new ArrayList<>(); }
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

    public void setItems(List<String> items) {
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
        copy.setItems(getItems());
        copy.setTags(getTags());
        copy.setMods(getMods());
        copy.setRecipes(getRecipes());
        copy.setDimensions(getDimensions());
        EntityLocks locksCopy = new EntityLocks();
        locksCopy.setAttacklock(getEntities().getAttacklock());
        locksCopy.setSpawnlock(getEntities().getSpawnlock());
        copy.setEntities(locksCopy);
        return copy;
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
