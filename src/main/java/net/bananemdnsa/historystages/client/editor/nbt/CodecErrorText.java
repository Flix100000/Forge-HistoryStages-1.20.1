package net.bananemdnsa.historystages.client.editor.nbt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the one thing worth knowing out of a codec's complaint: which field it is missing.
 *
 * <p>Split out from {@link ComponentShapes} on purpose. The rest of that class needs Minecraft and
 * so cannot be unit-tested, while this is the part most likely to break silently — it depends on
 * how DataFixerUpper happens to word an error, which is not a contract. If a Minecraft update
 * rephrases it, the shape probing quietly stops finding anything and nothing else notices. The
 * test pins the wording this was built against.
 */
public final class CodecErrorText {

    /** DFU words a missing record field as {@code No key <name> in MapLike[...]}. */
    private static final Pattern MISSING_KEY = Pattern.compile("No key ([A-Za-z0-9_]+)");

    private CodecErrorText() {}

    /** The field the codec says is missing, or null when the message does not name one. */
    public static String missingKey(String errorMessage) {
        if (errorMessage == null) return null;
        Matcher matcher = MISSING_KEY.matcher(errorMessage);
        return matcher.find() ? matcher.group(1) : null;
    }
}
