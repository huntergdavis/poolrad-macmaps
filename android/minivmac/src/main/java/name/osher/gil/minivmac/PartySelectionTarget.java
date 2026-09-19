package name.osher.gil.minivmac;

import name.osher.gil.minivmac.mapper.PartyState;

/** Resolve the tapped identity against the fresh frame, refusing ambiguous names/classes. */
final class PartySelectionTarget {
    static int index(PartyState current, PartyState.Member requested) {
        if (current == null || requested == null) return -1;
        int found = -1;
        for (int i = 0; i < current.members.size(); i++) {
            PartyState.Member member = current.members.get(i);
            if (member.characterClass == requested.characterClass && member.name.equals(requested.name)) {
                if (found >= 0) return -1;
                found = i;
            }
        }
        return found;
    }
}
