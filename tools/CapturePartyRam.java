import com.sun.jdi.Bootstrap;
import com.sun.jdi.ClassType;
import com.sun.jdi.Method;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.EventRequest;
import java.util.Collections;
import java.util.Map;

/** Developer-only, read-only capture using the existing DEBUG core API.
 * Start the debug APK and select Map, then forward the app's JDWP process:
 * adb -s emulator-5584 forward tcp:8700 jdwp:APP_PID
 * java --add-modules jdk.jdi tools/CapturePartyRam.java 8700
 * The app saves its own bounded snapshot in private files/snapshots/.
 * No guest RAM fields, guest disks, saves, or game input are changed.
 */
public final class CapturePartyRam {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: CapturePartyRam LOCAL_JDWP_PORT");
        AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().get();
        Map<String, Connector.Argument> options = connector.defaultArguments();
        options.get("hostname").setValue("127.0.0.1");
        options.get("port").setValue(args[0]);
        options.get("timeout").setValue("10000");
        VirtualMachine vm = connector.attach(options);
        try {
            ClassType core = (ClassType) vm.classesByName("name.osher.gil.minivmac.Core").stream()
                    .findFirst().orElseThrow(() -> new IllegalStateException("Start the emulator activity first"));
            Method sample = core.methodsByName("requestPartySample").get(0);
            BreakpointRequest request = vm.eventRequestManager().createBreakpointRequest(sample.location());
            request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
            request.enable();
            long deadline = System.nanoTime() + 30_000_000_000L;
            while (System.nanoTime() < deadline) {
                EventSet events = vm.eventQueue().remove(1000);
                if (events == null) continue;
                boolean captured = false;
                try {
                    for (Event event : events) if (event instanceof BreakpointEvent) {
                        BreakpointEvent hit = (BreakpointEvent) event;
                        request.disable();
                        ObjectReference instance = hit.thread().frame(0).thisObject();
                        Object result = instance.invokeMethod(hit.thread(), core.methodsByName("captureRam").get(0),
                                Collections.emptyList(), ObjectReference.INVOKE_SINGLE_THREADED);
                        if (!"true".equals(result.toString())) throw new IllegalStateException("Core declined DEBUG capture");
                        System.out.println("Read-only snapshot queued; inspect app files/snapshots after the next core tick.");
                        captured = true;
                    }
                } finally { events.resume(); }
                if (captured) return;
            }
            throw new IllegalStateException("No party sample tick; select visible Map in a running debug APK");
        } finally { vm.dispose(); }
    }
}
