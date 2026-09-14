package name.osher.gil.minivmac;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

/** Shared presentation for offline references and their touch-only pickers. */
public final class UpperHalfReferenceDialog {
    private UpperHalfReferenceDialog() { }

    /** Content must provide its own bounded scrolling; no input method is requested. */
    public static AlertDialog show(Activity activity, String title, View content) {
        return show(activity, title, content, () -> { });
    }

    public static AlertDialog show(Activity activity, String title, View content, Runnable onDismiss) {
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title)
                .setView(content).setNegativeButton("Close", null).create();
        View host = activity.getWindow().getDecorView();
        Runnable resize = () -> {
            if (!dialog.isShowing()) return;
            Window window = dialog.getWindow();
            if (window == null) return;
            Rect visible = new Rect();
            host.getWindowVisibleDisplayFrame(visible);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            window.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
            window.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            window.setLayout(Math.max(1, visible.width()), Math.max(1, visible.height() / 2));
        };
        View.OnLayoutChangeListener listener = (v, l, t, r, b, ol, ot, or, ob) -> resize.run();
        LifecycleEventObserver lifecycle = (owner, event) -> {
            if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss();
        };
        dialog.setOnDismissListener(ignored -> {
            host.removeOnLayoutChangeListener(listener);
            if (activity instanceof LifecycleOwner)
                ((LifecycleOwner) activity).getLifecycle().removeObserver(lifecycle);
            onDismiss.run();
        });
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
        host.addOnLayoutChangeListener(listener);
        if (activity instanceof LifecycleOwner)
            ((LifecycleOwner) activity).getLifecycle().addObserver(lifecycle);
        resize.run();
        return dialog;
    }
}
