package name.osher.gil.minivmac.mapper;

import java.util.ArrayList;
import java.util.List;

/**
 * The handful of things the party is waiting for somebody to do.
 *
 * All of it is already on screen -- a T here, a word there, a bar that is
 * shorter than it was -- and all of it is easy to miss when six rows are
 * competing for a glance. Gathered into one line it answers the question you
 * actually have between fights: is there anything to deal with before moving
 * on?
 *
 * It reports; it does not advise. "2 can train" is a fact about what the game
 * will allow, not a suggestion to go to the training hall, and nothing here
 * decides an order or nags about what is left.
 */
public final class PartyChores {
    /** The heading shown when there is nothing outstanding. */
    public static final String NOTHING = "PARTY · TAP FOR DETAILS";

    private PartyChores() {}

    /**
     * One line for the party pane's heading: the chores, or the plain heading
     * when there are none.
     */
    public static String summary(PartyState party) {
        List<String> parts = new ArrayList<>(4);
        if (party != null) {
            int train = 0, rest = 0, down = 0, hurt = 0;
            for (PartyState.Member member : party.members) {
                if (member.readyToTrain()) train++;
                if (member.spellsAwaitingRestTotal() > 0) rest++;
                if (member.downLabel() != null) down++;
                else if (member.injured()) hurt++;
            }
            // Ordered by how much it matters, not by how the record is laid
            // out: somebody dying is not a footnote to somebody's spell slots.
            if (down > 0) parts.add(count(down, "down"));
            if (hurt > 0) parts.add(count(hurt, "hurt"));
            if (rest > 0) parts.add(count(rest, "needs rest", "need rest"));
            if (train > 0) parts.add(count(train, "can train"));
        }
        if (parts.isEmpty()) return NOTHING;
        StringBuilder line = new StringBuilder(parts.get(0));
        for (int i = 1; i < parts.size(); i++) line.append(" · ").append(parts.get(i));
        return line.toString();
    }

    private static String count(int many, String word) { return count(many, word, word); }

    private static String count(int many, String one, String several) {
        return many + " " + (many == 1 ? one : several);
    }
}
