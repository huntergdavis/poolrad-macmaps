package name.osher.gil.minivmac;

import android.app.Activity;
import java.util.function.Supplier;
import name.osher.gil.minivmac.mapper.PartyState;

/** The game's validated chain order, without adding another mark to every map row. */
public final class PartyOrderDialog {
    private PartyOrderDialog() { }

    public static void show(Activity activity, Supplier<PartyState> reading) {
        LiveTextReferenceDialog.show(activity, "Marching order",
                "The game's current order, first to last.", () -> {
                    PartyState party = reading.get();
                    if (party == null || party.members.isEmpty()) return "Marching order unavailable";
                    StringBuilder rows = new StringBuilder();
                    for (int i = 0; i < party.members.size(); i++) {
                        if (i > 0) rows.append("\n");
                        rows.append(i + 1).append(". ").append(party.members.get(i).displayName());
                    }
                    return rows.toString();
                }, "Change the order in the game: Encamp → Alter → Order.", 20, null, null);
    }
}
