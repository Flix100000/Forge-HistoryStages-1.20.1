package net.bananemdnsa.historystages.client.scroll;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Opens the open-scroll screen for a lectern-served scroll.
 *
 * <p>Kept separate from {@code OpenLecternScrollPacket} so the packet class — which is
 * loaded on the dedicated server too — never references {@link OpenScrollScreen} (a
 * {@code Screen} subclass) directly; that reference would fail RuntimeDistCleaner's
 * verification on a server-only dist.
 */
public class ClientLecternScrollHandler {

    public static void open(String stageId, BlockPos lecternPos) {
        Minecraft.getInstance().setScreen(new OpenScrollScreen(stageId, lecternPos));
    }
}
