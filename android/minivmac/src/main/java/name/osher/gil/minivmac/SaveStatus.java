package name.osher.gil.minivmac;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The active emulator snapshot and how much room is left for more. Pure Java:
 * the controller records loads and manual saves here, and the Info → Saves page
 * renders {@link #describe}. Automatic saves count toward storage but never
 * become the active save, so the page names what the player chose.
 */
public final class SaveStatus {
    public enum How { LOADED, SAVED }

    /** Bytes below which the page tells the player to delete old saves now. */
    public static final long LOW_FREE_BYTES = 64L * 1024 * 1024;
    /** Fallback per-save estimate before any snapshot exists. */
    static final long TYPICAL_SAVE_BYTES = 1_200_000L;
    /** Room for the first save's shared reference template, once. */
    static final long REFERENCE_BYTES = 1_200_000L;

    private File active;
    private How how;
    private long when;

    public synchronized void loaded(File file, long at) { record(file, How.LOADED, at); }
    public synchronized void saved(File file, long at) { record(file, How.SAVED, at); }
    private void record(File file, How how, long at) {
        if (file == null) return;
        this.active = file; this.how = how; this.when = at;
    }

    public synchronized File active() { return active; }
    public synchronized How how() { return how; }

    public synchronized String describe(SaveStateStore.Usage usage, long freeBytes) {
        StringBuilder out = new StringBuilder();
        out.append("Active save\n");
        if (active == null) {
            out.append(usage.total() == 0
                    ? "None yet. This is a normal Mac boot; nothing has been saved or loaded."
                    : "None this session. The game is running from a normal Mac boot, not a snapshot.");
        } else {
            out.append(SaveStateStore.displayLabel(active).replace('\n', ' '));
            out.append('\n').append(how == How.LOADED ? "Loaded " : "Saved ")
                    .append(new SimpleDateFormat("h:mm a", Locale.US).format(new Date(when)));
            if (!active.isFile()) out.append("\nThis save has since been deleted or rotated out; the game is still running from it.");
        }
        out.append("\n\nSaves stored\n");
        if (usage.total() == 0) out.append("No saves yet.");
        else {
            out.append(usage.total()).append(usage.total() == 1 ? " save" : " saves")
                    .append(" · ").append(bytes(usage.totalBytes)).append('\n')
                    .append(usage.quick).append(" quick (newest ").append(SaveStateStore.QUICK_KEEP).append(" kept), ")
                    .append(usage.auto).append(" automatic (newest ").append(SaveStateController.AUTO_KEEP).append(" kept), ")
                    .append(usage.named).append(" named (kept until deleted)");
        }
        out.append("\n\nRoom left\n");
        if (freeBytes < 0) out.append("Free space unknown.");
        else {
            out.append(bytes(freeBytes)).append(" free on this device.\n");
            long each = usage.saves() > 0 ? Math.max(1, usage.saveBytes / usage.saves()) : TYPICAL_SAVE_BYTES;
            long spare = freeBytes - LOW_FREE_BYTES - (usage.total() == 0 ? REFERENCE_BYTES : 0);
            long more = spare <= 0 ? 0 : spare / each;
            if (freeBytes < LOW_FREE_BYTES) out.append("Low on space: delete old saves in Load… before saving again.");
            else out.append("Roughly ").append(count(more)).append(" more ").append(more == 1 ? "save" : "saves")
                    .append(" at the current average of ").append(bytes(each)).append(" each.");
        }
        return out.toString();
    }

    static String count(long value) {
        if (value >= 100_000) return "100,000+";
        return String.format(Locale.US, "%,d", value);
    }

    static String bytes(long value) {
        if (value < 0) return "unknown";
        if (value < 1024) return value + " B";
        if (value < 1000L * 1024) return String.format(Locale.US, "%.0f KB", value / 1024.0);   // 1004 KB reads as 1.0 MB
        if (value < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", value / (1024.0 * 1024));
        return String.format(Locale.US, "%.1f GB", value / (1024.0 * 1024 * 1024));
    }
}
