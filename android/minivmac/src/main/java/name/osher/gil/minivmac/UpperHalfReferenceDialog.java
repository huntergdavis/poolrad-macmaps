package name.osher.gil.minivmac;

import android.app.Activity;
import android.view.View;
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
        return present(activity, dialog, onDismiss);
    }

    /** The flag editor supplies its own single-row title/tools/Close control. */
    public static AlertDialog showEditor(Activity activity, View content, Runnable onDismiss) {
        return present(activity, new AlertDialog.Builder(activity).setView(content).create(), onDismiss);
    }

    private static AlertDialog present(Activity activity, AlertDialog dialog, Runnable onDismiss) {
        CompanionDialogBounds.Binding[] binding = new CompanionDialogBounds.Binding[1];
        LifecycleEventObserver lifecycle = (owner, event) -> {
            if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss();
        };
        dialog.setOnDismissListener(ignored -> {
            if (binding[0] != null) binding[0].close();
            if (activity instanceof LifecycleOwner)
                ((LifecycleOwner) activity).getLifecycle().removeObserver(lifecycle);
            onDismiss.run();
        });
        // Build the buttons even on safe failure: existing callers customize the
        // returned dialog. A hidden companion never opens a window over the guest.
        dialog.create();
        if (!CompanionDialogBounds.prepare(activity, dialog)) {
            onDismiss.run();
            return dialog;
        }
        dialog.show();
        binding[0] = CompanionDialogBounds.track(activity, dialog);
        if (activity instanceof LifecycleOwner)
            ((LifecycleOwner) activity).getLifecycle().addObserver(lifecycle);
        return dialog;
    }
}
