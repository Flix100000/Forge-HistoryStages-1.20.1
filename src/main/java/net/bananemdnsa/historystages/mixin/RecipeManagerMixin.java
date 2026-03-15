package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.events.RecipeHandler;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {
    @Shadow private Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes;
    @Shadow private Map<ResourceLocation, Recipe<?>> byName;

    @Inject(
            method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("TAIL"),
            remap = true // WICHTIG: Erlaubt Forge das Finden der Methode in der JAR
    )
    private void onApplyPost(Map<ResourceLocation, com.google.gson.JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler, CallbackInfo ci) {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            StageData data = StageData.get(server.overworld());
            StageData.SERVER_CACHE.clear();
            StageData.SERVER_CACHE.addAll(data.getUnlockedStages());
        }

        // Registry "reinigen" - pro RecipeType absichern, damit ein fehlerhaftes
        // Rezept nicht alle anderen RecipeTypes (z.B. sewingkit:sewing) mitzieht
        Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> newRecipes = new HashMap<>();
        this.recipes.forEach((type, map) -> {
            try {
                Map<ResourceLocation, Recipe<?>> filtered = new HashMap<>(map);
                filtered.entrySet().removeIf(e -> RecipeHandler.isOutputLocked(e.getValue()) || RecipeHandler.isRecipeIdLocked(e.getKey()));
                newRecipes.put(type, filtered);
            } catch (Exception e) {
                // Bei Fehler den RecipeType unverändert übernehmen statt zu verlieren
                System.err.println("[HistoryStages] Fehler beim Filtern von RecipeType " + type + ": " + e.getMessage());
                newRecipes.put(type, map);
            }
        });
        this.recipes = newRecipes;

        Map<ResourceLocation, Recipe<?>> newByName = new HashMap<>(this.byName);
        newByName.entrySet().removeIf(e -> {
            try {
                boolean outputLocked = RecipeHandler.isOutputLocked(e.getValue());
                boolean idLocked = RecipeHandler.isRecipeIdLocked(e.getKey());
                if (idLocked) {
                    System.out.println("[HistoryStages] Recipe locked by ID: " + e.getKey());
                }
                return outputLocked || idLocked;
            } catch (Exception ex) {
                System.err.println("[HistoryStages] Fehler beim Filtern von Rezept " + e.getKey() + ": " + ex.getMessage());
                return false; // Im Zweifel Rezept behalten
            }
        });
        this.byName = newByName;

        System.out.println("[HistoryStages] Registry gesäubert.");
    }
}