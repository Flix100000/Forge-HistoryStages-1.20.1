package net.bananemdnsa.historystages.emi;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;

public class EMIPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        // Register the locked recipe decorator globally for all categories
        registry.addRecipeDecorator(new LockedEmiRecipeDecorator());
    }
}
