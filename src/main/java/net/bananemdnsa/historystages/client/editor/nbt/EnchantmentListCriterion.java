package net.bananemdnsa.historystages.client.editor.nbt;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code Enchantments} or {@code StoredEnchantments}. Stays a top-level key because
 * {@code NbtMatcher.buildMatchTag} synthesises exactly these two from the item's enchantment
 * components — they are not looked up in {@code custom_data}.
 */
public final class EnchantmentListCriterion implements NbtCriterion {

    /** One enchantment line. {@code level} stays a string so ranges like "1-4" survive editing. */
    public static final class Line {
        public String id;
        public String level;

        public Line(String id, String level) {
            this.id = id == null ? "" : id;
            this.level = level == null ? "" : level;
        }
    }

    private final String key;
    public final List<Line> lines = new ArrayList<>();

    public EnchantmentListCriterion(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    @Override
    public CriterionKind kind() {
        return CriterionKind.ENCHANTMENTS;
    }

    @Override
    public String identity() {
        return key;
    }

    @Override
    public boolean isEmpty() {
        return lines.stream().allMatch(l -> l.id.isBlank());
    }
}
