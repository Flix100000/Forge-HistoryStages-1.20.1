package net.bananemdnsa.historystages.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;
import net.astr0.historystages.api.StageScope;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StageDefinition {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final StageScope _scope;


    @SerializedName("display_name")
    private String stageName;

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



    public StageDefinition(String stage, StageScope scope) {
        stageName = stage;
        _scope = scope;

        this.items = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.mods = new ArrayList<>();
        this.modExceptions = new ArrayList<>();
        this.recipes = new ArrayList<>();
        this.dimensions = new ArrayList<>();
        this.structures = new ArrayList<>();
        this.entities = new EntityLocks();
    }

    public String getName() {
        return stageName;
    }

    public List<Item> getLockedItems() {
        return new ArrayList<>();
    }

    public List<ResourceLocation> getLockedMods() {
        return new ArrayList<>();
    }

    public List<ResourceLocation> getLockedStructures() {
        return new ArrayList<>();
    }

    public StageScope getStageScope() {
        return _scope;
    }

    public List<TagKey<Item>> getLockedItemTags() {
        List<TagKey<Item>> lockedItemTags = new ArrayList<>();
        return lockedItemTags;
    }

    /** Returns the custom icon item id, or null if not set (use default). */
    public String getIcon() {
        return (icon != null && !icon.isEmpty()) ? icon : null;
    }

    public int getResearchTime() {
        return researchTime; // 0 means "use global default from config"
    }

    /** Returns ALL item IDs (with and without NBT) — for display/counting only. */
    public List<String> getAllItemIds() {
        if (items == null) return new ArrayList<>();
        return items.stream().map(ItemEntry::getId).collect(Collectors.toCollection(ArrayList::new));
    }

    @Nonnull
    public List<String> getTags() { return tags != null ? tags : new ArrayList<>(); }

    @Nonnull
    public List<String> getMods() { return mods != null ? mods : new ArrayList<>(); }

    @Nonnull
    public List<String> getRecipes() { return recipes != null ? recipes : new ArrayList<>(); }

    @Nonnull
    public List<String> getDimensions() {
        return dimensions != null ? dimensions : new ArrayList<>();
    }

    @Nonnull
    public List<String> getStructures() {
        return structures != null ? structures : new ArrayList<>();
    }

    @Nonnull
    public EntityLocks getEntities() {
        return entities != null ? entities : new EntityLocks();
    }

    public String toJson() {
        return GSON.toJson(this);
    }
}
