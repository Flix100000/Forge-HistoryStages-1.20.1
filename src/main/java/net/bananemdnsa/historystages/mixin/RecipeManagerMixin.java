package net.bananemdnsa.historystages.mixin;

import net.bananemdnsa.historystages.events.RecipeHandler;
import net.bananemdnsa.historystages.util.StageData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeHolder;
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
    @Shadow private Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes;
    @Shadow private Map<ResourceLocation, RecipeHolder<?>> byName;

    @Inject(
            method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("TAIL"),
            remap = true // WICHTIG: Erlaubt NeoForge das Finden der Methode in der JAR
    )
    private void onApplyPost(Map<ResourceLocation, com.google.gson.JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler, CallbackInfo ci) {
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            StageData data = StageData.get(server.overworld());
            StageData.SERVER_CACHE.clear();
            StageData.SERVER_CACHE.addAll(data.getUnlockedStages());
        }

        // Registry "reinigen"
        Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> newRecipes = new HashMap<>();
        this.recipes.forEach((type, map) -> {
            Map<ResourceLocation, RecipeHolder<?>> filtered = new HashMap<>(map);
            filtered.entrySet().removeIf(e -> RecipeHandler.isOutputLocked(e.getValue()));
            newRecipes.put(type, filtered);
        });
        this.recipes = newRecipes;

        Map<ResourceLocation, RecipeHolder<?>> newByName = new HashMap<>(this.byName);
        newByName.entrySet().removeIf(e -> RecipeHandler.isOutputLocked(e.getValue()));
        this.byName = newByName;

        System.out.println("[HistoryStages] Registry gesäubert.");
    }
}
