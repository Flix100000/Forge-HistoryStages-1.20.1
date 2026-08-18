package net.bananemdnsa.historystages.data.scroll;

/**
 * One line of the {@code openScrollChapters} config: a chapter, whether it is shown, and how it
 * draws. Text-only chapters are normalised to {@link OpenScrollChapterMode#TEXT} on construction,
 * so no caller has to remember that rule.
 */
public record OpenScrollChapterEntry(OpenScrollChapter chapter, boolean enabled, OpenScrollChapterMode mode) {

    public OpenScrollChapterEntry {
        if (chapter != null && chapter.isTextOnly()) mode = OpenScrollChapterMode.TEXT;
    }
}
