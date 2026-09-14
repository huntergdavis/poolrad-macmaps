package name.osher.gil.minivmac.notebook;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.zip.CRC32;

/** App-private user ink only: this class never opens an emulator disk or save. */
public final class NotebookStore {
    private static final int VERSION = 1;
    private static final int BOOK_MAGIC = 0x50524e42; // PRNB
    private static final int INK_MAGIC = 0x50524e49; // PRNI
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
        int next = 1;
        for (Notebook book : existing) next = Math.max(next, labelNumber(book.label) + 1);
        if (next > MAX_NOTEBOOKS) throw new IOException("Notebook label limit reached");
        Notebook book = new Notebook(UUID.randomUUID().toString(), "Notebook " + next);
        File directory = new File(root, book.id);
        if (!directory.mkdir()) throw new IOException("Cannot create notebook folder");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(book.id);
            out.writeUTF(book.label);
        }
        try {
            writeAtomic(new File(directory, "notebook.bin"), BOOK_MAGIC, bytes.toByteArray());
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

    /** A missing flag is empty ink; listFlags distinguishes it from a saved blank note. */
    public synchronized InkNote read(String notebookId, String areaId, int x, int y)
            throws IOException {
        File file = noteFile(notebookId, areaId, x, y, false);
        return file.exists() ? readInk(file, notebookId, areaId, x, y) : InkNote.empty();
    }

    /** Saves even an empty note, because a deliberate blank flag is still a flag. */
    public synchronized void save(String notebookId, String areaId, int x, int y, InkNote note)
            throws IOException {
        if (note == null) throw new IllegalArgumentException("Missing note");
        File file = noteFile(notebookId, areaId, x, y, true);
        // Do not turn corruption or an unknown future format into silent data loss.
        if (file.exists()) readInk(file, notebookId, areaId, x, y);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeUTF(notebookId);
            out.writeUTF(areaId);
            out.writeByte(x);
            out.writeByte(y);
            out.writeInt(note.strokes().size());
            for (InkNote.Stroke stroke : note.strokes()) {
                out.writeBoolean(stroke.eraser());
                out.writeFloat(stroke.width());
                out.writeInt(stroke.pointCount());
                for (float point : stroke.points()) out.writeFloat(point);
            }
        }
        writeAtomic(file, INK_MAGIC, bytes.toByteArray());
    }

    public synchronized void delete(String notebookId, String areaId, int x, int y)
            throws IOException {
        File file = noteFile(notebookId, areaId, x, y, false);
        if (!file.exists()) return;
        readInk(file, notebookId, areaId, x, y);
        if (!file.delete()) throw new IOException("Cannot delete this flag and note");
    }

    /** Returns row-major tile numbers y*16+x, after validating each stored note. */
    public synchronized Set<Integer> listFlags(String notebookId, String areaId)
            throws IOException {
        File parent = noteFile(notebookId, areaId, 0, 0, false).getParentFile();
        if (!parent.exists()) return Collections.emptySet();
        File[] files = parent.listFiles();
        if (files == null) throw new IOException("Cannot read area notes");
        Set<Integer> tiles = new TreeSet<>();
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith(".pending-")) continue; // Interrupted, uncommitted write.
            if (!name.matches("(0|[1-9][0-9]{0,2})\\.ink")) {
                throw new IOException("Unrecognized area note record");
            }
            int tile = Integer.parseInt(name.substring(0, name.length() - 4));
            if (tile > 255) throw new IOException("Invalid note tile");
            readInk(file, notebookId, areaId, tile % 16, tile / 16);
            tiles.add(tile);
        }
        return Collections.unmodifiableSet(tiles);
    }

    private File noteFile(String notebookId, String areaId, int x, int y, boolean create)
            throws IOException {
        requireId(notebookId);
        if (areaId == null || !areaId.matches("por-mac-v11-geo-(0|[1-9]|[12][0-9]|3[0-2])")) {
            throw new IllegalArgumentException("A resolved Macintosh area identity is required");
        }
        if (x < 0 || x >= 16 || y < 0 || y >= 16) {
            throw new IllegalArgumentException("Tile must be within the 16 by 16 area");
        }
        readNotebook(notebookId);
        File parent = new File(new File(root, notebookId), areaId);
        directory(parent, create);
        return new File(parent, (y * 16 + x) + ".ink");
    }

    private Notebook readNotebook(String id) throws IOException {
        requireId(id);
        File parent = new File(root, id);
        if (!parent.isDirectory()) throw new IOException("Notebook is missing or unreadable");
        try (DataInputStream in = readEnvelope(new File(parent, "notebook.bin"), BOOK_MAGIC, 1024)) {
            String recordedId = in.readUTF();
            String label = in.readUTF();
            if (!id.equals(recordedId) || !label.matches("Notebook [1-9][0-9]{0,2}")
                    || labelNumber(label) > MAX_NOTEBOOKS
                    || in.read() != -1) throw new IOException("Invalid notebook identity");
            return new Notebook(id, label);
        }
    }

    private static InkNote readInk(File file, String notebookId, String areaId, int x, int y)
            throws IOException {
        try (DataInputStream in = readEnvelope(file, INK_MAGIC, MAX_NOTE_BYTES)) {
            if (!notebookId.equals(in.readUTF()) || !areaId.equals(in.readUTF())
                    || x != in.readUnsignedByte() || y != in.readUnsignedByte()) {
                throw new IOException("Note identity does not match its flag");
            }
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

    private static DataInputStream readEnvelope(File file, int magic, int maximum)
            throws IOException {
        if (!file.isFile() || file.length() < 20 || file.length() > maximum + 20L) {
            throw new IOException("Missing, truncated, or oversized notebook data");
        }
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            if (in.readInt() != magic || in.readInt() != VERSION) {
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
            return new DataInputStream(new ByteArrayInputStream(payload));
        }
    }

    private static void writeAtomic(File target, int magic, byte[] payload) throws IOException {
        File pending = File.createTempFile(".pending-", ".tmp", target.getParentFile());
        try {
            try (FileOutputStream stream = new FileOutputStream(pending);
                    DataOutputStream out = new DataOutputStream(stream)) {
                CRC32 crc = new CRC32();
                crc.update(payload);
                out.writeInt(magic);
                out.writeInt(VERSION);
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
