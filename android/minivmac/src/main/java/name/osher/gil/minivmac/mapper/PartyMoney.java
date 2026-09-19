package name.osher.gil.minivmac.mapper;

import java.text.NumberFormat;
import java.util.Locale;

/** Read-only purse presentation; the existing converter supplies exact arithmetic. */
public final class PartyMoney {
    private PartyMoney() { }

    public static final class Purse {
        private final int[] counts;
        Purse(int[] counts) { this.counts = counts.clone(); }
        public int count(int denomination) { return counts[denomination]; }
    }

    public static String describe(PartyState party) {
        if (party == null || party.members.isEmpty()) return "Party money unavailable";
        long[] totals = new long[7];
        int unknown = 0;
        for (PartyState.Member member : party.members) {
            if (member.purse == null) { unknown++; continue; }
            for (int i = 0; i < 7; i++) totals[i] += member.purse.count(i);
        }
        NumberFormat n = NumberFormat.getIntegerInstance(Locale.US);
        StringBuilder result = new StringBuilder();
        if (unknown == 0) {
            result.append("Party total\n").append(coins(totals));
            long copper = MoneyReference.totalCopper(java.util.Arrays.copyOf(totals, 5));
            MoneyReference.Equivalent gold = MoneyReference.equivalent(copper, MoneyReference.Coin.GOLD);
            result.append("\nCoin value: ").append(n.format(gold.coins)).append(" gp");
            if (gold.copperRemainder != 0)
                result.append(" + ").append(n.format(gold.copperRemainder)).append(" cp");
            result.append("\nGems: ").append(n.format(totals[5]))
                    .append(" · Jewelry: ").append(n.format(totals[6]));
        } else {
            result.append("Party total unavailable · ").append(unknown)
                    .append(unknown == 1 ? " unreadable purse" : " unreadable purses");
        }
        for (PartyState.Member member : party.members) {
            result.append("\n\n").append(member.displayName()).append('\n');
            if (member.purse == null) { result.append("Purse unavailable"); continue; }
            long[] values = new long[5];
            for (int i = 0; i < 5; i++) values[i] = member.purse.count(i);
            result.append(coins(values)).append("\nGems: ").append(n.format(member.purse.count(5)))
                    .append(" · Jewelry: ").append(n.format(member.purse.count(6)));
        }
        return result.toString();
    }

    private static String coins(long[] counts) {
        NumberFormat n = NumberFormat.getIntegerInstance(Locale.US);
        StringBuilder result = new StringBuilder();
        MoneyReference.Coin[] coins = MoneyReference.Coin.values();
        for (int i = 0; i < 5; i++) {
            if (counts[i] == 0) continue;
            if (result.length() > 0) result.append(" · ");
            result.append(n.format(counts[i])).append(' ').append(coins[i].abbreviation);
        }
        return result.length() == 0 ? "No coins" : result.toString();
    }
}
