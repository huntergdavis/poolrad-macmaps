package name.osher.gil.minivmac.mapper;

/**
 * What "Rest until healed" will do for this party, in the player's words, and
 * what it did afterwards. Pure Java. Counts follow the native write's rules:
 * only Okay, Unconscious and Dying members take part; the dead, petrified and
 * absent are left; tracked effects and the game clock are not touched.
 */
public final class RestSummary {
    public static final int HP = 1, CONDITION = 2, SPELLS = 4;
    private RestSummary() { }

    private static boolean restable(PartyState.Member m) {
        return m.condition == BandagePlan.OKAY || m.condition == BandagePlan.UNCONSCIOUS || m.condition == BandagePlan.DYING;
    }

    /** The confirmation text before writing; null when nothing would change. */
    public static String plan(PartyState party) {
        if (party == null || party.members.isEmpty()) return null;
        int hurt = 0, down = 0, spells = 0, left = 0;
        for (PartyState.Member m : party.members) {
            if (!restable(m)) { left++; continue; }
            if (m.currentHp < m.maxHp) hurt++;
            if (m.condition != BandagePlan.OKAY) down++;
            spells += m.spellsAwaitingRestTotal();
        }
        if (hurt == 0 && down == 0 && spells == 0) return null;
        StringBuilder out = new StringBuilder("Does what a full, uninterrupted rest would:\n");
        if (hurt > 0) out.append("\u2022 ").append(hurt).append(hurt == 1 ? " character" : " characters").append(" healed to full hit points\n");
        if (down > 0) out.append("\u2022 ").append(down).append(down == 1 ? " down character wakes" : " down characters wake").append('\n');
        if (spells > 0) out.append("\u2022 ").append(spells).append(spells == 1 ? " chosen spell" : " chosen spells").append(" memorized\n");
        if (left > 0) out.append("\u2022 ").append(left).append(left == 1 ? " member" : " members").append(" beyond rest (dead, petrified or absent) left as they are\n");
        out.append("\nThe game clock does not advance and poison is not cured. Nothing is saved.");
        return out.toString();
    }

    /** The notice after writing, from the per-member result flags (-1 = refused). */
    public static String outcome(int[] flags) {
        int healed = 0, woke = 0, memorized = 0, refused = 0;
        for (int f : flags) {
            if (f < 0) { refused++; continue; }
            if ((f & HP) != 0) healed++;
            if ((f & CONDITION) != 0) woke++;
            if ((f & SPELLS) != 0) memorized++;
        }
        if (healed == 0 && woke == 0 && memorized == 0)
            return refused > 0 ? "The party could not be read cleanly; nothing was changed." : "Everyone was already rested.";
        StringBuilder out = new StringBuilder("Rested: ");
        if (healed > 0) out.append(healed).append(" healed");
        if (woke > 0) out.append(healed > 0 ? ", " : "").append(woke).append(" awake");
        if (memorized > 0) out.append(healed + woke > 0 ? ", " : "").append(memorized).append(memorized == 1 ? " caster memorized" : " casters memorized");
        if (refused > 0) out.append("; ").append(refused).append(" refused");
        return out.append('.').toString();
    }
}
