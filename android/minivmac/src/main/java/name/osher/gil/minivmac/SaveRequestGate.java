package name.osher.gil.minivmac;

/** One capture owns its label/notebook until its background write finishes. */
final class SaveRequestGate {
    enum Kind { QUICK, NAMED, AUTO }
    static final class Request {
        final Kind kind;
        final String label, notebook;
        final long started;
        Request(Kind kind, String label, String notebook, long started) {
            this.kind=kind; this.label=label; this.notebook=notebook; this.started=started;
        }
    }
    private Request pending;
    private boolean delivered;
    synchronized boolean begin(Request request) {
        if (pending != null) return false;
        pending=request; delivered=false; return true;
    }
    synchronized Request captured() {
        if (pending == null || delivered) return null;
        delivered=true; return pending;
    }
    synchronized void finish(Request request) { if (pending == request) pending=null; }
    synchronized boolean busy() { return pending != null; }
}
