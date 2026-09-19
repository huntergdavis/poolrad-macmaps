package name.osher.gil.minivmac;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import name.osher.gil.minivmac.hfs.Blocks;
import name.osher.gil.minivmac.hfs.HfsFile;
import name.osher.gil.minivmac.hfs.HfsVolume;
import name.osher.gil.minivmac.hfs.SaveArchive;
import name.osher.gil.minivmac.hfs.SaveBackupFiles;
import name.osher.gil.minivmac.hfs.SavedParty;

/**
 * Copying the game's own saved games off the guest disk, and putting them back.
 *
 * The disk image is the only copy of these files that exists. Everything else
 * planned for saved games writes to that image, so this comes first: there is
 * no honest way to build a save writer before there is a way to undo it.
 *
 * Restoring is the dangerous direction, and it is fenced accordingly. The
 * emulator caches disk blocks, so writing underneath a disk it has inserted
 * would produce a volume that disagrees with itself; a restore is refused
 * while the disk is in the drive, and says so. The bytes go back where they
 * already were, never anywhere new. And both forks are checked to fit before
 * either is written, so a restore cannot get half way and stop.
 */
public final class SaveBackupController {
    /** Whether the emulator currently has a disk in the drive. */
    public interface DriveState { boolean anyDiskInserted(); }

    /** How a chosen saved game gets loaded: quit, start again, open it. */
    public interface Loader { void load(String folder, String save); }

    /** The game's own application, as the Finder lists it. */
    public static final String APPLICATION = "Pool of Radiance v1.1";

    private final Activity activity;
    private final FileManager files;
    private final DriveState drive;
    private Loader loader;
    private final SaveBackupFiles backups;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    public SaveBackupController(Activity activity, FileManager files, DriveState drive) {
        this.activity = activity;
        this.files = files;
        this.drive = drive;
        this.backups = new SaveBackupFiles(new File(activity.getFilesDir(), "savebackups"));
    }

    public void setLoader(Loader loader) { this.loader = loader; }

    public void dispose() { io.shutdown(); }

    /** One line per saved game found on a disk, with where it came from. */
    private static final class Found {
        final File image; final HfsFile file; final String volume;
        Found(File image, HfsFile file, String volume) { this.image = image; this.file = file; this.volume = volume; }
    }

    public void show() {
        io.execute(() -> {
            List<Found> saves = new ArrayList<>();
            String trouble = null;
            try { saves = findSaves(); }
            catch (IOException failure) { trouble = failure.getMessage(); }
            final List<Found> found = saves;
            final String why = trouble;
            main.post(() -> showMenu(found, why));
        });
    }

    private void showMenu(List<Found> saves, String trouble) {
        if (activity.isFinishing()) return;
        List<File> stored = backups.backups();

        /*
         * The counts belong in the actions, not in a message. An AlertDialog's
         * message and its item list occupy the same space, so setting both
         * showed the summary and hid every action behind it -- a dialog that
         * said what it had found and offered nothing to do about it.
         */
        if (trouble != null) {
            plainly("Could not read the guest disks: " + trouble);
            return;
        }
        if (saves.isEmpty() && stored.isEmpty()) {
            plainly("No saved games were found on the inserted disks, and there are no backups yet.");
            return;
        }

        List<String> choices = new ArrayList<>();
        /*
         * Load is withdrawn (2026-09-19). The sequence restarts the emulated
         * machine to reach a state where the game offers Load, and a restart
         * that did not come back cleanly left the owner's emulator wedged badly
         * enough to need a force-reboot. Backing up and restoring the save
         * files is untouched -- that never restarts anything. The loader
         * plumbing is kept but not offered, so it can be finished under F86
         * without re-adding a menu item by hand.
         */
        // if (!saves.isEmpty() && loader != null) choices.add("Load a saved game\u2026");
        if (!saves.isEmpty()) choices.add("Back up all " + saves.size()
                + (saves.size() == 1 ? " saved game" : " saved games"));
        if (!stored.isEmpty()) choices.add("Restore one of " + stored.size()
                + (stored.size() == 1 ? " backup\u2026" : " backups\u2026"));

        final List<String> options = choices;
        final List<Found> theSaves = saves;
        new AlertDialog.Builder(activity)
                .setTitle("Saved games")
                .setItems(options.toArray(new String[0]), (dialog, which) -> {
                    String chosen = options.get(which);
                    if (chosen.startsWith("Load")) chooseSaveToLoad(theSaves);
                    else if (chosen.startsWith("Back up")) backUp(theSaves);
                    else if (chosen.startsWith("Restore")) chooseBackup();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void plainly(String message) {
        new AlertDialog.Builder(activity)
                .setTitle("Saved games")
                .setMessage(message)
                .setPositiveButton("Close", null)
                .show();
    }

    /**
     * Which save, described by who is in it. A list of file names is no help
     * when they are called F7Healed and F7Injured; a list of parties is.
     */
    private void chooseSaveToLoad(List<Found> saves) {
        List<Found> loadable = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Found save : saves) {
            String who = describeParty(save);
            if (who == null) continue;          // not a saved party; nothing to load
            loadable.add(save);
            labels.add(save.file.name + "\n" + who);
        }
        if (loadable.isEmpty()) { say("No saved parties were found on the guest disk."); return; }
        new AlertDialog.Builder(activity)
                .setTitle("Load which saved game?")
                .setItems(labels.toArray(new String[0]),
                        (dialog, which) -> confirmLoad(loadable.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** "Zarram, Arax the Bold and 4 others - 5/9 HP", or null if it is not a party. */
    private String describeParty(Found save) {
        try (Blocks blocks = SaveBackupFiles.open(save.image, false)) {
            HfsVolume volume = HfsVolume.open(blocks);
            SavedParty party = SavedParty.parse(volume.readResourceFork(save.file));
            StringBuilder text = new StringBuilder();
            int shown = Math.min(2, party.members.size());
            for (int i = 0; i < shown; i++) {
                if (i > 0) text.append(", ");
                text.append(party.members.get(i).name);
            }
            int rest = party.members.size() - shown;
            if (rest > 0) text.append(" and ").append(rest).append(rest == 1 ? " other" : " others");
            int hurt = 0;
            for (SavedParty.Member member : party.members) if (member.currentHp < member.maxHp) hurt++;
            if (hurt > 0) text.append(" \u00b7 ").append(hurt).append(hurt == 1 ? " hurt" : " hurt");
            return text.toString();
        } catch (IOException | RuntimeException notAParty) {
            return null;
        }
    }

    private void confirmLoad(Found save) {
        new AlertDialog.Builder(activity)
                .setTitle("Load " + save.file.name + "?")
                .setMessage("The game has to be closed and started again to load, because it "
                        + "only offers Load Saved Game before a game begins. Anything not saved "
                        + "in the game you are playing now will be lost.")
                .setPositiveButton("Load", (DialogInterface dialog, int which) -> {
                    Loader run = loader;
                    if (run != null) run.load(folderOf(save), save.file.name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** The last part of the folder path, which is what the game's dialog wants. */
    private String folderOf(Found save) {
        String path = SaveBackupFiles.SAVE_FOLDER;
        int colon = path.lastIndexOf(':');
        return colon < 0 ? path : path.substring(colon + 1);
    }

    private void backUp(List<Found> saves) {
        io.execute(() -> {
            String stamp = new SimpleDateFormat("yyyy-MM-dd h.mma", Locale.US).format(new Date())
                    .replace("AM", "am").replace("PM", "pm");
            int done = 0; long bytes = 0; String trouble = null;
            try {
                for (Found save : saves) {
                    try (Blocks blocks = SaveBackupFiles.open(save.image, false)) {
                        HfsVolume volume = HfsVolume.open(blocks);
                        SaveArchive archive = new SaveArchive(save.file.name, save.file.type, save.file.creator,
                                volume.readDataFork(save.file), volume.readResourceFork(save.file));
                        backups.write(archive, stamp);
                        done++; bytes += archive.totalBytes();
                    }
                }
            } catch (IOException | RuntimeException failure) {
                trouble = String.valueOf(failure.getMessage());
            }
            final int saved = done; final long total = bytes; final String why = trouble;
            main.post(() -> say(why != null
                    ? "Backed up " + saved + " before stopping: " + why
                    : "Backed up " + saved + (saved == 1 ? " saved game" : " saved games")
                        + ", " + (total + 1023) / 1024 + " KB, to the app's own storage."));
        });
    }

    private void chooseBackup() {
        List<File> stored = backups.backups();
        if (stored.isEmpty()) { say("There are no backups to restore."); return; }
        String[] labels = new String[stored.size()];
        for (int i = 0; i < stored.size(); i++)
            labels[i] = stored.get(i).getName().replace(SaveBackupFiles.EXTENSION, "");
        new AlertDialog.Builder(activity)
                .setTitle("Restore which backup?")
                .setItems(labels, (dialog, which) -> confirmRestore(stored.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmRestore(File backup) {
        if (drive != null && drive.anyDiskInserted()) {
            /*
             * The emulator caches disk blocks. Writing underneath an inserted
             * disk gives a volume that disagrees with itself, which is a much
             * worse outcome than an inconvenient refusal.
             */
            say("Eject the game disk first. Restoring into a disk the emulator has "
                    + "in the drive would leave it inconsistent.");
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Restore " + backup.getName().replace(SaveBackupFiles.EXTENSION, "") + "?")
                .setMessage("This replaces the saved game of the same name on the guest disk. "
                        + "The bytes go back exactly where they were; nothing else on the disk is touched.")
                .setPositiveButton("Restore", (DialogInterface dialog, int which) -> restore(backup))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restore(File backup) {
        io.execute(() -> {
            String outcome;
            try {
                SaveArchive archive = backups.read(backup);
                outcome = putBack(archive);
            } catch (IOException | RuntimeException failure) {
                outcome = "Nothing was changed: " + failure.getMessage();
            }
            final String said = outcome;
            main.post(() -> say(said));
        });
    }

    private String putBack(SaveArchive archive) throws IOException {
        for (File image : diskImages()) {
            try (Blocks blocks = SaveBackupFiles.open(image, true)) {
                HfsVolume volume = HfsVolume.open(blocks);
                int folder = volume.folderAt(SaveBackupFiles.SAVE_FOLDER);
                if (folder < 0) continue;
                for (HfsFile file : volume.files(folder)) {
                    if (!file.name.equalsIgnoreCase(archive.name)) continue;
                    // Both forks checked before either is written, so a restore
                    // cannot get half way and stop.
                    if (!volume.fits(file, archive.data, archive.resource))
                        return "Nothing was changed: " + archive.name
                                + " no longer has room for this backup on the disk.";
                    volume.writeDataForkInPlace(file, archive.data);
                    volume.writeResourceForkInPlace(file, archive.resource);
                    return "Restored " + archive.name + " on " + volume.volumeName() + ".";
                }
            } catch (IOException ignored) {
                // Not every disk is an HFS volume with this game on it.
            }
        }
        return "Nothing was changed: no disk has a saved game called " + archive.name + ".";
    }

    private List<Found> findSaves() throws IOException {
        List<Found> saves = new ArrayList<>();
        for (File image : diskImages()) {
            try (Blocks blocks = SaveBackupFiles.open(image, false)) {
                HfsVolume volume = HfsVolume.open(blocks);
                int folder = volume.folderAt(SaveBackupFiles.SAVE_FOLDER);
                if (folder < 0) continue;
                for (HfsFile file : volume.files(folder)) saves.add(new Found(image, file, volume.volumeName()));
            } catch (IOException ignored) {
                // A disk that is not this game's is simply not this game's.
            }
        }
        return saves;
    }

    private List<File> diskImages() {
        List<File> images = new ArrayList<>();
        File[] found = files.getDisksDir().listFiles();
        if (found != null) for (File file : found) if (file.isFile() && file.length() > 1024) images.add(file);
        return images;
    }

    private void say(String message) {
        if (!activity.isFinishing()) Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
    }
}
