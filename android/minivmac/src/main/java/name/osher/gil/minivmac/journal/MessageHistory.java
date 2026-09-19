package name.osher.gil.minivmac.journal;

import java.io.*;
import java.util.*;

/** The last observed Message-window texts, never an inferred transcript. */
public final class MessageHistory {
    public static final int LIMIT = 200, MAX_BYTES = 128 * 1024;
    public static final class Entry {
        public final long observedAt;
        public final String text;
        public final boolean truncated;
        private Entry(long time, String text, boolean truncated) {
            this.observedAt = time; this.text = text; this.truncated = truncated;
        }
    }
    private final List<Entry> entries = new ArrayList<>();
    private String lastObserved;
    private boolean lastTruncated;

    public List<Entry> entries() { return Collections.unmodifiableList(new ArrayList<>(entries)); }

    /** Repeated polls add nothing; a growing window updates its latest reading. */
    public boolean observe(GameMessage message, long time) {
        if (message == null) return false;
        String text = message.text;
        if (text.trim().isEmpty()) { lastObserved = null; return false; }
        if (text.equals(lastObserved) && message.truncated == lastTruncated) return false;
        Entry last = entries.isEmpty() ? null : entries.get(entries.size() - 1);
        boolean grows = last != null && lastObserved != null && last.text.equals(lastObserved)
                && text.startsWith(lastObserved);
        Entry next = new Entry(grows ? last.observedAt : Math.max(0, time), text, message.truncated);
        if (grows) entries.set(entries.size() - 1, next);
        else {
            if (entries.size() == LIMIT) entries.remove(0);
            entries.add(next);
        }
        lastObserved = text; lastTruncated = message.truncated;
        return true;
    }

    public byte[] encode() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(entries.size());
            for (Entry entry : entries) {
                out.writeLong(entry.observedAt);
                out.writeBoolean(entry.truncated);
                out.writeUTF(entry.text);
            }
        }
        return bytes.toByteArray();
    }

    public static MessageHistory read(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > LIMIT) throw new IOException("Invalid message count");
        MessageHistory history = new MessageHistory();
        for (int i = 0; i < count; i++) {
            long time = in.readLong(); int truncated = in.readUnsignedByte(); String text = in.readUTF();
            if (time < 0 || truncated > 1 || text.isEmpty() || text.trim().isEmpty()
                    || text.length() > GameMessage.MAX_TEXT) throw new IOException("Invalid message entry");
            for (int j = 0; j < text.length(); j++) {
                char c = text.charAt(j);
                if (!(c >= 32 && c <= 126 || c == '\r' || c == '\t'))
                    throw new IOException("Invalid message text");
            }
            history.entries.add(new Entry(time, text, truncated == 1));
            history.lastObserved = text; history.lastTruncated = truncated == 1;
        }
        if (in.read() != -1) throw new IOException("Unexpected message history data");
        return history;
    }
}
