package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.block.entity.ResearchPedestalBlockEntity;
import net.bananemdnsa.historystages.data.StageEntry;
import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.dependency.DependencyChecker;
import net.bananemdnsa.historystages.data.dependency.DependencyResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> Server: Request dependency status check for a specific stage.
 * Includes the pedestal's BlockPos so the server can read the scroll's
 * deposited-dependencies NBT.  Server responds with SyncDependencyStatusPacket.
 */
public record CheckDependencyPacket(String stageId, boolean isIndividual, BlockPos pos)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CheckDependencyPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "check_dependency"));

    public static final StreamCodec<FriendlyByteBuf, CheckDependencyPacket> STREAM_CODEC =
            StreamCodec.of(CheckDependencyPacket::encode, CheckDependencyPacket::decode);

    private static void encode(FriendlyByteBuf buf, CheckDependencyPacket packet) {
        buf.writeUtf(packet.stageId);
        buf.writeBoolean(packet.isIndividual);
        buf.writeBlockPos(packet.pos);
    }

    private static CheckDependencyPacket decode(FriendlyByteBuf buf) {
        return new CheckDependencyPacket(buf.readUtf(), buf.readBoolean(), buf.readBlockPos());
    }

    public static void handle(CheckDependencyPacket packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;

            StageEntry entry = packet.isIndividual
                    ? StageManager.getIndividualStages().get(packet.stageId)
                    : StageManager.getStages().get(packet.stageId);

            if (entry == null) return;

            // Try to read deposited NBT from the pedestal scroll for accurate status
            CompoundTag depositedTag = null;
            BlockEntity be = player.level().getBlockEntity(packet.pos);
            if (be instanceof ResearchPedestalBlockEntity pedestal) {
                ItemStack scroll = pedestal.getScrollStack();
                CompoundTag scrollTag = scroll.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
                if (scrollTag.contains("DepositedDependencies")) {
                    depositedTag = scrollTag.getCompound("DepositedDependencies");
                }
            }

            DependencyResult result = DependencyChecker.checkAll(entry, player, player.level(), depositedTag);
            PacketDistributor.sendToPlayer(player, new SyncDependencyStatusPacket(packet.stageId, result));
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
