package name.osher.gil.minivmac;
import org.junit.Test;
import static org.junit.Assert.*;
public class SaveRequestGateTest {
    private SaveRequestGate.Request request(SaveRequestGate.Kind kind,String book) {
        return new SaveRequestGate.Request(kind,"label",book,123);
    }
    @Test public void autoCannotStealAQuickTargetOrNotebook() {
        SaveRequestGate gate=new SaveRequestGate();
        SaveRequestGate.Request quick=request(SaveRequestGate.Kind.QUICK,"A"),auto=request(SaveRequestGate.Kind.AUTO,"B");
        assertTrue(gate.begin(quick));assertFalse(gate.begin(auto));assertSame(quick,gate.captured());
        assertTrue(gate.busy());assertFalse(gate.begin(auto));assertNull(gate.captured());
        gate.finish(auto);assertTrue(gate.busy());gate.finish(quick);assertFalse(gate.busy());assertTrue(gate.begin(auto));
        assertSame(auto,gate.captured());
    }
    @Test public void failedCaptureCanBeRetriedWithoutLosingNamedMetadata() {
        SaveRequestGate gate=new SaveRequestGate();SaveRequestGate.Request named=request(SaveRequestGate.Kind.NAMED,"C");
        assertNull(gate.captured());assertTrue(gate.begin(named));gate.finish(named);
        assertTrue(gate.begin(named));SaveRequestGate.Request captured=gate.captured();
        assertSame(named,captured);assertEquals("C",captured.notebook);assertEquals("label",captured.label);
        assertEquals(SaveRequestGate.Kind.NAMED,captured.kind);assertNull(gate.captured());
    }
}
