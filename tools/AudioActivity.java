import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import java.util.*;

/** Observe real audio callbacks for ten seconds; optional normal guest key (e.g. 8). */
public class AudioActivity {
    public static void main(String[] args) throws Exception {
        AttachingConnector connector=Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        Map<String,Connector.Argument> options=connector.defaultArguments();
        options.get("hostname").setValue("127.0.0.1"); options.get("port").setValue(args[0]);
        options.get("timeout").setValue("10000");
        VirtualMachine vm=connector.attach(options);
        long calls=0, samples=0, nonCenter=0; boolean sent=false;
        try {
            ClassType core=(ClassType)vm.classesByName("name.osher.gil.minivmac.Core").get(0);
            BreakpointRequest audio=vm.eventRequestManager().createBreakpointRequest(
                    core.methodsByName("playSound").get(0).location());
            audio.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); audio.enable();
            BreakpointRequest input=null;
            if(args.length>1) {
                input=vm.eventRequestManager().createBreakpointRequest(
                        core.methodsByName("requestPartySample").get(0).location());
                input.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD); input.enable();
            }
            long deadline=System.nanoTime()+10_000_000_000L;
            while(System.nanoTime()<deadline) {
                EventSet events=vm.eventQueue().remove(500); if(events==null) continue;
                try {
                    for(Event event:events) if(event instanceof BreakpointEvent) {
                        BreakpointEvent hit=(BreakpointEvent)event;
                        if(hit.request()==audio) {
                            StackFrame frame=hit.thread().frame(0);
                            ArrayReference bytes=(ArrayReference)frame.getValue(frame.visibleVariableByName("buf"));
                            calls++; samples+=bytes.length();
                            for(Value value:bytes.getValues())
                                if((((ByteValue)value).value()&255)!=128) nonCenter++;
                        } else {
                            input.disable();
                            ObjectReference currentCore=hit.thread().frame(0).thisObject();
                            ClassType fragment=(ClassType)vm.classesByName("name.osher.gil.minivmac.EmulatorFragment").get(0);
                            for(ObjectReference instance:fragment.instances(8)) {
                                if(!currentCore.equals(instance.getValue(fragment.fieldByName("mCore")))) continue;
                                instance.invokeMethod(hit.thread(),fragment.methodsByName("sendGuestLine").get(0),
                                        List.of(vm.mirrorOf(args[1]),vm.mirrorOf(false)),ObjectReference.INVOKE_SINGLE_THREADED);
                                sent=true; break;
                            }
                        }
                    }
                } finally { events.resume(); }
            }
            System.out.println("audioCalls="+calls+" samples="+samples+" nonCenter="+nonCenter
                    +" track="+core.getValue(core.fieldByName("mAudioTrack"))+" guestKeySent="+sent);
            if(args.length>1 && !sent) throw new IllegalStateException("No live guest input poll");
        } finally { vm.dispose(); }
    }
}
