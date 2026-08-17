package net.bananemdnsa.historystages.data;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Relative folder paths inside a stage tree ({@code config/historystages/global} or
 * {@code .../individual}). A path is {@code /}-separated, with {@code ""} meaning the
 * tree root.
 *
 * <p>Every path that arrives from a network packet passes through {@link #isValid} before
 * it reaches the file system. Stage IDs are file names and are checked with the same
 * segment rules, which is what keeps {@code ..} and absolute paths out of
 * {@code new File(root, id + ".json")}.
 */
public final class StagePaths {

    /** Deepest folder nesting accepted, counted in segments. */
    public static final int MAX_DEPTH = 8;

    private static final String SEGMENT_PATTERN = "[a-zA-Z0-9\\-][a-zA-Z0-9_\\-]*";

    private StagePaths() {}

    /**
     * True for a single path segment: letters, digits, underscore, hyphen — but never with a
     * leading underscore.
     *
     * <p>A leading {@code _} means "ignored by the loader" everywhere in this system: the
     * tree walk skips {@code _note.json} and {@code _backup/} alike. Such a name can therefore
     * never be a usable folder or stage — a folder created under it would be reported as
     * created and then vanish on the next reload, with no way to remove it again.
     */
    public static boolean isValidSegment(String segment) {
        return segment != null && segment.matches(SEGMENT_PATTERN);
    }

    /**
     * True for a relative folder path. The empty string (tree root) is valid; anything
     * containing {@code ..}, a backslash, a drive letter, a leading slash or an empty
     * segment is not, and neither is a path deeper than {@link #MAX_DEPTH}.
     */
    public static boolean isValid(String path) {
        if (path == null) return false;
        if (path.isEmpty()) return true;
        if (path.contains("\\") || path.contains(":")) return false;
        String[] segments = path.split("/", -1);
        if (segments.length > MAX_DEPTH) return false;
        for (String segment : segments) {
            if (!isValidSegment(segment)) return false;
        }
        return true;
    }

    /** Number of segments in {@code path}; 0 for the tree root. */
    public static int depth(String path) {
        if (path == null || path.isEmpty()) return 0;
        return path.split("/", -1).length;
    }

    /** {@code "a/b/c" -> "a/b"}, {@code "a" -> ""}, {@code "" -> ""}. */
    public static String parent(String path) {
        if (path == null || path.isEmpty()) return "";
        int slash = path.lastIndexOf('/');
        return slash < 0 ? "" : path.substring(0, slash);
    }

    /** {@code "a/b/c" -> "c"}, {@code "" -> ""}. */
    public static String name(String path) {
        if (path == null || path.isEmpty()) return "";
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /** Appends {@code child} to {@code path}, handling the root case. */
    public static String join(String path, String child) {
        if (path == null || path.isEmpty()) return child;
        if (child == null || child.isEmpty()) return path;
        return path + "/" + child;
    }

    /** Every segment prefix of {@code path}, root first: {@code "a/b" -> ["", "a", "a/b"]}. */
    public static List<String> breadcrumb(String path) {
        List<String> out = new ArrayList<>();
        out.add("");
        if (path == null || path.isEmpty()) return out;
        StringBuilder sb = new StringBuilder();
        for (String segment : path.split("/", -1)) {
            if (sb.length() > 0) sb.append('/');
            sb.append(segment);
            out.add(sb.toString());
        }
        return out;
    }

    /**
     * Resolves {@code path} against {@code root}, or returns null when the path fails
     * {@link #isValid} or would escape {@code root}. The canonical-prefix check is the
     * backstop in case a future caller loosens the segment rules.
     */
    public static File resolve(File root, String path) {
        if (!isValid(path)) return null;
        File target = path.isEmpty() ? root : new File(root, path.replace('/', File.separatorChar));
        try {
            String rootPath = root.getCanonicalPath();
            String targetPath = target.getCanonicalPath();
            if (!targetPath.equals(rootPath) && !targetPath.startsWith(rootPath + File.separator)) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
        return target;
    }
}
