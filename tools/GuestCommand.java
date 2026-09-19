import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.util.*;

/** Invoke the app's normal timed Command-key input, via local debug access.
 * No guest-memory edits. Usage: GuestCommand PORT load|quit|begin|view|save|text|key [TEXT].
 * view is the Character menu Cmd-E; use key V for the active exploration/camp View.
 */
public class GuestCommand {
    public static void main(String[] args) throws Exception {
        Map<String,Character> keys=Map.of("load",'L',"quit",'Q',"begin",'B',"view",'E',"save",'S');
        Character key=keys.get(args[1]);
        boolean single=args[1].equals("key") && args.length==3 && args[2].matches("[A-Za-z0-9]");
        boolean text=args[1].equals("text") && args.length==3 && args[2].matches("[A-Za-z0-9 .]{0,64}");
        if(key==null && !text && !single) throw new IllegalArgumentException("Expected load, quit, begin, view, save, text STRING, or key LETTER");
        AttachingConnector c=Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(x -> x.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        Map<String,Connector.Argument> opts=c.defaultArguments();
        opts.get("hostname").setValue("127.0.0.1"); opts.get("port").setValue(args[0]);
        opts.get("timeout").setValue("10000");
        VirtualMachine vm=c.attach(opts);
        try {
            ClassType core=(ClassType)vm.classesByName("name.osher.gil.minivmac.Core").get(0);
            ClassType fragment=(ClassType)vm.classesByName("name.osher.gil.minivmac.EmulatorFragment").get(0);
            BreakpointRequest request=vm.eventRequestManager().createBreakpointRequest(
                    core.methodsByName("requestPartySample").get(0).location());
            request.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); request.enable();
            long deadline=System.nanoTime()+30_000_000_000L;
            while(System.nanoTime()<deadline) {
                EventSet events=vm.eventQueue().remove(1000); if(events==null) continue;
                boolean sent=false;
                try {
                    for(Event e:events) if(e instanceof BreakpointEvent) {
                        BreakpointEvent hit=(BreakpointEvent)e; request.disable();
                        ObjectReference currentCore=hit.thread().frame(0).thisObject();
                        for(ObjectReference instance:fragment.instances(8)) {
                            if(!currentCore.equals(instance.getValue(fragment.fieldByName("mCore")))) continue;
                            if(text || single) instance.invokeMethod(hit.thread(),fragment.methodsByName("sendGuestLine").get(0),
                                    List.of(vm.mirrorOf(args[2]),vm.mirrorOf(!single)),ObjectReference.INVOKE_SINGLE_THREADED);
                            else instance.invokeMethod(hit.thread(),fragment.methodsByName("sendCommandKey").get(0),
                                    List.of(vm.mirrorOf(key.charValue())),ObjectReference.INVOKE_SINGLE_THREADED);
                            sent=true; break;
                        }
                        if(!sent) throw new IllegalStateException("No active fragment for this core");
                    }
                } finally { events.resume(); }
                if(sent) { System.out.println("Queued normal guest input: "+args[1]); return; }
            }
            throw new IllegalStateException("No live UI party poll");
        } finally { vm.dispose(); }
    }
}
