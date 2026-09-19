package name.osher.gil.minivmac;

import name.osher.gil.minivmac.mapper.PartyState;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;

/** Bounded intents, not row numbers. Only drain from the emulation-thread sample callback. */
final class QuickToggleQueue {
    interface Writer { boolean set(int member, boolean on); }
    private final Map<String,Boolean> pending=new LinkedHashMap<>();
    private static String key(PartyState.Member member) { return member.characterClass+"/"+member.name; }
    synchronized boolean request(PartyState.Member member, boolean on) {
        if(pending.size()>=PartyState.MAX_MEMBERS&&!pending.containsKey(key(member))) return false;
        pending.put(key(member),on);return true;
    }
    synchronized Boolean pending(PartyState.Member member) { return pending.get(key(member)); }
    synchronized boolean busy() { return !pending.isEmpty(); }
    synchronized void clear() { pending.clear(); }
    synchronized boolean drain(PartyState party, Writer writer) {
        if(party==null) return false; // A transient unreadable frame must not lose a tap.
        boolean written=false;
        Iterator<Map.Entry<String,Boolean>> it=pending.entrySet().iterator();
        while(it.hasNext()) {
            Map.Entry<String,Boolean> intent=it.next();int index=-1,matches=0;
            for(int i=0;i<party.members.size();i++) if(key(party.members.get(i)).equals(intent.getKey())) {index=i;matches++;}
            if(matches!=1) {it.remove();continue;} // Gone or ambiguous: never select someone else.
            PartyState.Member member=party.members.get(index);
            if(intent.getValue().equals(member.quick)) it.remove();
            else if(writer.set(index,intent.getValue())) {it.remove();written=true;}
        }
        return written;
    }
}
