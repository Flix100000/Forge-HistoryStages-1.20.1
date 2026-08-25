package net.bananemdnsa.historystages.gametest;

import java.util.List;

import io.netty.buffer.Unpooled;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.network.clientbound.SyncStagesPacket;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Packets survive the wire.
 *
 * <p>Half of the sync path, and the half that fails quietly. What the client does with a packet
 * cannot be tested here — on a gametest server there is no client — but a codec that drops a field
 * hands the client a perfectly valid packet with the wrong contents, and nothing anywhere throws.
 */
@GameTestHolder(HistoryStages.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PacketTests {

    private PacketTests() {}

    @GameTest(template = "empty")
    public static void syncStagesSurvivesItsCodec(GameTestHelper helper) {
        SyncStagesPacket original = new SyncStagesPacket(
                List.of("gametest:one", "gametest:two", "gametest:three"));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        SyncStagesPacket.STREAM_CODEC.encode(buffer, original);
        SyncStagesPacket restored = SyncStagesPacket.STREAM_CODEC.decode(buffer);

        if (!original.unlockedStages().equals(restored.unlockedStages())) {
            // Both printed, because the realistic fault is one entry lost out of several, and a
            // bare "not equal" leaves somebody comparing two lists by hand.
            helper.fail("the packet did not survive its codec"
                    + "\n  sent:     " + original.unlockedStages()
                    + "\n  received: " + restored.unlockedStages());
            return;
        }
        if (buffer.readableBytes() != 0) {
            helper.fail("the decoder left " + buffer.readableBytes()
                    + " bytes unread, so it reads less than the encoder writes");
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void anEmptySyncStagesSurvivesItsCodec(GameTestHelper helper) {
        // The empty case on its own: a codec that forgets a length prefix passes the case above and
        // fails here, and "nothing unlocked yet" is what every fresh world starts as.
        SyncStagesPacket original = new SyncStagesPacket(List.of());

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        SyncStagesPacket.STREAM_CODEC.encode(buffer, original);
        SyncStagesPacket restored = SyncStagesPacket.STREAM_CODEC.decode(buffer);

        if (!restored.unlockedStages().isEmpty()) {
            helper.fail("an empty packet came back holding " + restored.unlockedStages());
            return;
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void syncStagesKeepsItsOrder(GameTestHelper helper) {
        // Order matters to nothing in the mod today, and that is exactly why a codec could quietly
        // start reversing it. Cheaper to notice here than to wonder about a shuffled stage list.
        List<String> sent = List.of("gametest:alpha", "gametest:beta", "gametest:gamma");
        SyncStagesPacket original = new SyncStagesPacket(sent);

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        SyncStagesPacket.STREAM_CODEC.encode(buffer, original);
        SyncStagesPacket restored = SyncStagesPacket.STREAM_CODEC.decode(buffer);

        for (int i = 0; i < sent.size(); i++) {
            if (!sent.get(i).equals(restored.unlockedStages().get(i))) {
                helper.fail("entry " + i + " came back as " + restored.unlockedStages().get(i)
                        + " instead of " + sent.get(i));
                return;
            }
        }
        helper.succeed();
    }
}
