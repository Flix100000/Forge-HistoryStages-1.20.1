package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyItem;
import net.bananemdnsa.historystages.data.dependency.XpLevelDep;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class DepositDependencyPacket {
    private final BlockPos pos;
    private final int groupIndex;
    private final String type; // "ITEM" or "XP"
    private final String data; // itemId or empty

    public DepositDependencyPacket(BlockPos pos, int groupIndex, String type, String data) {
        this.pos = pos;
        this.groupIndex = groupIndex;
        this.type = type;
        this.data = data;
    }

    public DepositDependencyPacket(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.groupIndex = buf.readInt();
        this.type = buf.readUtf();
        this.data = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeInt(groupIndex);
        buf.writeUtf(type);
        buf.writeUtf(data);
    }

    public void handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null)
                return;

            BlockEntity be = player.level().getBlockEntity(pos);
            if (!(be instanceof ResearchPedestalBlockEntity pedestal))
                return;

            ItemStack scroll = pedestal.getScrollStack();
            if (scroll.isEmpty() || !scroll.hasTag() || !scroll.getTag().contains("StageResearch"))
                return;

            String stageId = scroll.getTag().getString("StageResearch");
            StageEntry stageEntry = StageManager.isIndividualStage(stageId)
                    ? StageManager.getIndividualStages().get(stageId)
                    : StageManager.getStages().get(stageId);

            if (stageEntry == null || stageEntry.getDependencies() == null || groupIndex < 0
                    || groupIndex >= stageEntry.getDependencies().size())
                return;

            DependencyGroup group = stageEntry.getDependencies().get(groupIndex);
            CompoundTag deposited = scroll.getOrCreateTagElement("DepositedDependencies");

            if ("ITEM".equals(type)) {
                ResourceLocation rl = ResourceLocation.tryParse(data);
                if (rl == null)
                    return;

                // Find required count by comparing ResourceLocations
                int required = 0;
                for (DependencyItem item : group.getItems()) {
                    ResourceLocation requiredRl = ResourceLocation.tryParse(item.getId());
                    if (requiredRl != null && requiredRl.equals(rl)) {
                        required = item.getCount();
                        break;
                    }
                }
                if (required == 0)
                    return;

                String key = "Group_" + groupIndex + "_Item_" + rl.toString();
                int current = deposited.getInt(key);
                int needed = required - current;
                if (needed <= 0)
                    return;

                // Consume from player inventory
                int consumed = 0;
                for (int i = 0; i < player.getInventory().getContainerSize() && consumed < needed; i++) {
                    ItemStack invStack = player.getInventory().getItem(i);
                    if (!invStack.isEmpty()) {
                        ResourceLocation invRl = ForgeRegistries.ITEMS.getKey(invStack.getItem());
                        if (rl.equals(invRl)) {
                            int toRemove = Math.min(needed - consumed, invStack.getCount());
                            invStack.shrink(toRemove);
                            consumed += toRemove;
                        }
                    }
                }
                if (consumed > 0) {
                    deposited.putInt(key, current + consumed);
                    pedestal.setChanged();
                    // Mark the stack as changed for sync
                    scroll.setTag(scroll.getTag());
                }
            } else if ("XP".equals(type)) {
                XpLevelDep xpLevel = group.getXpLevel();
                if (xpLevel != null && xpLevel.isConsume() && xpLevel.getLevel() > 0) {
                    String key = "Group_" + groupIndex + "_XP";
                    if (!deposited.getBoolean(key) && player.experienceLevel >= xpLevel.getLevel()) {
                        player.giveExperienceLevels(-xpLevel.getLevel());
                        deposited.putBoolean(key, true);
                        pedestal.setChanged();
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }
}
