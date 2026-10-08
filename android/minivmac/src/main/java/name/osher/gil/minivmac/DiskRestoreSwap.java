package name.osher.gil.minivmac;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Rename prepared, verified disk bytes into place at the stopped native restore boundary. */
final class DiskRestoreSwap {
    private static final class Move {
        final File target;
        final File staged;
        File previous;
        boolean installed;
        Move(DiskCheckpointStore.Prepared.Disk disk) {
            target = disk.target; staged = disk.staged;
        }
    }
    private final List<Move> moves = new ArrayList<>();
    private boolean begun;

    DiskRestoreSwap(DiskCheckpointStore.Prepared prepared) {
        for (DiskCheckpointStore.Prepared.Disk disk : prepared.disks) moves.add(new Move(disk));
    }

    void begin() throws IOException {
        if (begun) throw new IllegalStateException("Disk swap already begun");
        begun = true;
        try {
            for (Move move : moves) {
                if (!move.staged.isFile()) throw new IOException("Recovered disk is unavailable");
                if (move.target.exists()) {
                    if (!move.target.isFile()) throw new IOException("Disk destination is not a file");
                    File reserved = File.createTempFile(".poolrad-previous-", ".bak",
                            move.target.getParentFile());
                    if (!reserved.delete() || !move.target.renameTo(reserved))
                        throw new IOException("Could not hold the current disk");
                    move.previous = reserved;
                }
                if (!move.staged.renameTo(move.target))
                    throw new IOException("Could not install the recovered disk");
                move.installed = true;
            }
        } catch (IOException | RuntimeException failure) {
            try { rollback(); }
            catch (IOException rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }

    void rollback() throws IOException {
        IOException failure = null;
        for (int i = moves.size() - 1; i >= 0; i--) {
            Move move = moves.get(i);
            if (move.installed) {
                if (!move.target.renameTo(move.staged)) {
                    failure = new IOException("Could not remove a rejected recovered disk", failure);
                    continue;
                }
                move.installed = false;
            }
            if (move.previous != null && !move.previous.renameTo(move.target))
                failure = new IOException("Could not restore the previous disk", failure);
            else move.previous = null;
        }
        if (failure != null) throw failure;
    }

    void commit() {
        for (Move move : moves) {
            if (move.previous != null) move.previous.delete();
            move.previous = null;
        }
    }
}
