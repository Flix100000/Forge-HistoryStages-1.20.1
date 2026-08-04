package net.bananemdnsa.historystages.network.clientbound;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.bananemdnsa.historystages.GraphConfig;
import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.graph.StageGraphConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server → client sync of {@code graph.toml}.
 *
 * <p>Unlike {@link SyncConfigPacket}, which keeps a hand-written list of keys, this walks the spec
 * itself. Seventy keys in a hand-maintained list is a list somebody forgets to extend — that has
 * already happened in the common config, where several keys are saved server-side and never reach
 * clients at all.
 *
 * <p>Keys are the dotted toml paths ({@code style.global.unlocked.shape}), not the invented flat
 * names {@link SyncConfigPacket} uses. Flat names exist there because leaf names repeat across
 * categories; a path is unambiguous by construction, and this packet has no legacy wire format to
 * stay compatible with.
 */
public record SyncGraphConfigPacket(Map<String, String> values) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncGraphConfigPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(HistoryStages.MOD_ID, "sync_graph_config"));

    public static final StreamCodec<FriendlyByteBuf, SyncGraphConfigPacket> STREAM_CODEC =
            StreamCodec.of(SyncGraphConfigPacket::encode, SyncGraphConfigPacket::decode);

    private static void encode(FriendlyByteBuf buffer, SyncGraphConfigPacket msg) {
        buffer.writeInt(msg.values.size());
        for (Map.Entry<String, String> entry : msg.values.entrySet()) {
            buffer.writeUtf(entry.getKey());
            buffer.writeUtf(entry.getValue());
        }
    }

    private static SyncGraphConfigPacket decode(FriendlyByteBuf buffer) {
        int size = buffer.readInt();
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i < size; i++) {
            values.put(buffer.readUtf(), buffer.readUtf());
        }
        return new SyncGraphConfigPacket(values);
    }

    /** Snapshots every value in the graph spec, keyed by its dotted toml path. */
    public static SyncGraphConfigPacket fromServerConfig() {
        Map<String, String> values = new LinkedHashMap<>();
        collect(GraphConfig.GRAPH_SPEC.getValues(), "", values);
        return new SyncGraphConfigPacket(values);
    }

    /** Depth-first walk of the spec's value tree; leaves are {@link ModConfigSpec.ConfigValue}. */
    private static void collect(UnmodifiableConfig config, String prefix, Map<String, String> out) {
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Object raw = entry.getRawValue();
            if (raw instanceof UnmodifiableConfig nested) {
                collect(nested, path, out);
            } else if (raw instanceof ModConfigSpec.ConfigValue<?> value) {
                Object current = value.get();
                if (current != null) out.put(path, String.valueOf(current));
            }
        }
    }

    public static void handle(SyncGraphConfigPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> apply(msg.values));
    }

    /** Writes the received values straight into the client's own spec objects. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void apply(Map<String, String> values) {
        for (Map.Entry<String, String> entry : values.entrySet()) {
            List<String> path = Arrays.asList(entry.getKey().split("\\."));
            Object raw = GraphConfig.GRAPH_SPEC.getValues().getRaw(path);
            if (!(raw instanceof ModConfigSpec.ConfigValue<?> value)) continue;

            Object parsed = parseLike(value.getDefault(), entry.getValue());
            if (parsed != null) ((ModConfigSpec.ConfigValue) value).set(parsed);
        }

        // graph.toml changed under it — every previously resolved node style is stale.
        StageGraphConfig.invalidateCache();
    }

    /**
     * Parses a string back into the type of the spec's own default value.
     *
     * <p>Returns null when the text cannot be parsed, in which case the caller keeps the local
     * value. A server sending something this client cannot read is not worth failing a login over.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parseLike(Object template, String text) {
        try {
            if (template instanceof Boolean) return Boolean.parseBoolean(text);
            if (template instanceof Integer) return Integer.parseInt(text);
            if (template instanceof Long) return Long.parseLong(text);
            if (template instanceof Double) return Double.parseDouble(text);
            if (template instanceof Enum<?> constant) {
                return Enum.valueOf((Class<Enum>) constant.getDeclaringClass(), text);
            }
            return text;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
