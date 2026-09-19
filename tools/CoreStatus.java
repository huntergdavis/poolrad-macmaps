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
                for (ObjectReference core:type.instances(8)) {
                    System.out.println("Core " + core.uniqueID());
                    for(String name:List.of("initOk","emulationEnded","mIsInitialized","numInsertedDisks"))
                        System.out.println(name+"="+core.getValue(type.fieldByName(name)));
                }
            }
            for(ThreadReference thread:vm.allThreads())
                if(thread.name().toLowerCase().contains("emul")) System.out.println("thread="+thread.name()+" status="+thread.status());
        } finally { vm.resume(); vm.dispose(); }
    }
}
