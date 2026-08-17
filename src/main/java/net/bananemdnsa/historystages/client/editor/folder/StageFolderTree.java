package net.bananemdnsa.historystages.client.editor.folder;

import net.bananemdnsa.historystages.data.StageManager;
import net.bananemdnsa.historystages.data.StagePaths;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read model over the folder data synced with the stage definitions. Answers what the
 * overview screen needs to draw one level of a tree, without any file access — on a
 * dedicated server the client has no config files, only what the sync delivered.
 */
public final class StageFolderTree {

    /** One folder row: its full path, the segment shown as label, and how many stages sit below it. */
    public record Folder(String path, String name, int stageCount) {}

    private StageFolderTree() {}

    private static Set<String> folderSet(boolean individual) {
        return individual ? StageManager.getIndividualFolders() : StageManager.getFolders();
    }

    private static Map<String, String> pathMap(boolean individual) {
        return individual ? StageManager.getIndividualStagePaths() : StageManager.getStagePaths();
    }

    /** Direct subfolders of {@code path}, sorted by name. */
    public static List<Folder> foldersAt(boolean individual, String path) {
        List<Folder> out = new ArrayList<>();
        for (String folder : folderSet(individual)) {
            if (!StagePaths.parent(folder).equals(path)) continue;
            out.add(new Folder(folder, StagePaths.name(folder), stageCountBelow(individual, folder)));
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    /**
     * Stage IDs whose file sits directly in {@code path}, in the order the manager reports.
     * {@code order} is passed in so the caller keeps using the list it already filtered.
     */
    public static List<String> stagesAt(boolean individual, String path, List<String> order) {
        Map<String, String> paths = pathMap(individual);
        List<String> out = new ArrayList<>();
        for (String id : order) {
            if (paths.getOrDefault(id, "").equals(path)) out.add(id);
        }
        return out;
    }

    /** Stages in {@code folder} and everything below it. */
    public static int stageCountBelow(boolean individual, String folder) {
        int count = 0;
        String prefix = folder + "/";
        for (String stageFolder : pathMap(individual).values()) {
            if (stageFolder.equals(folder) || stageFolder.startsWith(prefix)) count++;
        }
        return count;
    }

    /** True when {@code path} still exists in the synced data — used after another admin deleted it. */
    public static boolean exists(boolean individual, String path) {
        return path.isEmpty() || folderSet(individual).contains(path);
    }
}
