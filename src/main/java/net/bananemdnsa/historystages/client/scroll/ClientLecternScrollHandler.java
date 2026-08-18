package net.bananemdnsa.historystages.client.scroll;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/**
 * Opens the open-scroll screen for a lectern-served scroll.
 *
 * <p>Kept separate from {@code OpenLecternScrollPacket} so the packet class — which is loaded on
 * the dedicated server too, for registration — never mentions {@link OpenScrollScreen} (a
 * {@code Screen} subclass) anywhere in its own bytecode. The {@link net.minecraftforge.fml.DistExecutor}
 * indirection the packet used before already kept the call from running server-side, but leaving
 * the type out entirely is the stronger guarantee and matches the neoforge branch, where the
 * verifier rejects it outright. Mirrors {@code ClientToastHandler}.
 */
public class ClientLecternScrollHandler {

    public static void open(String stageId, BlockPos lecternPos) {
        Minecraft.getInstance().setScreen(new OpenScrollScreen(stageId, lecternPos));
    }
}
