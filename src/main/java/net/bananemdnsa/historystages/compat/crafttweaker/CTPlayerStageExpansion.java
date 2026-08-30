package net.bananemdnsa.historystages.compat.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import net.bananemdnsa.historystages.compat.script.ScriptStageApi;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.openzen.zencode.java.ZenCodeType;

/**
 * {@code player.hasStage("bronze")} — the shape GameStages users already have in their scripts,
 * so a pack migrating over does not have to relearn the call.
 *
 * <p>Asks both scopes and does not warn about the one it does not find the stage in. That is the
 * whole difference to {@code isUnlockedFor}: {@code hasStage} has no notion of two scopes, so
 * naming a global stage here is correct rather than a mistake, and warning about it would fire on
 * every correct use.
 */
@ZenRegister
@ZenCodeType.Expansion("crafttweaker.api.entity.type.player.Player")
public final class CTPlayerStageExpansion {

    private CTPlayerStageExpansion() {}

    @ZenCodeType.Method
    public static boolean hasStage(Player player, String stageId) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        return ScriptStageApi.hasStageEitherScope(serverPlayer, stageId);
    }
}
