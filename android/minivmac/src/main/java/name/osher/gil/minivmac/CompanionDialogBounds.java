package name.osher.gil.minivmac;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;

/** Reuses UpperHalfReferenceDialog's quiet presentation, within the real companion. */
final class CompanionDialogBounds {
    private CompanionDialogBounds() { }

    /** Configure before show, so the initial window is not a full-height guest overlay. */
    static boolean prepare(Activity activity, Dialog dialog) {
        // Builder.create() constructs the object but does not run Dialog.onCreate.
        // Force that one-time theme/content installation BEFORE overriding its
        // animations and bounds; otherwise the first show restores AppCompat's
        // animation style. Dialog.create() is idempotent and does not show a window.
        // https://developer.android.com/reference/android/app/Dialog#create()
        dialog.create();
        Window window = dialog.getWindow();
        if (window == null) return false;
        window.setWindowAnimations(0);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        // The page sits above the guest, so the guest must stay usable while it
        // is open: a touch outside the dialog goes to the game below instead of
        // dying at a modal boundary. Keys still go to the page; Close it, or
        // tap outside, and the game has the screen back as before.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        dialog.setCanceledOnTouchOutside(false);
        return position(activity, dialog, false, null);
    }

    static Binding track(Activity activity, Dialog dialog) {
        Binding binding = new Binding(activity, dialog);
        binding.start();
        return binding;
    }

    private static CompanionGeometry.Bounds bounds(Rect rect) {
        return new CompanionGeometry.Bounds(rect.left, rect.top, rect.right, rect.bottom);
    }

    private static boolean position(Activity activity, Dialog dialog, boolean requireCompanion,
                                    Binding binding) {
        View host = activity.getWindow().getDecorView();
        View companion = activity.findViewById(R.id.companion_pane);
        Rect visible = new Rect(); host.getWindowVisibleDisplayFrame(visible);
        Rect pane = null;
        if (companion != null && companion.isShown() && companion.isAttachedToWindow()) {
            // getGlobalVisibleRect is ROOT-relative; getLocationOnScreen includes the
            // real activity offset (including split screen). Never assume a toolbar size.
            // https://developer.android.com/reference/android/view/View#getGlobalVisibleRect(android.graphics.Rect)
            pane = new Rect();
            if (companion.getGlobalVisibleRect(pane)) {
                int[] rootLocation = new int[2];
                companion.getRootView().getLocationOnScreen(rootLocation);
                pane.offset(rootLocation[0], rootLocation[1]);
            } else pane = null;
        }
        CompanionGeometry.Bounds target = CompanionGeometry.dialog(bounds(visible),
                requireCompanion || companion != null, pane == null ? null : bounds(pane));
        if (target == null || activity.isFinishing() || activity.isDestroyed()) return false;
        Window window = dialog.getWindow();
        if (window == null) return false;
        WindowManager.LayoutParams params = window.getAttributes();
        // TOP/LEFT offsets are relative to the containing window frame, not screen
        // coordinates. Start from the host frame, then measure the actual dialog
        // origin once attached; this also handles platform inset/cutout differences.
        int frameLeft = visible.left, frameTop = visible.top;
        View decor = window.getDecorView();
        if (binding != null && decor.isLaidOut() && decor.isAttachedToWindow()
                && binding.applied && !binding.awaitingLayout) {
            int[] location = new int[2]; decor.getLocationOnScreen(location);
            frameLeft = location[0] - params.x;
            frameTop = location[1] - params.y;
        } else if (binding != null && binding.awaitingLayout) {
            frameLeft = binding.frameLeft; frameTop = binding.frameTop;
        }
        int x = target.left - frameLeft, y = target.top - frameTop;
        int gravity = Gravity.TOP | Gravity.LEFT;
        if (params.x != x || params.y != y || params.width != target.width()
                || params.height != target.height() || params.gravity != gravity) {
            params.gravity = gravity; params.x = x; params.y = y;
            params.width = target.width(); params.height = target.height();
            params.horizontalMargin = 0; params.verticalMargin = 0;
            window.setAttributes(params);
            if (binding != null) binding.awaitingLayout = true;
        }
        if (binding != null) {
            binding.frameLeft = frameLeft; binding.frameTop = frameTop; binding.applied = true;
        }
        return true;
    }

    /** Caller owns dismiss callbacks; close on dismiss/stop, and track again on start. */
    static final class Binding implements AutoCloseable {
        private final Activity activity;
        private final Dialog dialog;
        private final View host, companion, decor;
        private final boolean requireCompanion;
        private boolean closed, applied, awaitingLayout;
        private int frameLeft, frameTop;
        private ViewTreeObserver observer, dialogObserver;
        private final View.OnLayoutChangeListener hostLayout = (v, l, t, r, b, ol, ot, or, ob) -> resize();
        private final View.OnLayoutChangeListener dialogLayout = (v, l, t, r, b, ol, ot, or, ob) -> {
            awaitingLayout = false; resize();
        };
        private final ViewTreeObserver.OnGlobalLayoutListener globalLayout = this::resize;
        private final ViewTreeObserver.OnPreDrawListener beforeDraw = () -> {
            // A window can move without changing its decor's local layout bounds.
            // Reconcile that measured screen origin before drawing, not one frame later.
            awaitingLayout = false;
            resize();
            return !awaitingLayout && !closed;
        };

        private Binding(Activity activity, Dialog dialog) {
            this.activity = activity; this.dialog = dialog;
            host = activity.getWindow().getDecorView();
            companion = activity.findViewById(R.id.companion_pane);
            requireCompanion = companion != null;
            decor = dialog.getWindow() == null ? null : dialog.getWindow().getDecorView();
        }

        private void start() {
            host.addOnLayoutChangeListener(hostLayout);
            if (companion != null) companion.addOnLayoutChangeListener(hostLayout);
            if (decor != null) decor.addOnLayoutChangeListener(dialogLayout);
            if (decor != null) {
                dialogObserver = decor.getViewTreeObserver();
                dialogObserver.addOnPreDrawListener(beforeDraw);
            }
            observer = host.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(globalLayout);
            resize();
        }

        void resize() {
            if (closed) return;
            if (!dialog.isShowing()) { close(); return; }
            if (!position(activity, dialog, requireCompanion, this)) {
                close(); dialog.dismiss();
            }
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            host.removeOnLayoutChangeListener(hostLayout);
            if (companion != null) companion.removeOnLayoutChangeListener(hostLayout);
            if (decor != null) decor.removeOnLayoutChangeListener(dialogLayout);
            if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(globalLayout);
            if (dialogObserver != null && dialogObserver.isAlive()) dialogObserver.removeOnPreDrawListener(beforeDraw);
            observer = null; dialogObserver = null;
        }
    }
}
