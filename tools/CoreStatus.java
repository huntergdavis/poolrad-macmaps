import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import java.util.*;

/** Read-only JDWP status; usage: java --add-modules jdk.jdi tools/CoreStatus.java PORT. */
public class CoreStatus {
    public static void main(String[] args) throws Exception {
        AttachingConnector connector=Bootstrap.virtualMachineManager().attachingConnectors().stream()
                .filter(c -> c.name().equals("com.sun.jdi.SocketAttach")).findFirst().orElseThrow();
        Map<String,Connector.Argument> options=connector.defaultArguments();
        options.get("hostname").setValue("127.0.0.1"); options.get("port").setValue(args[0]);
        options.get("timeout").setValue("10000");
        VirtualMachine vm=connector.attach(options);
        try {
            vm.suspend();
            for (ReferenceType type:vm.classesByName("name.osher.gil.minivmac.Core")) {
                System.out.println("audioTrack="+type.getValue(type.fieldByName("mAudioTrack")));
                for (ObjectReference core:type.instances(8)) {
                    System.out.println("Core " + core.uniqueID());
                    for(String name:List.of("initOk","emulationEnded","mIsInitialized","numInsertedDisks","emulationPaused","automaticIdle"))
                        System.out.println(name+"="+core.getValue(type.fieldByName(name)));
                }
            }
            for (ReferenceType type:vm.classesByName("name.osher.gil.minivmac.EmulatorFragment")) {
                for (ObjectReference fragment:type.instances(8)) {
                    for (String name:List.of("mMapPolling", "mWheelPolling", "mAutoSaving", "mBootDismissArmed"))
                        System.out.println(name+"="+fragment.getValue(type.fieldByName(name)));
                    ObjectReference lock=(ObjectReference)fragment.getValue(type.fieldByName("mMulticastLock"));
                    System.out.println("multicastLockHeld="+(lock==null ? "false"
                            : lock.getValue(lock.referenceType().fieldByName("mHeld"))));
                }
            }
            for(ThreadReference thread:vm.allThreads())
                if(thread.name().toLowerCase().contains("emul")) System.out.println("thread="+thread.name()+" status="+thread.status());
        } finally { vm.resume(); vm.dispose(); }
    }
}
