package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.data.DependencyGroup;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyItem;
import net.bananemdnsa.historystages.data.dependency.XpLevelDep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Deposit an item or XP level into the research scroll
 * to satisfy a dependency requirement.
 */
public record DepositDependencyPacket(BlockPos pos, int groupIndex, String depositType, String data)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<DepositDependencyPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "deposit_dependency"));

    public static final StreamCodec<FriendlyByteBuf, DepositDependencyPacket> STREAM_CODEC =
            StreamCodec.of(DepositDependencyPacket::encode, DepositDependencyPacket::decode);

    private static void encode(FriendlyByteBuf buf, DepositDependencyPacket packet) {
        buf.writeBlockPos(packet.pos);
        buf.writeInt(packet.groupIndex);
        buf.writeUtf(packet.depositType);
        buf.writeUtf(packet.data);
    }

    private static DepositDependencyPacket decode(FriendlyByteBuf buf) {
        return new DepositDependencyPacket(buf.readBlockPos(), buf.readInt(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(DepositDependencyPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (!(be instanceof ResearchPedestalBlockEntity pedestal)) return;

            ItemStack scroll = pedestal.getScrollStack();
            if (scroll.isEmpty()) return;

            CompoundTag tag = scroll.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (!tag.contains("StageResearch")) return;

            String stageId = tag.getString("StageResearch");
            StageEntry stageEntry = StageManager.isIndividualStage(stageId)
                    ? StageManager.getIndividualStages().get(stageId)
                    : StageManager.getStages().get(stageId);

            if (stageEntry == null || stageEntry.getDependencies() == null
                    || packet.groupIndex < 0 || packet.groupIndex >= stageEntry.getDependencies().size())
                return;

            DependencyGroup group = stageEntry.getDependencies().get(packet.groupIndex);

            CompoundTag deposited = tag.contains("DepositedDependencies")
                    ? tag.getCompound("DepositedDependencies")
                    : new CompoundTag();

            boolean changed = false;

            if ("ITEM".equals(packet.depositType)) {
                ResourceLocation rl = ResourceLocation.tryParse(packet.data);
                if (rl == null) return;

                int required = 0;
                for (DependencyItem item : group.getItems()) {
                    if (item.getId().equals(packet.data)) {
                        required = item.getCount();
                        break;
                    }
                }
                if (required == 0) return;

                String key = "Group_" + packet.groupIndex + "_Item_" + packet.data;
                int current = deposited.getInt(key);
                int needed = required - current;
                if (needed <= 0) return;

                int consumed = 0;
                for (int i = 0; i < player.getInventory().getContainerSize() && consumed < needed; i++) {
                    ItemStack invStack = player.getInventory().getItem(i);
                    if (!invStack.isEmpty()
                            && rl.equals(BuiltInRegistries.ITEM.getKey(invStack.getItem()))) {
                        int toRemove = Math.min(needed - consumed, invStack.getCount());
                        invStack.shrink(toRemove);
                        consumed += toRemove;
                    }
                }
                if (consumed > 0) {
                    deposited.putInt(key, current + consumed);
                    changed = true;
                }

            } else if ("XP".equals(packet.depositType)) {
                XpLevelDep xpLevel = group.getXpLevel();
                if (xpLevel != null && xpLevel.isConsume() && xpLevel.getLevel() > 0) {
                    String key = "Group_" + packet.groupIndex + "_XP";
                    if (!deposited.getBoolean(key) && player.experienceLevel >= xpLevel.getLevel()) {
                        player.giveExperienceLevels(-xpLevel.getLevel());
                        deposited.putBoolean(key, true);
                        changed = true;
                    }
                }
            }

            if (changed) {
                tag.put("DepositedDependencies", deposited);
                scroll.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                pedestal.setChanged();
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
