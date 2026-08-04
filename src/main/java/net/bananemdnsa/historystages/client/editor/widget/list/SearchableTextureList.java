package net.bananemdnsa.historystages.client.editor.widget.list;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Searchable overlay list of the block textures the loaded resource packs provide, for the
 * graph's tiled background.
 *
 * <p>Block textures only. They are the ones built to sit edge to edge — GUI and entity textures
 * would fill the list with things that tile into visible seams, and there are thousands of them.
 */
public class SearchableTextureList extends AbstractSearchableList<String> {

    private static final String BLOCK_TEXTURE_DIR = "textures/block";
    private static final int PREVIEW = 16;

    public SearchableTextureList(Consumer<String> onSelect) {
        super(Component.translatable("editor.historystages.search.textures").getString(),
                onSelect, null);
    }

    @Override
    protected List<String> loadEntries() {
        var resources = Minecraft.getInstance().getResourceManager();

        // Animated textures ship a .mcmeta beside the .png and are stored as a vertical strip of
        // frames. Tiled as a still image the whole strip shows at once, which looks like a bug
        // rather than a choice, so they are left out of the list.
        Set<String> animated = new HashSet<>();
        for (ResourceLocation rl : resources.listResources(BLOCK_TEXTURE_DIR,
                p -> p.getPath().endsWith(".png.mcmeta")).keySet()) {
            animated.add(rl.toString().replace(".png.mcmeta", ".png"));
        }

        List<String> textures = new ArrayList<>();
        for (ResourceLocation rl : resources.listResources(BLOCK_TEXTURE_DIR,
                p -> p.getPath().endsWith(".png")).keySet()) {
            String id = rl.toString();
            if (!animated.contains(id)) textures.add(id);
        }
        textures.sort(String::compareToIgnoreCase);
        return textures;
    }

    @Override
    protected String getIdForFilter(String entry) {
        return entry;
    }

    @Override
    protected boolean matchesQuery(String entry, String lowerCaseQuery) {
        return entry.toLowerCase().contains(lowerCaseQuery);
    }

    @Override
    protected String selectionValueOf(String entry) {
        return entry;
    }

    /**
     * The block-atlas sprite for a texture path, or null when it is not stitched into the atlas.
     *
     * <p>Drawing the {@code .png} by its own path instead would make Minecraft load and upload
     * that file as a standalone texture the first time it is drawn — once per row, synchronously,
     * while the user scrolls. The atlas is already in memory and costs nothing to sample.
     */
    public static TextureAtlasSprite spriteFor(String texturePath) {
        ResourceLocation rl = ResourceLocation.tryParse(texturePath.trim());
        if (rl == null) return null;
        String path = rl.getPath();
        if (path.startsWith("textures/")) path = path.substring("textures/".length());
        if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);

        return Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(ResourceLocation.fromNamespaceAndPath(rl.getNamespace(), path));
    }

    @Override
    protected void renderRow(GuiGraphics g, Font font, String entry,
                             int x, int y, int w, int h, boolean hovered, int rowIndex) {
        TextureAtlasSprite sprite = spriteFor(entry);
        if (sprite != null) {
            g.blit(x + 2, y + (h - PREVIEW) / 2, 0, PREVIEW, PREVIEW, sprite);
        }

        // The directory prefix is the same on every row and only pushes the part that
        // distinguishes them out of view.
        String label = entry.replace(BLOCK_TEXTURE_DIR + "/", "").replace(".png", "");
        drawRowText(g, font, label, x + PREVIEW + 4, y, w - PREVIEW - 4, hovered);
    }
}
