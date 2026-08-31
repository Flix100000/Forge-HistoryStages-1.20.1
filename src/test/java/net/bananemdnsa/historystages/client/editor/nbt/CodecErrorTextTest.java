package net.bananemdnsa.historystages.client.editor.nbt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Pins the DataFixerUpper error wording the shape probing reads.
 *
 * <p>Nothing guarantees this phrasing across Minecraft versions. If it changes, the probing stops
 * naming fields and the component dialog silently loses its best hint — a failure that shows up as
 * "the help is just gone" rather than as anything breaking. This test is the tripwire.
 */
class CodecErrorTextTest {

    @Test
    void namesTheMissingField() {
        assertEquals("predicates",
                CodecErrorText.missingKey("No key predicates in MapLike[{}]"));
    }

    @Test
    void findsTheFieldInsideALongerComplaint() {
        assertEquals("levels", CodecErrorText.missingKey(
                "Not a JSON object: \"\"; No key levels in MapLike[{\"show_in_tooltip\":true}]"));
    }

    @Test
    void keepsUnderscoresInFieldNames() {
        assertEquals("show_in_tooltip",
                CodecErrorText.missingKey("No key show_in_tooltip in MapLike[{}]"));
    }

    @Test
    void aComplaintAboutSomethingElseNamesNothing() {
        assertNull(CodecErrorText.missingKey("Not a JSON object: []"));
        assertNull(CodecErrorText.missingKey(""));
        assertNull(CodecErrorText.missingKey(null));
    }
}
