import java.io.*;
import java.nio.file.*;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import name.osher.gil.minivmac.SaveStateStore;

/** A private, self-contained fixture for the native restore-failure callback.
 * Usage: java -cp APP_CLASSES tools/MakeRejectedState.java COPIED_STORE SOURCE NEW_OUTPUT
 * Never reads a live disk or overwrites a file. Use only a disposable emulator.
 */
public final class MakeRejectedState {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("COPIED_STORE SOURCE NEW_OUTPUT");
        SaveStateStore store = new SaveStateStore(new File(args[0]));
        File source = new File(args[1]);
        SaveStateStore.Snapshot snapshot = store.readSnapshot(source);
        byte[] original = snapshot.state;
        if (original.length < 1024 || original[0] != 'P' || original[1] != 'R'
                || original[2] != 'S' || original[3] != 'S')
            throw new IOException("Expected a native PRSS machine snapshot");
        String binding = store.readBinding(source);
        if (binding == null) throw new IOException("Use a snapshot with an existing notebook binding");
        byte[] shortState = Arrays.copyOf(original, original.length - 1);
        for (int i = 0; i < 4; i++) shortState[12 + i] = (byte)(shortState.length >>> (24 - i * 8));
        Path output = Path.of(args[2]).toAbsolutePath();
        try (OutputStream out = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
            out.write(new byte[]{'P','R','Q','S','3','\n'});
            snapshot.disks.writeTo(out);
            out.write(0); // complete-image mode
            GZIPOutputStream gzip = new GZIPOutputStream(out);
            gzip.write(shortState); gzip.finish();
        }
        Files.writeString(Path.of(output + ".notebook"), binding, StandardOpenOption.CREATE_NEW);
        byte[] readback = new SaveStateStore(output.getParent().toFile()).read(output.toFile());
        if (!Arrays.equals(shortState, readback)) throw new IOException("Fixture readback differs");
        System.out.println("Created truncated native-body fixture with a valid container: " + output);
    }
}
