package net.bananemdnsa.historystages.client.editor.widget.dialog;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modal dialog built from a declarative list of {@link InputField} specs: builds the
 * EditBoxes, lays them out, runs validation live, and shows the first error above the
 * button row.
 *
 * <p>Errors stay hidden until the user has typed in a field or pressed confirm once, so
 * opening a dialog with an empty required field does not greet the user with an error.
 *
 * <p>Dialogs with interactive extras (dropdowns, suggestion lists) use the
 * {@code extraContent*} hooks, which receive input as well as render calls — mirroring
 * {@code AbstractSearchableList}'s top-inset hooks.
 */
public abstract class AbstractInputScreen extends AbstractModalScreen {

    protected static final int LABEL_GREY = 0x999999;
    protected static final int FIELD_BG = 0xFF0D0D0D;
    protected static final int FIELD_BORDER = 0xFF4A4A4A;
    protected static final int ERROR_RED = 0xFF5555;

    protected static final int FIELD_H = 20;
    protected static final int LABEL_H = 10;
    protected static final int FIELD_GAP = 8;
    protected static final int ERROR_H = 12;
    /** Font line height — the EditBox is sized to its text, not to the drawn field. */
    private static final int TEXT_H = 8;
    /**
     * Half the error band, reserved above the fields as well as below them. The band is empty
     * most of the time, and reserving it only below would bias every dialog's fields upward.
     */
    private static final int ERROR_BALANCE = (FIELD_GAP + ERROR_H) / 2;
    /** Vertical offset that centres {@link #TEXT_H} text inside a {@link #FIELD_H} field. */
    private static final int TEXT_INSET_Y = (FIELD_H - TEXT_H) / 2;

    private final List<InputField> specs = new ArrayList<>();
    private final List<EditBox> boxes = new ArrayList<>();
    private final List<Boolean> touched = new ArrayList<>();
    /** Drawn field bounds per slot as {x, y, width}, so clicks can be mapped to the box. */
    private final List<int[]> fieldRects = new ArrayList<>();

    private boolean confirmAttempted = false;
    private Component currentError = null;
    /** Remembered focus slot, needed while an extra (non-EditBox) slot holds focus. */
    private int focusSlot = 0;

    protected AbstractInputScreen(Screen parent, Component title) {
        super(parent, title);
    }

    // ============ Extension points ============

    /** The fields, in tab order. Called once during {@link #init}. */
    protected abstract List<InputField> fields();

    /** Invoked with the trimmed, validated values. Implementations close the screen themselves. */
    protected abstract void onConfirm(InputValues values);

    /** Extra vertical space reserved below the fields for {@link #renderExtraContent}. */
    protected int extraContentHeight() {
        return 0;
    }

    protected void renderExtraContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
    }

    /**
     * @param button GLFW mouse button; 0 is left. Implementations must check it — a dropdown
     *               that ignores it would open on right-click.
     * @return true to consume the click.
     */
    protected boolean extraContentMouseClicked(double mx, double my, int button) {
        return false;
    }

    /** @return true to consume the scroll. */
    protected boolean extraContentMouseScrolled(double mx, double my, double scrollY) {
        return false;
    }

    /** @return true to consume the drag. Needed by extras with a draggable scrollbar. */
    protected boolean extraContentMouseDragged(double mx, double my, int button) {
        return false;
    }

    /** @return true to consume the release. */
    protected boolean extraContentMouseReleased(double mx, double my, int button) {
        return false;
    }

    /** @return true to consume the key. */
    protected boolean extraContentKeyPressed(int keyCode) {
        return false;
    }

    /**
     * Number of focusable slots. Defaults to the field count; override when the dialog has
     * a non-EditBox element in the tab order (e.g. a dropdown) and return
     * {@code fieldCount() + extras}.
     */
    protected int focusableCount() {
        return specs.size();
    }

    /**
     * Called when the focus index lands on a slot beyond the EditBoxes. Use to focus a
     * non-EditBox element. Default no-op.
     */
    protected void onExtraFocused(int slot) {
    }

    // ============ Layout ============

    private int fieldBlockHeight(InputField f) {
        return (f.label() != null ? LABEL_H : 0) + FIELD_H;
    }

    @Override
    protected final int contentHeight() {
        int h = ERROR_BALANCE;
        for (InputField f : specs) {
            h += fieldBlockHeight(f) + FIELD_GAP;
        }
        h += extraContentHeight();
        h += ERROR_H;
        return h;
    }

    @Override
    protected void init() {
        // fields() must be resolved before contentHeight() runs inside super.init()
        specs.clear();
        specs.addAll(fields());
        touched.clear();
        for (int i = 0; i < specs.size(); i++) touched.add(false);
        super.init();
    }

    @Override
    protected void buildContentWidgets() {
        boxes.clear();
        fieldRects.clear();
        int y = contentY + ERROR_BALANCE;
        int contentX = boxX + PAD;
        int contentW = boxW - PAD * 2;

        for (int i = 0; i < specs.size(); i++) {
            InputField f = specs.get(i);
            int fw = f.width() > 0 ? Math.min(f.width(), contentW) : contentW;
            int fx = contentX + (contentW - fw) / 2;
            int fy = y + (f.label() != null ? LABEL_H : 0);
            fieldRects.add(new int[]{fx, fy, fw});

            // bordered=false: the frame is drawn in renderContent, focus-aware. The box is only
            // as tall as its text and sits centred in the drawn field, so its own hit rect never
            // spills into the gap below; mouseClicked maps the full drawn field onto it.
            EditBox box = new EditBox(this.font, fx + 4, fy + TEXT_INSET_Y, fw - 8, TEXT_H,
                    f.label() != null ? f.label() : Component.literal(f.key()));
            box.setMaxLength(f.maxLength());
            box.setBordered(false);
            box.setTextColor(0xFFFFFF);
            box.setValue(f.initial());
            box.moveCursorToEnd(false);
            if (f.hint() != null) box.setHint(f.hint());

            final InputField spec = f;
            final int idx = i;
            box.setFilter(spec::acceptsTyping);
            box.setResponder(v -> {
                touched.set(idx, true);
                revalidate();
            });

            this.addRenderableWidget(box);
            boxes.add(box);
            y += fieldBlockHeight(f) + FIELD_GAP;
        }

        if (!boxes.isEmpty()) {
            boxes.get(0).setFocused(true);
            this.setFocused(boxes.get(0));
        }
        revalidate();
    }

    // ============ Validation ============

    /** Recomputes {@link #currentError} from the first field that fails and is visible. */
    private void revalidate() {
        currentError = null;
        for (int i = 0; i < specs.size(); i++) {
            Component err = specs.get(i).validate(boxes.get(i).getValue());
            if (err != null) {
                if (touched.get(i) || confirmAttempted) currentError = err;
                // First failing field wins, whether or not it is shown yet.
                return;
            }
        }
    }

    /** True when every field validates, regardless of whether errors are being shown. */
    private boolean allValid() {
        for (int i = 0; i < specs.size(); i++) {
            if (specs.get(i).validate(boxes.get(i).getValue()) != null) return false;
        }
        return true;
    }

    @Override
    protected boolean canConfirm() {
        return true; // The button stays clickable; onConfirm gates and surfaces the error.
    }

    @Override
    protected final void onConfirm() {
        confirmAttempted = true;
        revalidate();
        if (!allValid()) return; // the error line now explains why nothing happened

        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < specs.size(); i++) {
            values.put(specs.get(i).key(), boxes.get(i).getValue().trim());
        }
        onConfirm(new InputValues(values));
    }

    /** Access to a live box, for subclasses that need the raw value (e.g. suggestion filtering). */
    protected EditBox box(int index) {
        return boxes.get(index);
    }

    protected int fieldCount() {
        return boxes.size();
    }

    /**
     * Moves focus onto an extra (non-EditBox) slot, unfocusing every field. Call this when the
     * user activates such an element by mouse — tabbing to it routes through {@code cycleFocus},
     * but a click otherwise leaves focus on whatever field had it, and keys would go there.
     */
    protected void focusExtraSlot(int slot) {
        focusSlot = slot;
        for (EditBox b : boxes) b.setFocused(false);
        this.setFocused(null);
    }

    // ============ Rendering ============

    @Override
    protected void renderContent(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        // Must match the cursor buildContentWidgets() used, or frames and hit areas drift apart.
        int cy = y + ERROR_BALANCE;
        for (int i = 0; i < specs.size(); i++) {
            InputField f = specs.get(i);
            EditBox box = boxes.get(i);
            int fw = f.width() > 0 ? Math.min(f.width(), w) : w;
            int fx = x + (w - fw) / 2;
            int fy = cy + (f.label() != null ? LABEL_H : 0);

            if (f.label() != null) {
                g.drawString(this.font, f.label(), fx, cy, LABEL_GREY, false);
            }

            int border = box.isFocused() ? ACCENT_GOLD : FIELD_BORDER;
            g.fill(fx - 1, fy - 1, fx + fw + 1, fy + FIELD_H + 1, border);
            g.fill(fx, fy, fx + fw, fy + FIELD_H, FIELD_BG);

            cy += fieldBlockHeight(f) + FIELD_GAP;
        }

        // Always invoked, even at zero reserved height: an extra may anchor itself elsewhere
        // (the stage-type dropdown sits on the title row) and still needs to be drawn. The
        // default implementation does nothing, so this costs unhooked subclasses nothing.
        renderExtraContent(g, x, cy, w, mouseX, mouseY);
        cy += extraContentHeight();

        if (currentError != null) {
            g.drawCenteredString(this.font, currentError, x + w / 2, cy + 2, ERROR_RED);
        }
    }

    // ============ Input ============

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Swallowed while the dialog is still scaling in — see isOpenSettled().
        if (!isOpenSettled()) return true;
        if (extraContentMouseClicked(mx, my, button)) return true;
        if (button == 0 && focusFieldAt(mx, my, button)) return true;
        return super.mouseClicked(mx, my, button);
    }

    /**
     * Maps a click anywhere on a drawn field onto its box. The box is only as tall as its text,
     * so without this the padding around the text would look clickable but do nothing.
     *
     * @return true if a field was hit.
     */
    private boolean focusFieldAt(double mx, double my, int button) {
        for (int i = 0; i < fieldRects.size(); i++) {
            int[] r = fieldRects.get(i);
            if (mx < r[0] || mx >= r[0] + r[2] || my < r[1] || my >= r[1] + FIELD_H) continue;
            focusSlot = i;
            for (EditBox b : boxes) b.setFocused(false);
            EditBox box = boxes.get(i);
            box.setFocused(true);
            this.setFocused(box);
            // Forward at the box's own height so the caret lands under the cursor's x.
            box.mouseClicked(mx, box.getY() + TEXT_H / 2.0, button);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (extraContentMouseScrolled(mx, my, scrollY)) return true;
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (extraContentMouseDragged(mx, my, button)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (extraContentMouseReleased(mx, my, button)) return true;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (extraContentKeyPressed(keyCode)) return true;

        if (keyCode == 258) { // TAB
            cycleFocus(hasShiftDown() ? -1 : 1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * The slot that currently has focus, which may be an extra slot beyond the EditBoxes.
     *
     * <p>A focused EditBox is authoritative — clicking one moves focus without going through
     * {@link #cycleFocus}. Only when no box is focused does the remembered slot apply, which
     * is exactly the case where an extra slot holds focus: subclasses need this to draw a
     * focus ring on a dropdown, since a dropdown has no {@code isFocused()} of its own.
     */
    protected int focusedSlot() {
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).isFocused()) {
                focusSlot = i;
                return i;
            }
        }
        return focusSlot >= boxes.size() ? focusSlot : -1;
    }

    private void cycleFocus(int delta) {
        int count = focusableCount();
        if (count <= 1) return;
        int cur = focusedSlot();
        if (cur < 0) cur = 0;
        int next = ((cur + delta) % count + count) % count;

        focusSlot = next;
        for (EditBox b : boxes) b.setFocused(false);
        if (next < boxes.size()) {
            boxes.get(next).setFocused(true);
            this.setFocused(boxes.get(next));
        } else {
            this.setFocused(null);
            onExtraFocused(next);
        }
    }
}
