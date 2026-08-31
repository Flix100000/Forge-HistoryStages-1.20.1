package net.bananemdnsa.historystages.compat;

/**
 * Turns a stage id into something a {@code ResourceLocation} path will accept.
 *
 * <p>Stage ids are author-chosen, and this mod deliberately allows what a resource location does
 * not: {@code StageManager} takes an uppercase file name and only suggests lowercase as a matter
 * of style. The recipe viewers need a real id per displayed entry though, and
 * {@code ResourceLocation.fromNamespaceAndPath} throws on anything outside {@code [a-z0-9/._-]}.
 *
 * <p>The two viewers used to sanitise only the colon. A single uppercase letter in a single stage
 * id then threw out of the middle of the registration loop, before the viewer was handed the list
 * — so <em>every</em> reseal entry vanished, not just the offending one, and JEI reported it as
 * an error from our plugin.
 *
 * <p>Minecraft-free so the rule can be tested; the callers wrap the result in a resource location.
 */
public final class StageDisplayPath {

    private StageDisplayPath() {}

    /**
     * A resource-location path segment for {@code stageId}.
     *
     * <p>An id that already qualifies comes back untouched — that is the common case, and folding
     * it anyway would make every id in the viewers and the logs harder to read for the sake of the
     * rare broken one. Anything else is lowercased with the illegal characters replaced, and then
     * carries a short suffix derived from the original: without it {@code Bronze} and
     * {@code bronze} would both land on {@code bronze}, and one stage's entry would sit on top of
     * the other's.
     */
    public static String of(String stageId) {
        String id = stageId == null ? "" : stageId;

        StringBuilder path = new StringBuilder(id.length());
        boolean rewritten = false;
        for (int i = 0; i < id.length(); i++) {
            char original = id.charAt(i);
            char lower = Character.toLowerCase(original);
            if (isAllowed(lower)) {
                path.append(lower);
                if (lower != original) rewritten = true;
            } else {
                path.append('_');
                rewritten = true;
            }
        }

        if (!rewritten) return path.toString();
        return path + "_" + Integer.toHexString(id.hashCode() & 0x7fffffff);
    }

    /** Exactly the set {@code ResourceLocation.assertValidPath} allows. */
    private static boolean isAllowed(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '/' || c == '.' || c == '_' || c == '-';
    }
}
