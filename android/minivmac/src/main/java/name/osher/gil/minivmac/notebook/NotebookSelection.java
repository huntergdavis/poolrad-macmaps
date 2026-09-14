package name.osher.gil.minivmac.notebook;

import java.io.IOException;
import java.util.List;

/** A missing remembered campaign is never silently replaced by a different one. */
public final class NotebookSelection {
    private NotebookSelection() { }
    public static NotebookStore.Notebook choose(List<NotebookStore.Notebook> books, String remembered) throws IOException {
        if (remembered == null || remembered.isEmpty()) return books.isEmpty() ? null : books.get(0);
        for (NotebookStore.Notebook book : books) if (book.id().equals(remembered)) return book;
        throw new IOException("Selected notebook is missing; choose a notebook explicitly");
    }
}
