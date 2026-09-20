import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.util.*;

/** Emulator-only lifecycle probe: rapid requests, paused RAM delivery and frozen guest ticks. */
public class PauseActivity {
    private static void invoke(ObjectReference core, ClassType type, ThreadReference thread, String name)
            throws Exception {
        core.invokeMethod(thread, type.methodsByName(name).get(0), List.of(),
                ObjectReference.INVOKE_SINGLE_THREADED);
    }
    public static void main(String[] args) throws Exception {
        AttachingConnector connector = Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        Map<String, Connector.Argument> options = connector.defaultArguments();
        options.get("hostname").setValue("127.0.0.1");
        options.get("port").setValue(args[0]);
        options.get("timeout").setValue("10000");
        VirtualMachine vm = connector.attach(options);
        try {
            ClassType type = (ClassType)vm.classesByName("name.osher.gil.minivmac.Core").get(0);
            BreakpointRequest ui = vm.eventRequestManager().createBreakpointRequest(
                    type.methodsByName("requestPartySample").get(0).location());
            BreakpointRequest ram = vm.eventRequestManager().createBreakpointRequest(
                    type.methodsByName("onRamSnapshot").get(0).location());
            ui.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); ui.enable();
            ram.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); ram.enable();
            int phase = 0, replies = 0;
            long ticks = -1, firstReply = 0, deadline = System.nanoTime() + 60_000_000_000L;
            while (System.nanoTime() < deadline) {
                EventSet events = vm.eventQueue().remove(500);
                if (events == null) continue;
                boolean done = false;
                try {
                    for (Event event : events) if (event instanceof BreakpointEvent) {
                        BreakpointEvent hit = (BreakpointEvent)event;
                        StackFrame frame = hit.thread().frame(0);
                        ObjectReference core = frame.thisObject();
                        if (hit.request() == ui) {
                            if (phase == 0) {
                                for (int i = 0; i < 20; ++i) {
                                    invoke(core, type, hit.thread(), "pauseEmulation");
                                    invoke(core, type, hit.thread(), "resumeEmulation");
                                }
                                invoke(core, type, hit.thread(), "pauseEmulation");
                                long pauseDeadline = System.nanoTime() + 3_000_000_000L;
                                while (!((BooleanValue)type.invokeMethod(hit.thread(),
                                        type.methodsByName("isPaused").get(0), List.of(),
                                        ObjectReference.INVOKE_SINGLE_THREADED)).value()) {
                                    if (System.nanoTime() >= pauseDeadline)
                                        throw new AssertionError("Native pause was not acknowledged");
                                    Thread.sleep(20);
                                }
                                invoke(core, type, hit.thread(), "captureRam");
                                phase = 1;
                            } else if (phase == 1 && replies == 1
                                    && System.nanoTime() - firstReply > 1_000_000_000L) {
                                invoke(core, type, hit.thread(), "captureRam");
                                phase = 2;
                            } else if (phase == 2 && replies == 2) {
                                invoke(core, type, hit.thread(), "resumeEmulation");
                                done = true;
                            }
                        } else {
                            ArrayReference bytes = (ArrayReference)frame.getValue(frame.visibleVariableByName("ram"));
                            long now = 0;
                            for (Value b : bytes.getValues(0x16a, 4))
                                now = (now << 8) | (((ByteValue)b).value() & 255);
                            if (replies == 0) { ticks = now; firstReply = System.nanoTime(); }
                            else if (now != ticks) throw new AssertionError("Guest advanced while paused");
                            ++replies;
                            System.out.println("paused RAM reply " + replies + ": guest TickCount=" + now
                                    + " audioTrack=" + type.getValue(type.fieldByName("mAudioTrack")));
                        }
                    }
                } finally { events.resume(); }
                if (done) {
                    System.out.println("PASS: 20 rapid pause/resume pairs; two paused RAM requests;"
                            + " guest clock frozen for >1 second; resumed");
                    return;
                }
            }
            throw new IllegalStateException("Pause probe timed out; foreground the app to resume");
        } finally { vm.dispose(); }
    }
}
