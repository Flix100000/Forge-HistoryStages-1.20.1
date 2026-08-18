package net.bananemdnsa.historystages.data.graph;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;

/**
 * {@code config/historystages/settings/} — the home of files the mod manages itself, next to
 * the {@code global/}, {@code individual/} and {@code logs/} directories.
 */
public final class GraphSettingsPaths {

    public static final String LAYOUT_FILE = "graph_layout.json";
    public static final String STAGES_FILE = "graph_stages.json";

    private GraphSettingsPaths() {}

    /** The settings directory; created if missing. */
    public static File dir() {
        File dir = FMLPaths.CONFIGDIR.get().resolve("historystages").resolve("settings").toFile();
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File file(String name) {
        return new File(dir(), name);
    }
}
