package net.bananemdnsa.historystages.client.toast;

/**
 * Implemented by this mod's toasts so a click can dismiss them. {@code ToastClickHandler}
 * hit-tests the visible toasts and calls {@link #dismiss()} on the one under the cursor; the
 * toast's {@code render} then reports {@code Visibility.HIDE}, letting vanilla play its slide-out.
 */
public interface DismissibleToast {
    void dismiss();
}
