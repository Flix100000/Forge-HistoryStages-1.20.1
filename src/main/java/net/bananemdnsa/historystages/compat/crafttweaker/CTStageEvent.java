package net.bananemdnsa.historystages.compat.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import org.openzen.zencode.java.ZenCodeType;

/**
 * What a ZenScript listener receives when a global stage changes.
 *
 * <p>An object rather than a two-argument lambda, for two reasons: CraftTweaker maps script
 * closures onto {@link java.util.function.Consumer}, which takes exactly one argument, and it
 * makes the ZenScript side read the same as the KubeJS side — {@code event.stage} in both.
 */
@ZenRegister
@ZenCodeType.Name("mods.historystages.StageEvent")
public class CTStageEvent {

    private final String stage;
    private final String displayName;

    public CTStageEvent(String stage, String displayName) {
        this.stage = stage;
        this.displayName = displayName;
    }

    @ZenCodeType.Getter("stage")
    public String getStage() {
        return stage;
    }

    @ZenCodeType.Getter("displayName")
    public String getDisplayName() {
        return displayName;
    }
}
