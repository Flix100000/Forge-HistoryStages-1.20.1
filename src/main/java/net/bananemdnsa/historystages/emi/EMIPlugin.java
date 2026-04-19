package net.bananemdnsa.historystages.emi;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

public class EMIPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        // Locked recipe overlay (always active, works for all recipe categories)
        registry.addRecipeDecorator(new LockedEmiRecipeDecorator());
    }
}