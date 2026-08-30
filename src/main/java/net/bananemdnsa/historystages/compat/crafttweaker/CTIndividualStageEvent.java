package net.bananemdnsa.historystages.compat.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import net.minecraft.world.entity.player.Player;
import org.openzen.zencode.java.ZenCodeType;
import org.jetbrains.annotations.Nullable;

/**
 * What a ZenScript listener receives when an individual stage changes for one player.
 *
 * <p>{@link Player} is handed over unwrapped: CraftTweaker registers the vanilla class itself as
 * the ZenScript type {@code crafttweaker.api.entity.type.player.Player}, so there is no wrapper
 * to build and a script gets every method CraftTweaker expands onto it.
 */
@ZenRegister
@ZenCodeType.Name("mods.historystages.IndividualStageEvent")
public class CTIndividualStageEvent extends CTStageEvent {

    private final Player player;

    public CTIndividualStageEvent(String stage, String displayName, @Nullable Player player) {
        super(stage, displayName);
        this.player = player;
    }

    /**
     * Null when that player is offline, which an individual relock on a timer can perfectly well
     * be. Scripts that talk to the player have to check.
     */
    @ZenCodeType.Getter("player")
    @Nullable
    public Player getPlayer() {
        return player;
    }
}
