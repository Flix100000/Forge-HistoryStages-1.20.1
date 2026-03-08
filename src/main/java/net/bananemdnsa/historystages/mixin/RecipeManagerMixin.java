package net.bananemdnsa.historystages.mixin;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
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

import java.util.Map;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {
    @Shadow private Multimap<RecipeType<?>, RecipeHolder<?>> byType;
    @Shadow private Map<ResourceLocation, RecipeHolder<?>> byName;

    @Inject(
            method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("TAIL"),
            remap = true
    )
    private void onApplyPost(Map<ResourceLocation, com.google.gson.JsonElement> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler, CallbackInfo ci) {
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            StageData data = StageData.get(server.overworld());
            StageData.SERVER_CACHE.clear();
            StageData.SERVER_CACHE.addAll(data.getUnlockedStages());
        }

        // Registry "reinigen" - Immutable Maps neu aufbauen, da 1.21 ImmutableMultimap/ImmutableMap verwendet
        ImmutableMultimap.Builder<RecipeType<?>, RecipeHolder<?>> typeBuilder = ImmutableMultimap.builder();
        this.byType.forEach((type, holder) -> {
            if (!RecipeHandler.isOutputLocked(holder)) {
                typeBuilder.put(type, holder);
            }
        });
        this.byType = typeBuilder.build();

        ImmutableMap.Builder<ResourceLocation, RecipeHolder<?>> nameBuilder = ImmutableMap.builder();
        this.byName.forEach((loc, holder) -> {
            if (!RecipeHandler.isOutputLocked(holder)) {
                nameBuilder.put(loc, holder);
            }
        });
        this.byName = nameBuilder.build();

        System.out.println("[HistoryStages] Registry gesäubert.");
    }
}
