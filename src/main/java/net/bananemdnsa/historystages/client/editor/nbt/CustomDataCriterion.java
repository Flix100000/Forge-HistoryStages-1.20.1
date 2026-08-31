package net.bananemdnsa.historystages.client.editor.nbt;

/**
 * A free top-level key. This is the only shape that is checked against the item's
 * {@code custom_data} tag, which is what {@code NbtMatcher.buildMatchTag} starts from.
 */
public final class CustomDataCriterion implements NbtCriterion {

    public String key;
    /** Raw text as typed. Parsed as JSON on save so numbers stay numbers. */
    public String valueText;
    /**
     * True when the key is one of the pre-1.20.5 names that people expect to work but that only
     * match if a pack literally put them in {@code custom_data}. Drives the hint on the card;
     * never acted on automatically.
     */
    public boolean legacySuspect;

    public CustomDataCriterion(String key, String valueText, boolean legacySuspect) {
        this.key = key == null ? "" : key;
        this.valueText = valueText == null ? "" : valueText;
        this.legacySuspect = legacySuspect;
    }

    @Override
    public CriterionKind kind() {
        return CriterionKind.CUSTOM_DATA;
    }

    @Override
    public String identity() {
        return key;
    }

    @Override
    public boolean isEmpty() {
        return key.isBlank() || valueText.isBlank();
    }
}
