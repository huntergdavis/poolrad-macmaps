import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** UI-only fixture replay into a specifically named disposable notebook.
 * Invoke through debug-ui.sh. Never edits guest RAM or sends guest input.
 * Usage: ReplayMessage PORT PACKET_FILE EXPECTED_NOTEBOOK_LABEL
 */
public class ReplayMessage {
    public static void main(String[] args) throws Exception {
        if(args.length!=3) throw new IllegalArgumentException("PORT PACKET_FILE EXPECTED_NOTEBOOK_LABEL");
        if(Files.size(Path.of(args[1]))!=520) throw new IllegalArgumentException("Expected one 520-byte PRT1 packet");
        byte[] packet=Files.readAllBytes(Path.of(args[1]));
        if(packet.length!=520 || packet[0]!='P' || packet[1]!='R' || packet[2]!='T' || packet[3]!='1')
            throw new IllegalArgumentException("Expected one PRT1 packet");
        if(packet[4]!=1 || (packet[5]!=0 && packet[5]!=1)) throw new IllegalArgumentException("Expected readable packet");
        int length=((packet[6]&255)<<8)|(packet[7]&255);
        if(length>512) throw new IllegalArgumentException("Invalid text length");
        for(int i=8;i<packet.length;i++) {
            int b=packet[i]&255;
            if(i>=8+length ? b!=0 : !(b>=32 && b<=126 || b==13 || b==9))
                throw new IllegalArgumentException("Invalid message text/padding");
        }
        AttachingConnector connector=Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(c->c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        Map<String,Connector.Argument> options=connector.defaultArguments();
        options.get("hostname").setValue("127.0.0.1"); options.get("port").setValue(args[0]);
        options.get("timeout").setValue("10000");
        VirtualMachine vm=connector.attach(options);
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
                    for(Event event:events) if(event instanceof BreakpointEvent hit) {
                        request.disable();
                        ObjectReference activeCore=hit.thread().frame(0).thisObject();
                        for(ObjectReference instance:fragment.instances(8)) {
                            if(!activeCore.equals(instance.getValue(fragment.fieldByName("mCore")))) continue;
                            ObjectReference notebook=(ObjectReference)instance.getValue(fragment.fieldByName("mNotebook"));
                            if(notebook==null) throw new IllegalStateException("No active notebook controller");
                            ReferenceType type=notebook.referenceType();
                            Value label=notebook.invokeMethod(hit.thread(),type.methodsByName("notebookLabel").get(0),
                                    List.of(),ObjectReference.INVOKE_SINGLE_THREADED);
                            if(!(label instanceof StringReference) || !((StringReference)label).value().equals(args[2]))
                                throw new IllegalStateException("Active notebook differs from expected test notebook");
                            ArrayType arrayType=(ArrayType)vm.classesByName("byte[]").get(0);
                            ArrayReference bytes=arrayType.newInstance(packet.length);
                            List<Value> values=new ArrayList<>();
                            for(byte b:packet) values.add(vm.mirrorOf(b));
                            bytes.setValues(values);
                            notebook.invokeMethod(hit.thread(),type.methodsByName("onGameMessage").get(0),
                                    List.of(bytes),ObjectReference.INVOKE_SINGLE_THREADED);
                            sent=true; break;
                        }
                        if(!sent) throw new IllegalStateException("No active fragment for current core");
                    }
                } finally { events.resume(); }
                if(sent) {
                    System.out.println(ZonedDateTime.now(ZoneId.of("America/Los_Angeles"))
                            .format(DateTimeFormatter.ofPattern("[yyyy-MM-dd HH:mm:ss z] "))
                            +"Replayed host-only message fixture into "+args[2]);
                    return;
                }
            }
            throw new IllegalStateException("No live UI party poll");
        } finally { vm.dispose(); }
    }
}
