package net.bananemdnsa.historystages.emi;

import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiStack;
import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.util.StageLockHelper;
import net.minecraft.world.item.ItemStack;

public class EMIPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.removeEmiStacks(stack -> {
            if (!Config.CLIENT.hideInJei) {
                return false;
            }

            ItemStack itemStack = stack.getItemStack();
            return !itemStack.isEmpty() && StageLockHelper.isItemLockedForClient(itemStack);
        });
    }
}
