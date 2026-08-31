package net.bananemdnsa.historystages.client.editor.nbt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The checks the editor runs while criteria are being edited. Warnings, not errors — the pack
 * author may be describing an item a mod produces that this client does not know about, so nothing
 * here blocks a save.
 *
 * <p>Both registry lookups are injected rather than read from {@code BuiltInRegistries} directly.
 * That keeps this class free of Minecraft types and therefore unit-testable; the screen passes the
 * real registries, the test passes a map.
 */
public final class NbtCriteriaValidator {

    public enum Kind { UNKNOWN_ENCHANTMENT, LEVEL_TOO_HIGH, UNKNOWN_POTION }

    /**
     * @param criterionIndex index into the list handed to {@link #validate}
     * @param lineIndex      index of the offending line inside that criterion, -1 when the criterion
     *                       as a whole is meant
     * @param subject        the id that triggered the warning
     * @param limit          the maximum that was exceeded, 0 when not applicable
     * @param actual         the value that exceeded it, 0 when not applicable
     */
    public record Warning(Kind kind, int criterionIndex, int lineIndex, String subject,
                          int limit, int actual) {}

    /** Maximum level of an enchantment id, or null when this client does not know the id. */
    private final Function<String, Integer> maxEnchantmentLevel;
    private final Predicate<String> potionExists;

    public NbtCriteriaValidator(Function<String, Integer> maxEnchantmentLevel,
                                Predicate<String> potionExists) {
        this.maxEnchantmentLevel = maxEnchantmentLevel;
        this.potionExists = potionExists;
    }

    public List<Warning> validate(List<NbtCriterion> criteria) {
        List<Warning> warnings = new ArrayList<>();

        for (int i = 0; i < criteria.size(); i++) {
            NbtCriterion criterion = criteria.get(i);

            if (criterion instanceof EnchantmentListCriterion ench) {
                for (int line = 0; line < ench.lines.size(); line++) {
                    checkEnchantment(warnings, i, line, ench.lines.get(line));
                }
            } else if (criterion instanceof CustomDataCriterion custom
                    && "Potion".equals(custom.key)
                    && !custom.valueText.isBlank()
                    && !potionExists.test(custom.valueText.trim())) {
                warnings.add(new Warning(Kind.UNKNOWN_POTION, i, -1, custom.valueText.trim(), 0, 0));
            }
        }
        return warnings;
    }

    private void checkEnchantment(List<Warning> warnings, int criterionIndex, int lineIndex,
                                  EnchantmentListCriterion.Line line) {
        if (line.id.isBlank()) return;

        Integer max = maxEnchantmentLevel.apply(line.id);
        if (max == null) {
            warnings.add(new Warning(Kind.UNKNOWN_ENCHANTMENT, criterionIndex, lineIndex, line.id, 0, 0));
            return;
        }

        Integer requested = upperLevel(line.level);
        if (requested != null && requested > max) {
            warnings.add(new Warning(Kind.LEVEL_TOO_HIGH, criterionIndex, lineIndex, line.id, max, requested));
        }
    }

    /** The highest level a line asks for: the number itself, or the top of a "1-4" range. */
    private static Integer upperLevel(String level) {
        String trimmed = level.trim();
        if (trimmed.matches("\\d+")) return Integer.parseInt(trimmed);
        if (trimmed.matches("\\d+-\\d+")) return Integer.parseInt(trimmed.split("-")[1]);
        return null;
    }
}
