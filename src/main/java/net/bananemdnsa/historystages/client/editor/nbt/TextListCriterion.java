package net.bananemdnsa.historystages.client.editor.nbt;

import java.util.ArrayList;
import java.util.List;

/**
 * A component whose value is a list of text lines — in practice {@code minecraft:lore}. Same
 * target as {@link ComponentCriterion} ({@code components.<id>}), but edited one line at a time
 * instead of as one blob of JSON.
 */
public final class TextListCriterion implements NbtCriterion {

    private final String componentId;
    public final List<String> lines = new ArrayList<>();
    public final String presetName;

    public TextListCriterion(String componentId, String presetName) {
        this.componentId = componentId;
        this.presetName = presetName;
    }

    public String componentId() {
        return componentId;
    }

    @Override
    public CriterionKind kind() {
        return CriterionKind.TEXT_LIST;
    }

    @Override
    public String identity() {
        return "components." + componentId;
    }

    @Override
    public boolean isEmpty() {
        return lines.stream().allMatch(String::isBlank);
    }
}
