package name.osher.gil.minivmac.mapper;

import org.junit.Test;
import java.util.Arrays;
import static name.osher.gil.minivmac.mapper.MoneyReference.Coin.*;
import static org.junit.Assert.*;

public class MoneyReferenceTest {
    @Test public void matchesOriginalMacintoshGoldEquivalents() {
        assertEquals(200, MoneyReference.totalCopper(new long[]{200, 0, 0, 0, 0}));
        assertEquals(200, MoneyReference.totalCopper(new long[]{0, 20, 0, 0, 0}));
        assertEquals(200, MoneyReference.totalCopper(new long[]{0, 0, 2, 0, 0}));
        assertEquals(200, MoneyReference.totalCopper(new long[]{0, 0, 0, 1, 0}));
        assertEquals(5, MoneyReference.equivalent(
                MoneyReference.totalCopper(new long[]{0, 0, 0, 0, 1}), GOLD).coins);
    }

    @Test public void addsMixedCoinsAndKeepsExactRemainders() {
        long total = MoneyReference.totalCopper(new long[]{1, 1, 1, 1, 1});
        assertEquals(1311, total);
        MoneyReference.Equivalent gold = MoneyReference.equivalent(total, GOLD);
        assertEquals(6, gold.coins);
        assertEquals(111, gold.copperRemainder);
        MoneyReference.Equivalent platinum = MoneyReference.equivalent(total, PLATINUM);
        assertEquals(1, platinum.coins);
        assertEquals(311, platinum.copperRemainder);
        assertArrayEquals(new long[]{1, 1, 1, 1, 1}, MoneyReference.compactChange(total));
    }

    @Test public void zeroAndSubCoinTotalsRemainExact() {
        assertEquals(0, MoneyReference.totalCopper(new long[5]));
        assertArrayEquals(new long[5], MoneyReference.compactChange(0));
        MoneyReference.Equivalent gold = MoneyReference.equivalent(199, GOLD);
        assertEquals(0, gold.coins);
        assertEquals(199, gold.copperRemainder);
        assertArrayEquals(new long[]{9, 9, 1, 0, 0}, MoneyReference.compactChange(199));
    }

    @Test public void boundedLargeAmountsDoNotOverflowOrLoseValue() {
        long[] maximum = new long[5];
        Arrays.fill(maximum, MoneyReference.MAX_COINS);
        long total = MoneyReference.totalCopper(maximum);
        assertEquals(1_310_999_998_689L, total);
        long[] change = MoneyReference.compactChange(total);
        long rebuilt = 0;
        for (MoneyReference.Coin coin : MoneyReference.Coin.values()) {
            MoneyReference.Equivalent converted = MoneyReference.equivalent(total, coin);
            assertEquals(total, converted.coins * coin.copperValue + converted.copperRemainder);
            assertTrue(converted.copperRemainder < coin.copperValue);
            rebuilt += change[coin.ordinal()] * coin.copperValue;
        }
        assertEquals(total, rebuilt);
    }

    @Test public void rejectsNegativeCountsInvalidShapesAndOverflowSizedValues() {
        assertThrows(IllegalArgumentException.class, () -> MoneyReference.totalCopper(null));
        assertThrows(IllegalArgumentException.class, () -> MoneyReference.totalCopper(new long[4]));
        assertThrows(IllegalArgumentException.class, () -> MoneyReference.totalCopper(new long[]{-1, 0, 0, 0, 0}));
        assertThrows(IllegalArgumentException.class, () -> MoneyReference.totalCopper(new long[]{0, 0, 0, 0, Long.MAX_VALUE}));
        assertThrows(IllegalArgumentException.class, () -> MoneyReference.equivalent(Long.MAX_VALUE, GOLD));
        assertThrows(IllegalArgumentException.class, () -> MoneyReference.compactChange(-1));
    }

    @Test public void keypadBoundsPreserveThePreviousAmountOnRejectedInput() {
        assertEquals(0, MoneyReference.appendDigit(0, 0));
        assertEquals(9, MoneyReference.appendDigit(0, 9));
        assertEquals(999_999_999, MoneyReference.appendDigit(99_999_999, 9));
        assertThrows(IllegalArgumentException.class, () -> MoneyReference.appendDigit(999_999_999, 0));
        assertThrows(IllegalArgumentException.class, () -> MoneyReference.appendDigit(0, -1));
        assertThrows(IllegalArgumentException.class, () -> MoneyReference.appendDigit(0, 10));
    }
}
