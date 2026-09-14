package name.osher.gil.minivmac.mapper;

/** Original Macintosh Pool of Radiance: supplied tables, MONEY CONVERSIONS. */
public final class MoneyReference {
    public static final long MAX_COINS = 999_999_999L;
    public static final long MAX_TOTAL_COPPER = MAX_COINS * 1311L;

    public enum Coin {
        COPPER("Copper", "cp", 1),
        SILVER("Silver", "sp", 10),
        ELECTRUM("Electrum", "ep", 100),
        GOLD("Gold", "gp", 200),
        PLATINUM("Platinum", "pp", 1000);

        public final String label;
        public final String abbreviation;
        public final long copperValue;

        Coin(String label, String abbreviation, long copperValue) {
            this.label = label;
            this.abbreviation = abbreviation;
            this.copperValue = copperValue;
        }
    }

    public static final class Equivalent {
        public final long coins;
        public final long copperRemainder;

        private Equivalent(long coins, long copperRemainder) {
            this.coins = coins;
            this.copperRemainder = copperRemainder;
        }
    }

    private MoneyReference() {}

    /** Counts follow Coin.values(): copper, silver, electrum, gold, platinum. */
    public static long totalCopper(long[] counts) {
        Coin[] coins = Coin.values();
        if (counts == null || counts.length != coins.length) {
            throw new IllegalArgumentException("Enter all five coin counts");
        }
        long total = 0;
        for (int i = 0; i < coins.length; i++) {
            checkCount(counts[i]);
            // The explicit nine-digit bound keeps every product and the sum
            // below 1.311 trillion, well within long without floating point.
            total += counts[i] * coins[i].copperValue;
        }
        return total;
    }

    public static Equivalent equivalent(long totalCopper, Coin denomination) {
        checkTotal(totalCopper);
        if (denomination == null) throw new IllegalArgumentException("Choose a coin type");
        return new Equivalent(totalCopper / denomination.copperValue,
                totalCopper % denomination.copperValue);
    }

    /** Exact descending-denomination change, returned in Coin.values() order. */
    public static long[] compactChange(long totalCopper) {
        checkTotal(totalCopper);
        Coin[] coins = Coin.values();
        long[] change = new long[coins.length];
        for (int i = coins.length - 1; i >= 0; i--) {
            change[i] = totalCopper / coins[i].copperValue;
            totalCopper %= coins[i].copperValue;
        }
        return change;
    }

    public static long appendDigit(long current, int digit) {
        checkCount(current);
        if (digit < 0 || digit > 9 || current > (MAX_COINS - digit) / 10) {
            throw new IllegalArgumentException("Up to 999,999,999 of each coin");
        }
        return current * 10 + digit;
    }

    private static void checkCount(long count) {
        if (count < 0 || count > MAX_COINS) {
            throw new IllegalArgumentException("Use 0 to 999,999,999 of each coin");
        }
    }

    private static void checkTotal(long total) {
        if (total < 0 || total > MAX_TOTAL_COPPER) {
            throw new IllegalArgumentException("Coin total is out of range");
        }
    }
}
