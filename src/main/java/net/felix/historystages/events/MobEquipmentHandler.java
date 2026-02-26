package net.felix.historystages.events;

import net.felix.historystages.Config;
import net.felix.historystages.HistoryStages;
import net.felix.historystages.data.StageManager;
import net.felix.historystages.util.StageData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MobEquipmentHandler {

    @SubscribeEvent
    public static void onMobFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        // KORREKTUR: Zugriff über Config.COMMON und .get()
        if (!Config.COMMON.lockMobEquipment.get()) return;

        LivingEntity entity = event.getEntity();

        // Sicherstellen, dass der Cache für den Server bereit ist
        if (StageData.SERVER_CACHE.isEmpty()) {
            StageData.get(event.getLevel().getLevel());
        }

        // Wir prüfen alle 6 Equipment-Slots (Hände + Rüstung)
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            ResourceLocation resLoc = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (resLoc == null) continue;

            String requiredStage = StageManager.getStageForItemOrMod(resLoc.toString(), resLoc.getNamespace());

            // Wenn das Item eine Stage braucht, die noch nicht frei ist: Weg damit!
            if (requiredStage != null && !StageData.SERVER_CACHE.contains(requiredStage)) {
                entity.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }
}