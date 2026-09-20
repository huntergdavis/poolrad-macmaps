package name.osher.gil.minivmac.mapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Who a fight's end leaves to bandage. Pure Java: the game's Bandage turns a
 * Dying character Unconscious at zero hit points and does nothing else, so the
 * plan names Dying members only. Nothing happens when nobody is standing: the
 * game's own party-lost screen is not a moment to write into.
 */
public final class BandagePlan {
    public static final int OKAY = 0, UNCONSCIOUS = 4, DYING = 5;
    private BandagePlan() { }

    /** Row indices, as the reader numbers them, whose condition reads Dying. */
    public static List<Integer> dying(PartyState party) {
        List<Integer> rows = new ArrayList<>();
        if (party == null) return rows;
        for (int i = 0; i < party.members.size(); i++)
            if (party.members.get(i).condition == DYING) rows.add(i);
        return rows;
    }

    /** True while at least one member's condition reads Okay. */
    public static boolean anyoneStanding(PartyState party) {
        if (party == null) return false;
        for (PartyState.Member member : party.members) if (member.condition == OKAY) return true;
        return false;
    }
}
