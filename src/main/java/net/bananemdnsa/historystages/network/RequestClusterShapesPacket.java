package net.bananemdnsa.historystages.network;

import net.bananemdnsa.historystages.Config;
import net.bananemdnsa.historystages.structure.ClusterBuilder;
import net.bananemdnsa.historystages.structure.StructureCluster;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Client-issued: dumps the current cluster + lockShape geometry around the player to chat.
 * Used to diagnose unexpected huge lock zones — shows exactly which BBs are being treated
 * as lock geometry and how big each one is. The header line is click-to-copy with the full
 * plain-text report.
 *
 * <p>Server replies via chat messages directly to the sender — no separate reply packet.
 */
public class RequestClusterShapesPacket {

    public RequestClusterShapesPacket() {}

    public static void encode(RequestClusterShapesPacket msg, FriendlyByteBuf buffer) {
    }

    public static RequestClusterShapesPacket decode(FriendlyByteBuf buffer) {
        return new RequestClusterShapesPacket();
    }

    public static void handle(RequestClusterShapesPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) return;

            int padding = Config.COMMON.structureLockPadding.get();
            int clusterDistance = Config.COMMON.structureClusterDistance.get();
            BlockPos pos = player.blockPosition();
            List<StructureCluster> clusters = ClusterBuilder.collectClustersNear(
                    player.serverLevel(), pos, 8, padding, clusterDistance);

            StringBuilder plain = new StringBuilder();
            plain.append("Cluster shapes at ").append(pos.getX()).append(", ").append(pos.getY())
                    .append(", ").append(pos.getZ()).append('\n');
            plain.append("padding=").append(padding).append(" clusterDistance=").append(clusterDistance).append('\n');

            if (clusters.isEmpty()) {
                plain.append("(no clusters near you)\n");
            } else {
                for (int i = 0; i < clusters.size(); i++) {
                    StructureCluster c = clusters.get(i);
                    String id = c.structure().unwrapKey().map(k -> k.location().toString()).orElse("<unknown>");
                    plain.append("\n[Cluster ").append(i + 1).append("] ").append(id)
                            .append(" — ").append(c.pieceBoxes().size()).append(" pieces, ")
                            .append(c.lockShapes().size()).append(" lock shapes\n");
                    plain.append("  envelope: ").append(formatBB(c.bounds()))
                            .append(" (span ").append(spanStr(c.bounds())).append(")\n");
                    plain.append("  pieces (largest first):\n");
                    appendBoxes(plain, c.pieceBoxes(), 5);
                    plain.append("  lockShapes (largest first):\n");
                    appendBoxes(plain, c.lockShapes(), 5);
                }
            }

            String plainReport = plain.toString();

            // Header line is click-to-copy with the full report.
            Component header = Component.literal("§6--- Cluster shapes §e[click to copy full report]§6 ---")
                    .copy()
                    .withStyle(Style.EMPTY
                            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, plainReport))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Copy full report to clipboard")
                                            .withStyle(ChatFormatting.GRAY))));
            player.sendSystemMessage(header);

            // Plus an in-chat readable version (truncated to top entries per cluster).
            player.sendSystemMessage(Component.literal("§7Position: §f"
                    + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                    + "  §7Config: §fpadding=" + padding + " clusterDistance=" + clusterDistance));

            if (clusters.isEmpty()) {
                player.sendSystemMessage(Component.literal("  §7(no clusters near you)"));
                return;
            }

            for (int i = 0; i < clusters.size(); i++) {
                StructureCluster c = clusters.get(i);
                String id = c.structure().unwrapKey().map(k -> k.location().toString()).orElse("<unknown>");
                player.sendSystemMessage(Component.literal(
                        "§e[" + (i + 1) + "] §f" + id
                                + " §7— " + c.pieceBoxes().size() + " pieces, "
                                + c.lockShapes().size() + " shapes"));
                player.sendSystemMessage(Component.literal(
                        "  §8envelope: " + formatBB(c.bounds()) + " §7(" + spanStr(c.bounds()) + ")"));
                List<BoundingBox> sortedShapes = c.lockShapes().stream()
                        .sorted((a, b) -> Long.compare(volume(b), volume(a)))
                        .toList();
                int top = Math.min(3, sortedShapes.size());
                for (int j = 0; j < top; j++) {
                    BoundingBox bb = sortedShapes.get(j);
                    player.sendSystemMessage(Component.literal(
                            "  §8• " + spanStr(bb) + " §7" + formatBB(bb)));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void appendBoxes(StringBuilder sb, List<BoundingBox> boxes, int limit) {
        List<BoundingBox> sorted = boxes.stream()
                .sorted((a, b) -> Long.compare(volume(b), volume(a)))
                .toList();
        int max = Math.min(limit, sorted.size());
        for (int i = 0; i < max; i++) {
            BoundingBox bb = sorted.get(i);
            sb.append("    ").append(spanStr(bb)).append("  ").append(formatBB(bb))
                    .append("  vol=").append(volume(bb)).append('\n');
        }
        if (sorted.size() > max) {
            sb.append("    ... +").append(sorted.size() - max).append(" more\n");
        }
    }

    private static long volume(BoundingBox bb) {
        long x = bb.maxX() - bb.minX() + 1L;
        long y = bb.maxY() - bb.minY() + 1L;
        long z = bb.maxZ() - bb.minZ() + 1L;
        return x * y * z;
    }

    private static String spanStr(BoundingBox bb) {
        return (bb.maxX() - bb.minX() + 1) + "x" + (bb.maxY() - bb.minY() + 1) + "x" + (bb.maxZ() - bb.minZ() + 1);
    }

    private static String formatBB(BoundingBox bb) {
        return "(" + bb.minX() + "," + bb.minY() + "," + bb.minZ() + ")->(" + bb.maxX() + "," + bb.maxY() + "," + bb.maxZ() + ")";
    }
}
