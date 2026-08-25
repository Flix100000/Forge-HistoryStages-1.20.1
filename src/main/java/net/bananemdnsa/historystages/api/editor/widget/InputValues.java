package net.bananemdnsa.historystages.api.editor.widget;

import java.util.Map;

/**
 * The trimmed field values handed to {@code AbstractInputScreen.onConfirm}. Values are only
 * produced after validation has passed, so the numeric getters cannot see malformed input.
 */
public final class InputValues {

    private final Map<String, String> values;

    InputValues(Map<String, String> values) {
        this.values = values;
    }

    /** @return the trimmed value, or "" when the key is unknown. */
    public String getString(String key) {
        return values.getOrDefault(key, "");
    }

    public int getInt(String key) {
        return Integer.parseInt(getString(key));
    }

    public double getDouble(String key) {
        return Double.parseDouble(getString(key));
    }
}
