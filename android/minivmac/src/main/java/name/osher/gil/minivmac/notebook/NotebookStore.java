package name.osher.gil.minivmac.notebook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.zip.CRC32;
import name.osher.gil.minivmac.journal.JournalHistory;

/** App-private user ink only: this class never opens an emulator disk or save. */
public final class NotebookStore {
    private static final int BOOK_VERSION = 1;
    private static final int INK_VERSION = 2;
    private static final int BOOK_MAGIC = 0x50524e42; // PRNB
    private static final int INK_MAGIC = 0x50524e49; // PRNI
    private static final int EXPLORATION_MAGIC = 0x50524558; // PREX
    private static final int JOURNAL_MAGIC = 0x50524e4a; // PRNJ
    static final int MAX_EXPLORATION_BYTES = 1024;
    static final int MAX_JOURNAL_BYTES = JournalHistory.MAX_BYTES;
    private static final int MAX_NOTE_BYTES = InkNote.MAX_TOTAL_POINTS * 8
            + InkNote.MAX_STROKES * 9 + 1024;
    private static final int MAX_NOTEBOOKS = 256;
    private final File root;

    public NotebookStore(File root) {
        if (root == null) throw new IllegalArgumentException("Missing notebook folder");
        this.root = root;
    }

    public static final class Notebook {
        private final String id;
        private final String label;
        private Notebook(String id, String label) { this.id = id; this.label = label; }
        public String id() { return id; }
        public String label() { return label; }
        @Override public String toString() { return label; }
    }

    public synchronized Notebook createNotebook() throws IOException {
        List<Notebook> existing = listNotebooks();
        if (existing.size() >= MAX_NOTEBOOKS) throw new IOException("Notebook limit reached");
        Notebook book = new Notebook(UUID.randomUUID().toString(), nextLabel(existing));
        File directory = new File(root, book.id);
        if (!directory.mkdir()) throw new IOException("Cannot create notebook folder");
        try {
            writeNotebook(directory, book);
        } catch (IOException failure) {
            // An empty failed create can be removed; never remove existing user files.
            directory.delete();
            throw failure;
        }
        return book;
    }

    public synchronized List<Notebook> listNotebooks() throws IOException {
        directory(root, true);
        File[] children = root.listFiles();
        if (children == null) throw new IOException("Cannot read notebook folder");
        List<Notebook> books = new ArrayList<>();
        for (File child : children) {
            if (!validId(child.getName())) throw new IOException("Unrecognized notebook record");
            books.add(readNotebook(child.getName()));
            if (books.size() > MAX_NOTEBOOKS) throw new IOException("Too many notebook records");
        }
        Collections.sort(books, (left, right) -> Integer.compare(
                labelNumber(left.label), labelNumber(right.label)));
        return Collections.unmodifiableList(books);
    }

    /** Complete, versioned backup; caller owns and closes its document stream. */
    public synchronized void exportNotebook(String id, OutputStream out) throws IOException {
        List<NotebookArchive.Entry> entries = archiveEntries(id);
        NotebookArchive.write(id, entries, out);
    }

    /**
     * Restore without changing any existing notebook. The original UUID survives;
     * a colliding display label gets the next local label. Never merges notebooks.
     * Only a completely verified staged directory is published to the live root.
     */
    public synchronized Notebook importNotebook(InputStream in) throws IOException {
        if (in == null) throw new IllegalArgumentException("Missing backup source");
        List<Notebook> existing = listNotebooks();
        if (existing.size() >= MAX_NOTEBOOKS) throw new IOException("Notebook limit reached");
        File staging = newStagingFolder();
        try {
            String id = NotebookArchive.read(in, staging);
            NotebookStore stagedStore = new NotebookStore(staging);
            stagedStore.archiveEntries(id); // Validates identity, all notes, icons and v1 backups.
            Notebook book = stagedStore.readNotebook(id);
            File destination = new File(root, id);
            if (destination.exists()) throw new IOException("This notebook already exists. Export it before removing it to restore a backup.");
            // Recheck after the stream read: an external document provider may take time.
            existing = listNotebooks();
            if (existing.size() >= MAX_NOTEBOOKS) throw new IOException("Notebook limit reached");
            boolean labelTaken = false;
            for (Notebook other : existing) {
                if (other.id.equals(id)) throw new IOException("This notebook already exists");
                if (other.label.equals(book.label)) labelTaken = true;
            }
            File imported = new File(staging, id);
            if (labelTaken) {
                book = new Notebook(id, nextLabel(existing));
                writeNotebook(imported, book);
            }
            if (destination.exists() || !imported.renameTo(destination)) {
                throw new IOException("Cannot publish notebook atomically; existing notebooks were not replaced");
            }
            return book;
        } finally {
            cleanStaging(staging);
        }
    }

    /**
     * Call only after user confirmation. Validation and a whole-directory rename
     * precede cleanup, so a failed removal never leaves half a visible notebook.
     * After the rename removal is committed; failed cleanup may leave private
     * retired files outside the notebook list, not a partially visible notebook.
     */
    public synchronized void deleteNotebook(String id) throws IOException {
        archiveEntries(id);
        File staging = newStagingFolder();
        try {
            if (!new File(root, id).renameTo(new File(staging, id))) {
                throw new IOException("Cannot remove notebook atomically; no notes were removed");
            }
        } finally {
            cleanStaging(staging);
        }
    }

    private List<NotebookArchive.Entry> archiveEntries(String id) throws IOException {
        readNotebook(id);
        File book = new File(root, id);
        requireDirectChild(root, book);
        List<NotebookArchive.Entry> entries = new ArrayList<>();
        int filesSeen = 0;
        for (File child : children(book)) {
            requireDirectChild(book, child);
            String name = child.getName();
            if (name.startsWith(".pending-")) {
                requirePendingFile(child); continue;
            }
            if (name.equals("notebook.bin")) {
                entries.add(new NotebookArchive.Entry(name, child)); continue;
            }
            if (name.equals("journal.bin")) {
                readJournal(child, id); // Refuse to back up a record we cannot read back.
                entries.add(new NotebookArchive.Entry(name, child)); continue;
            }
            if (!NotebookArchive.area(name) || !child.isDirectory()) {
                throw new IOException("Unrecognized notebook content; backup or removal refused");
            }
            for (File file : children(child)) {
                if (++filesSeen > NotebookArchive.MAX_ENTRIES) throw new IOException("Too many notebook files");
                requireDirectChild(child, file);
                String filename = file.getName();
                if (filename.startsWith(".pending-")) {
                    requirePendingFile(file); continue;
                }
                NotebookArchive.Entry entry = new NotebookArchive.Entry(name + "/" + filename, file);
                if (filename.equals("exploration.bin")) {
                    readExploration(file, id, name);
                } else if (!filename.equals("map.ink")) {
                    int tile = Integer.parseInt(filename.substring(0, filename.indexOf('.')));
                    StoredNote stored = readInk(file, id, name, tile % 16, tile / 16);
                    if (filename.endsWith(".v1") && stored.version != 1) {
                        throw new IOException("Invalid legacy notebook backup");
                    }
                }
                entries.add(entry);
            }
        }
        if (entries.size() > NotebookArchive.MAX_ENTRIES) throw new IOException("Too many notebook entries");
        Collections.sort(entries, (left, right) -> left.path.compareTo(right.path));
        return entries;
    }

    private static void requirePendingFile(File file) throws IOException {
        if (!file.isFile() || file.length() > NotebookArchive.MAX_ENTRY_BYTES) {
            throw new IOException("Unrecognized incomplete notebook write");
        }
    }

    private static File[] children(File parent) throws IOException {
        File[] files = parent.listFiles();
        if (files == null) throw new IOException("Cannot read notebook content");
        return files;
    }

    private static void requireDirectChild(File parent, File child) throws IOException {
        if (!child.getCanonicalFile().equals(new File(parent.getCanonicalFile(), child.getName()))) {
            throw new IOException("Notebook content must not contain linked paths");
        }
    }

    private File newStagingFolder() throws IOException {
        directory(root, true);
        File parent = root.getCanonicalFile().getParentFile();
        if (parent == null) throw new IOException("Notebook storage has no safe staging location");
        File staging = File.createTempFile(".poolrad-notebook-", ".stage", parent);
        if (!staging.delete() || !staging.mkdir()) throw new IOException("Cannot create notebook staging folder");
        return staging;
    }

    /** Only our freshly allocated three-level stage, never a caller-supplied tree. */
    private static void cleanStaging(File staging) {
        try {
            for (File book : children(staging)) {
                requireDirectChild(staging, book);
                if (!validId(book.getName()) || !book.isDirectory()) continue;
                for (File child : children(book)) {
                    requireDirectChild(book, child);
                    if (child.isFile()) {
                        String name = child.getName();
                        if (name.equals("notebook.bin") || name.equals("journal.bin")
                                || name.startsWith(".pending-")) child.delete();
                    } else if (NotebookArchive.area(child.getName()) && child.isDirectory()) {
                        for (File file : children(child)) {
                            requireDirectChild(child, file);
                            if (file.isFile()) file.delete();
                        }
                        child.delete();
                    }
                }
                book.delete();
            }
            staging.delete();
        } catch (IOException | SecurityException ignored) {
            // Before publication this is private uncommitted input; after
            // removal the whole notebook is already retired. Never undo either
            // commit or touch a linked/unexpected path to force cleanup.
        }
    }

    private static String nextLabel(List<Notebook> existing) throws IOException {
        boolean[] used = new boolean[MAX_NOTEBOOKS + 1];
        int next = 1;
        for (Notebook book : existing) {
            int number = labelNumber(book.label);
            used[number] = true; next = Math.max(next, number + 1);
        }
        if (next <= MAX_NOTEBOOKS) return "Notebook " + next;
        for (int i = 1; i <= MAX_NOTEBOOKS; i++) if (!used[i]) return "Notebook " + i;
        throw new IOException("Notebook label limit reached");
    }

    private static void writeNotebook(File directory, Notebook book) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(book.id); out.writeUTF(book.label);
        }
        writeAtomic(new File(directory, "notebook.bin"), BOOK_MAGIC, BOOK_VERSION, bytes.toByteArray());
    }

    /** A missing record means nothing has been observed; malformed records never become empty. */
    public synchronized ExplorationTrail loadExploration(String notebookId, String areaId) throws IOException {
        File file = explorationFile(notebookId, areaId, false);
        return file.exists() ? readExploration(file, notebookId, areaId) : ExplorationTrail.empty();
    }

    /** Atomic app-private companion history; does not modify any guest disk or game save. */
    public synchronized void saveExploration(String notebookId, String areaId, ExplorationTrail trail) throws IOException {
        if (trail == null) throw new IllegalArgumentException("Missing exploration history");
        File file = explorationFile(notebookId, areaId, true);
        if (file.exists()) readExploration(file, notebookId, areaId); // Preserve unreadable or future data.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(notebookId); out.writeUTF(areaId);
            out.write(trail.copyVisited()); out.writeShort(trail.steps.size());
            for (ExplorationTrail.Step step : trail.steps) {
                out.writeShort(step.from); out.writeByte(step.to);
            }
        }
        writeAtomic(file, EXPLORATION_MAGIC, 1, bytes.toByteArray());
    }

    /**
     * Journal lookups, bookmarks, checked tasks and flag links for one notebook.
     * A missing record means nothing has been looked up yet; a damaged record is
     * an error rather than a silent empty history.
     */
    public synchronized JournalHistory loadJournal(String notebookId) throws IOException {
        readNotebook(notebookId);
        File file = journalFile(notebookId);
        return file.exists() ? readJournal(file, notebookId) : new JournalHistory();
    }

    /** Atomic app-private companion history; touches no guest disk or game save. */
    public synchronized void saveJournal(String notebookId, JournalHistory history) throws IOException {
        if (history == null) throw new IllegalArgumentException("Missing journal history");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) { history.write(out); }
        saveJournal(notebookId, bytes.toByteArray());
    }

    /**
     * Takes an already-encoded history so the caller can serialize on its own
     * thread; a live history must never be walked while the UI is mutating it.
     */
    public synchronized void saveJournal(String notebookId, byte[] history) throws IOException {
        if (history == null) throw new IllegalArgumentException("Missing journal history");
        readNotebook(notebookId);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(notebookId); out.write(history);
        }
        if (bytes.size() > MAX_JOURNAL_BYTES) throw new IOException("Journal history is too large to save");
        writeAtomic(journalFile(notebookId), JOURNAL_MAGIC, 1, bytes.toByteArray());
    }

    private File journalFile(String notebookId) throws IOException {
        requireId(notebookId);
        File parent = new File(root, notebookId);
        requireDirectChild(root, parent);
        File file = new File(parent, "journal.bin");
        requireDirectChild(parent, file);
        return file;
    }

    private static JournalHistory readJournal(File file, String notebookId) throws IOException {
        Envelope record = readEnvelope(file, JOURNAL_MAGIC, 1, MAX_JOURNAL_BYTES);
        try (DataInputStream in = record.input()) {
            if (!notebookId.equals(in.readUTF()))
                throw new IOException("Journal history does not match its notebook");
            return JournalHistory.read(in);
        }
    }

    private File explorationFile(String notebookId, String areaId, boolean create) throws IOException {
        File parent = areaDirectory(notebookId, areaId, false);
        File book = new File(root, notebookId);
        requireDirectChild(root, book); requireDirectChild(book, parent);
        File file = new File(parent, "exploration.bin");
        requireDirectChild(parent, file);
        directory(parent, create);
        return file;
    }

    private static ExplorationTrail readExploration(File file, String notebookId, String areaId) throws IOException {
        Envelope record = readEnvelope(file, EXPLORATION_MAGIC, 1, MAX_EXPLORATION_BYTES);
        try (DataInputStream in = record.input()) {
            if (!notebookId.equals(in.readUTF()) || !areaId.equals(in.readUTF()))
                throw new IOException("Exploration identity does not match its notebook and area");
            byte[] visited = new byte[32]; in.readFully(visited);
            int count = in.readUnsignedShort();
            if (count > ExplorationTrail.MAX_STEPS) throw new IOException("Too many exploration steps");
            List<ExplorationTrail.Step> steps = new ArrayList<>(count);
            for (int i = 0; i < count; i++) steps.add(new ExplorationTrail.Step(in.readShort(), in.readUnsignedByte()));
            if (in.read() != -1) throw new IOException("Unexpected extra exploration data");
            return ExplorationTrail.restore(visited, steps);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Invalid exploration history", invalid);
        }
    }

    /** A missing flag is empty ink; listFlags distinguishes it from a saved blank note. */
    public synchronized InkNote read(String notebookId, String areaId, int x, int y)
            throws IOException {
        File file = noteFile(notebookId, areaId, x, y, false);
        return file.exists() ? readInk(file, notebookId, areaId, x, y).ink : InkNote.empty();
    }

    /** Missing and legacy v1 notes use a plain flag; invalid records are not defaults. */
    public synchronized NoteIcon readIcon(String notebookId, String areaId, int x, int y)
            throws IOException {
        File file = noteFile(notebookId, areaId, x, y, false);
        return file.exists() ? readInk(file, notebookId, areaId, x, y).icon : NoteIcon.FLAG;
    }

    /** Compatibility save preserves a previously selected icon. */
    public synchronized void save(String notebookId, String areaId, int x, int y, InkNote note)
            throws IOException {
        if (note == null) throw new IllegalArgumentException("Missing note");
        save(notebookId, areaId, x, y, note, readIcon(notebookId, areaId, x, y));
    }

    /**
     * Saves the complete 8:3 composite sheet and selected symbol atomically.
     * Blank notes remain deliberate flags. Legacy v1 input is validated and
     * retained byte-for-byte as TILE.ink.v1 before its first successful rewrite.
     */
    public synchronized void save(String notebookId, String areaId, int x, int y,
            InkNote note, NoteIcon icon)
            throws IOException {
        if (note == null || icon == null) throw new IllegalArgumentException("Missing note or icon");
        File file = noteFile(notebookId, areaId, x, y, true);
        // Do not turn corruption, unknown icons, or future formats into silent data loss.
        StoredNote previous = file.exists() ? readInk(file, notebookId, areaId, x, y) : null;
        if (previous != null && previous.version == 1) retainLegacy(file, previous.payload);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(notebookId);
            out.writeUTF(areaId);
            out.writeByte(x);
            out.writeByte(y);
            out.writeUTF(icon.id());
            writeStrokes(out, note);
        }
        writeAtomic(file, INK_MAGIC, INK_VERSION, bytes.toByteArray());
    }

    public synchronized void delete(String notebookId, String areaId, int x, int y)
            throws IOException {
        File file = noteFile(notebookId, areaId, x, y, false);
        if (!file.exists()) return;
        readInk(file, notebookId, areaId, x, y);
        File backup = legacyFile(file);
        if (backup.exists()) {
            StoredNote old = readInk(backup, notebookId, areaId, x, y);
            if (old.version != 1 || !backup.delete()) throw new IOException("Cannot delete this note's legacy backup");
        }
        if (!file.delete()) throw new IOException("Cannot delete this flag and note");
    }

    /** Returns row-major tile numbers y*16+x, after validating each stored note. */
    public synchronized Set<Integer> listFlags(String notebookId, String areaId)
            throws IOException {
        return Collections.unmodifiableSet(new TreeSet<>(listFlagIcons(notebookId, areaId).keySet()));
    }

    /** Validated row-major tile numbers and their explicitly chosen symbols. */
    public synchronized Map<Integer, NoteIcon> listFlagIcons(String notebookId, String areaId)
            throws IOException {
        File parent = noteFile(notebookId, areaId, 0, 0, false).getParentFile();
        if (!parent.exists()) return Collections.emptyMap();
        File[] files = parent.listFiles();
        if (files == null) throw new IOException("Cannot read area notes");
        Map<Integer, NoteIcon> tiles = new TreeMap<>();
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith(".pending-")) continue; // Interrupted, uncommitted write.
            // These exact area-wide records are independent of flag notes;
            // corruption in exploration must not conceal readable handwriting.
            if (name.equals("map.ink") || name.equals("exploration.bin")) continue;
            if (name.matches("(0|[1-9][0-9]{0,2})\\.ink\\.v1")) {
                int tile = Integer.parseInt(name.substring(0, name.indexOf('.')));
                if (tile > 255) throw new IOException("Invalid legacy backup tile");
                continue; // Preserved old bytes, never a second visible flag.
            }
            if (!name.matches("(0|[1-9][0-9]{0,2})\\.ink")) {
                throw new IOException("Unrecognized area note record");
            }
            int tile = Integer.parseInt(name.substring(0, name.length() - 4));
            if (tile > 255) throw new IOException("Invalid note tile");
            StoredNote note = readInk(file, notebookId, areaId, tile % 16, tile / 16);
            tiles.put(tile, note.icon);
        }
        return Collections.unmodifiableMap(tiles);
    }

    private File noteFile(String notebookId, String areaId, int x, int y, boolean create)
            throws IOException {
        if (x < 0 || x >= 16 || y < 0 || y >= 16) {
            throw new IllegalArgumentException("Tile must be within the 16 by 16 area");
        }
        return new File(areaDirectory(notebookId, areaId, create), (y * 16 + x) + ".ink");
    }

    private File areaDirectory(String notebookId, String areaId, boolean create)
            throws IOException {
        requireId(notebookId);
        if (areaId == null || !areaId.matches("por-mac-v11-geo-(0|[1-9]|[12][0-9]|3[0-2])")) {
            throw new IllegalArgumentException("A resolved Macintosh area identity is required");
        }
        readNotebook(notebookId);
        File parent = new File(new File(root, notebookId), areaId);
        directory(parent, create);
        return parent;
    }

    private Notebook readNotebook(String id) throws IOException {
        requireId(id);
        File parent = new File(root, id);
        if (!parent.isDirectory()) throw new IOException("Notebook is missing or unreadable");
        Envelope record = readEnvelope(new File(parent, "notebook.bin"), BOOK_MAGIC, BOOK_VERSION, 1024);
        try (DataInputStream in = record.input()) {
            String recordedId = in.readUTF();
            String label = in.readUTF();
            if (!id.equals(recordedId) || !label.matches("Notebook [1-9][0-9]{0,2}")
                    || labelNumber(label) > MAX_NOTEBOOKS
                    || in.read() != -1) throw new IOException("Invalid notebook identity");
            return new Notebook(id, label);
        }
    }

    private static StoredNote readInk(File file, String notebookId, String areaId, int x, int y)
            throws IOException {
        Envelope record = readEnvelope(file, INK_MAGIC, INK_VERSION, MAX_NOTE_BYTES);
        try (DataInputStream in = record.input()) {
            if (!notebookId.equals(in.readUTF()) || !areaId.equals(in.readUTF())
                    || x != in.readUnsignedByte() || y != in.readUnsignedByte()) {
                throw new IOException("Note identity does not match its flag");
            }
            NoteIcon icon = record.version == 1 ? NoteIcon.FLAG : NoteIcon.fromId(in.readUTF());
            InkNote ink = readStrokes(in);
            if (record.version == 1) ink = widenLegacySheet(ink);
            return new StoredNote(record.version, record.payload, ink, icon);
        } catch (IllegalArgumentException invalid) {
            throw new IOException("Note contains an invalid icon or ink", invalid);
        }
    }

    /** Preserve the old 4:3 sheet intact in the new 8:3 sheet's right-hand half. */
    private static InkNote widenLegacySheet(InkNote legacy) {
        List<InkNote.Stroke> strokes = new ArrayList<>(legacy.strokes().size());
        for (InkNote.Stroke stroke : legacy.strokes()) {
            float[] points = stroke.points();
            for (int p = 0; p < points.length; p += 2) points[p] = .5f + .5f * points[p];
            strokes.add(new InkNote.Stroke(stroke.eraser(), stroke.width(), points));
        }
        return new InkNote(strokes);
    }

    private static final class StoredNote {
        final int version;
        final byte[] payload;
        final InkNote ink;
        final NoteIcon icon;
        StoredNote(int version, byte[] payload, InkNote ink, NoteIcon icon) {
            this.version = version; this.payload = payload; this.ink = ink; this.icon = icon;
        }
    }

    private static final class Envelope {
        final int version;
        final byte[] payload;
        Envelope(int version, byte[] payload) { this.version = version; this.payload = payload; }
        DataInputStream input() { return new DataInputStream(new ByteArrayInputStream(payload)); }
    }

    private static File legacyFile(File current) { return new File(current.getParentFile(), current.getName() + ".v1"); }

    private static void retainLegacy(File current, byte[] originalPayload) throws IOException {
        File backup = legacyFile(current);
        if (backup.exists()) {
            Envelope saved = readEnvelope(backup, INK_MAGIC, 1, MAX_NOTE_BYTES);
            if (!Arrays.equals(saved.payload, originalPayload)) throw new IOException("Legacy backup does not match the original note");
            return;
        }
        // Re-encoding the verified envelope with its untouched payload reproduces
        // the exact original bytes, including its original v1 version and CRC.
        writeAtomic(backup, INK_MAGIC, 1, originalPayload);
    }

    private static void writeStrokes(DataOutputStream out, InkNote ink) throws IOException {
        out.writeInt(ink.strokes().size());
        for (InkNote.Stroke stroke : ink.strokes()) {
            out.writeBoolean(stroke.eraser());
            out.writeFloat(stroke.width());
            out.writeInt(stroke.pointCount());
            for (float point : stroke.points()) out.writeFloat(point);
        }
    }

    private static InkNote readStrokes(DataInputStream in) throws IOException {
        try {
            int count = in.readInt();
            if (count < 0 || count > InkNote.MAX_STROKES) throw new IOException("Invalid stroke count");
            List<InkNote.Stroke> strokes = new ArrayList<>(count);
            int total = 0;
            for (int i = 0; i < count; i++) {
                int eraserByte = in.readUnsignedByte();
                if (eraserByte > 1) throw new IOException("Invalid ink tool");
                float width = in.readFloat();
                int points = in.readInt();
                if (points < 1 || points > InkNote.MAX_POINTS_PER_STROKE
                        || points > InkNote.MAX_TOTAL_POINTS - total) {
                    throw new IOException("Invalid stroke point count");
                }
                total += points;
                float[] coordinates = new float[points * 2];
                for (int p = 0; p < coordinates.length; p++) coordinates[p] = in.readFloat();
                strokes.add(new InkNote.Stroke(eraserByte == 1, width, coordinates));
            }
            if (in.read() != -1) throw new IOException("Unexpected extra note data");
            return new InkNote(strokes);
        } catch (IllegalArgumentException invalidInk) {
            throw new IOException("Note contains invalid ink", invalidInk);
        }
    }

    private static Envelope readEnvelope(File file, int magic, int maximumVersion, int maximum)
            throws IOException {
        if (!file.isFile() || file.length() < 20 || file.length() > maximum + 20L) {
            throw new IOException("Missing, truncated, or oversized notebook data");
        }
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            if (in.readInt() != magic) throw new IOException("Unrecognized notebook record type");
            int version = in.readInt();
            if (version < 1 || version > maximumVersion) {
                throw new IOException("Unrecognized notebook data version");
            }
            int length = in.readInt();
            if (length < 0 || length > maximum || file.length() != length + 20L) {
                throw new IOException("Invalid notebook data length");
            }
            byte[] payload = new byte[length];
            in.readFully(payload);
            CRC32 crc = new CRC32();
            crc.update(payload);
            if (in.readLong() != crc.getValue() || in.read() != -1) {
                throw new IOException("Notebook data checksum failed");
            }
            return new Envelope(version, payload);
        }
    }

    private static void writeAtomic(File target, int magic, int version, byte[] payload) throws IOException {
        File pending = File.createTempFile(".pending-", ".tmp", target.getParentFile());
        try {
            try (FileOutputStream stream = new FileOutputStream(pending);
                    DataOutputStream out = new DataOutputStream(stream)) {
                CRC32 crc = new CRC32();
                crc.update(payload);
                out.writeInt(magic);
                out.writeInt(version);
                out.writeInt(payload.length);
                out.write(payload);
                out.writeLong(crc.getValue());
                out.flush();
                stream.getFD().sync();
            }
            // Android/Linux same-directory rename replaces atomically. Never delete
            // the previous file as a fallback on a filesystem that refuses replace.
            if (!pending.renameTo(target)) throw new IOException("Cannot save notebook atomically");
        } finally {
            if (pending.exists()) pending.delete();
        }
    }

    private static void directory(File folder, boolean create) throws IOException {
        if (!folder.exists()) {
            if (!create) return;
            if (!folder.mkdirs() && !folder.isDirectory()) throw new IOException("Cannot create notebook folder");
        }
        if (!folder.isDirectory()) throw new IOException("Notebook path is not a folder");
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    private static void requireId(String id) {
        if (!validId(id)) throw new IllegalArgumentException("Invalid notebook identity");
    }

    private static int labelNumber(String label) { return Integer.parseInt(label.substring(9)); }
}
