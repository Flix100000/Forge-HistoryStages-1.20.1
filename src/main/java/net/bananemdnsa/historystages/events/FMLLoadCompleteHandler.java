package net.bananemdnsa.historystages.events;

import net.bananemdnsa.historystages.HistoryStages;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;

@Mod.EventBusSubscriber(modid = HistoryStages.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FMLLoadCompleteHandler {

    // This is where we can build our mapping of stage data from the configs
    // That way all data is loaded before the player even joins a world
    private void onLoadComplete(FMLLoadCompleteEvent event) {
        // Perform tasks requiring fully loaded registries
    }
}
