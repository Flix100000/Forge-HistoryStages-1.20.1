package net.bananemdnsa.historystages.client.editor.dialog;

import net.bananemdnsa.historystages.HistoryStages;
import net.bananemdnsa.historystages.client.editor.widget.StyledButton;
import net.bananemdnsa.historystages.client.editor.widget.dialog.AbstractModalScreen;
import net.bananemdnsa.historystages.util.ModLinks;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.List;

/**
 * Who made the mod and where to reach it — reachable from the editor's header menu.
 *
 * <p>The version is shown next to the bug-report link on purpose: reports arrive with a
 * wrong or missing version often enough that having it on screen at the moment someone
 * decides to file one is worth the line.
 */
public class CreditsScreen extends AbstractModalScreen {

    private static final int LINE_H = 10;
    private static final int LINK_H = 20;
    private static final int LINK_GAP = 6;
    /** Space between the author block and the links below it. */
    private static final int SECTION_GAP = 12;

    private static final int AUTHORS_COLOR = 0xFFFFFF;

    /** One button per entry, in the order they are drawn. */
    private record Link(String labelKey, String url) {}

    private static final List<Link> LINKS = List.of(
            new Link("editor.historystages.credits.wiki", ModLinks.WIKI),
            new Link("editor.historystages.credits.discord", ModLinks.DISCORD),
            new Link("editor.historystages.credits.report_bug", ModLinks.BUG_REPORT),
            new Link("editor.historystages.credits.suggest_feature", ModLinks.FEATURE_REQUEST));

    /** Resolved once — {@link #subtitle()} is consulted every frame. */
    private final Component versionLine;

    public CreditsScreen(Screen parent) {
        super(parent, Component.translatable("editor.historystages.credits.title"));
        this.versionLine = Component.translatable("editor.historystages.credits.version", modVersion());
    }

    /** Reads the version off the loaded mod rather than a constant, so it cannot go stale. */
    private static String modVersion() {
        return ModList.get().getModContainerById(HistoryStages.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("?");
    }

    @Override
    protected Component subtitle() {
        return versionLine;
    }

    @Override
    protected Component confirmLabel() {
        return Component.translatable("editor.historystages.close");
    }

    /** Nothing to cancel — the single button just closes the dialog. */
    @Override
    protected boolean showCancelButton() {
        return false;
    }

    @Override
    protected void onConfirm() {
        this.minecraft.setScreen(parent);
    }

    @Override
    protected int contentHeight() {
        return LINE_H * 2 + SECTION_GAP
                + LINK_H * LINKS.size() + LINK_GAP * (LINKS.size() - 1);
    }

    @Override
    protected void buildContentWidgets() {
        int x = boxX + PAD;
        int w = boxW - PAD * 2;
        int y = contentY + LINE_H * 2 + SECTION_GAP;

        for (Link link : LINKS) {
            this.addRenderableWidget(StyledButton.of(Component.translatable(link.labelKey()),
                    btn -> openLink(link.url()), x, y, w, LINK_H));
            y += LINK_H + LINK_GAP;
        }
    }

    /**
     * Routes through vanilla's link confirmation rather than opening the browser outright:
     * that screen shows the destination, offers "copy link" for anyone who would rather not
     * be thrown out of the game, and respects the player's link-trust setting.
     */
    private void openLink(String url) {
        this.minecraft.setScreen(new ConfirmLinkScreen(confirmed -> {
            if (confirmed) Util.getPlatform().openUri(url);
            this.minecraft.setScreen(this);
        }, url, false));
    }

    @Override
    protected void renderContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        int centerX = x + w / 2;
        g.drawCenteredString(this.font, Component.translatable("editor.historystages.credits.made_by"),
                centerX, y, SUBTITLE_GREY);
        g.drawCenteredString(this.font, Component.translatable("editor.historystages.credits.authors"),
                centerX, y + LINE_H, AUTHORS_COLOR);
        // The links themselves are widgets and are drawn by super.render().
    }
}
