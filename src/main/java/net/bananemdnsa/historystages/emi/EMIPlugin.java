package net.bananemdnsa.historystages.emi;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.util.ClientStageCache;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public class EMIPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        // Register the locked recipe decorator globally for all categories
        registry.addRecipeDecorator(new LockedEmiRecipeDecorator());

        // Item hiding (optional, controlled by config)
        if (!Config.CLIENT.hideInJei.get()) return;

        for (Map.Entry<String, StageEntry> entry : StageManager.getStages().entrySet()) {
            String stageId = entry.getKey();
            StageEntry stageData = entry.getValue();

            if (!ClientStageCache.isStageUnlocked(stageId)) {
                // 1. Items verstecken
                for (String itemId : stageData.getItems()) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
                    if (item != null) registry.removeEmiStacks(EmiStack.of(item));
                }

                // 2. Mods verstecken
                for (String modId : stageData.getMods()) {
                    for (Item item : BuiltInRegistries.ITEM) {
                        ResourceLocation res = BuiltInRegistries.ITEM.getKey(item);
                        if (res != null && res.getNamespace().equals(modId)) {
                            registry.removeEmiStacks(EmiStack.of(item));
                        }
                    }
                }

                // 3. Tags verstecken
                for (String tagId : stageData.getTags()) {
                    ResourceLocation tagRes = ResourceLocation.parse(tagId);
                    for (Item item : BuiltInRegistries.ITEM) {
                        ItemStack stack = new ItemStack(item);
                        if (stack.getItem().builtInRegistryHolder().tags().anyMatch(t -> t.location().equals(tagRes))) {
                            registry.removeEmiStacks(EmiStack.of(item));
                        }
                    }
                }
            }
        }
    }
}
