package team.trimark.gateway.type;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Money}.
 */
class MoneyTest {
    @Test
    void getAmountInCurrencyReadsBaseAndConversions() {
        Money m = Money.of(new BigDecimal("100"), "USD", Map.of("EUR", new BigDecimal("90")));

        assertEquals(new BigDecimal("100"), m.getAmountInCurrency("USD").orElseThrow());
        assertEquals(new BigDecimal("90"), m.getAmountInCurrency("EUR").orElseThrow());
        assertTrue(m.getAmountInCurrency("KRW").isEmpty());
        assertTrue(m.hasCurrency("USD"));
        assertTrue(m.hasCurrency("EUR"));
        assertFalse(m.hasCurrency("KRW"));
    }

    @Test
    void rebaseUsesExistingConversionAsTheNewBase() {
        Money m = Money.of(new BigDecimal("100"), "USD", Map.of("EUR", new BigDecimal("90")));
        Money rebased = m.rebase("EUR");

        assertEquals("EUR", rebased.getCurrency());
        assertEquals(0, rebased.getAmount().compareTo(new BigDecimal("90")));
        // The old base is folded into the conversions.
        assertEquals(0, rebased.getAmountInCurrency("USD").orElseThrow().compareTo(new BigDecimal("100")));
    }

    @Test
    void rebaseKeepsPrecisionForNonRoundRates() {
        Money m = Money.of(new BigDecimal("100"), "USD", Map.of("EUR", new BigDecimal("33.33")));

        assertEquals(0, m.rebase("EUR").getAmount().compareTo(new BigDecimal("33.33")));
    }

    @Test
    void rebaseWithTargetPerIncumbentRate() {
        // 0.9 EUR per 1 USD; incumbent (USD) is the denominator.
        Money rebased = Money.of(100L, "USD").rebase("EUR", new BigDecimal("0.9"), false);

        assertEquals(0, rebased.getAmount().compareTo(new BigDecimal("90")));
    }

    @Test
    void rebaseWithIncumbentPerTargetRate() {
        // 1.1111... USD per 1 EUR; incumbent (USD) is the numerator, so the rate is inverted.
        Money rebased = Money.of(100L, "USD").rebase("EUR", new BigDecimal("1.1111111111"), true);

        assertEquals(new BigDecimal("90.00"), rebased.getAmount().setScale(2, RoundingMode.HALF_EVEN));
    }

    @Test
    void rebaseRejectsNonPositiveRate() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(1L, "USD").rebase("EUR", BigDecimal.ZERO, false));
        assertThrows(IllegalArgumentException.class, () -> Money.of(1L, "USD").rebase("EUR", new BigDecimal("-1"), false));
        assertThrows(IllegalArgumentException.class, () -> Money.of(1L, "USD").rebase("EUR", null, false));
    }

    @Test
    void rebaseOfNonZeroWithoutConversionThrows() {
        assertThrows(IllegalArgumentException.class, () -> Money.of(100L, "USD").rebase("EUR"));
    }

    @Test
    void zeroRebaseIsFailureProofEvenWithoutAConversion() {
        Money zero = Money.of(0L, "USD");
        Money rebased = zero.rebase("EUR");

        assertEquals("EUR", rebased.getCurrency());
        assertEquals(0, rebased.getAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    void compareToExpressesOtherInThisCurrency() {
        Money aUSD = Money.of(new BigDecimal("100"), "USD", Map.of("EUR", new BigDecimal("90")));

        assertEquals(-1, Integer.signum(aUSD.compareTo(Money.of(95L, "EUR"))));
        assertEquals(0, aUSD.compareTo(Money.of(90L, "EUR")));
        assertEquals(1, Integer.signum(aUSD.compareTo(Money.of(80L, "EUR"))));
    }

    @Test
    void compareToFallsBackToThisInOtherCurrency() {
        // 'this' is USD with no EUR conversion; 'other' is EUR carrying a USD conversion.
        Money thisUSD = Money.of(100L, "USD");
        Money otherEUR = Money.of(new BigDecimal("90"), "EUR", Map.of("USD", new BigDecimal("120")));

        // this(100 USD) vs other(120 USD) -> less than.
        assertEquals(-1, Integer.signum(thisUSD.compareTo(otherEUR)));
    }

    @Test
    void compareToThrowsWhenNoSharedCurrency() {
        assertThrows(RuntimeException.class, () -> Money.of(1L, "USD").compareTo(Money.of(1L, "EUR")));
    }

    @Test
    void compareToWithBigDecimalAndNumber() {
        assertEquals(0, Money.of(10L, "USD").compareTo(new BigDecimal("10")));
        assertEquals(0, Money.of(10L, "USD").compareTo(10.0));
    }

    @Test
    void equalsIsNumericAndScaleInsensitive() {
        assertEquals(Money.of(new BigDecimal("2.00"), "USD"), Money.of(2L, "USD"));
        assertEquals(Money.of(2L, "USD").hashCode(), Money.of(new BigDecimal("2.00"), "USD").hashCode());
        assertNotEquals(Money.of(2L, "USD"), Money.of(2L, "EUR"));
    }

    @Test
    void serializationRoundTrips() {
        Money m = Money.of(new BigDecimal("100.50"), "USD", Map.of("EUR", new BigDecimal("90"), "KRW", new BigDecimal("130000")));

        assertEquals(m, Money.fromString(m.toString()));
    }

    @Test
    void serializationRoundTripsWithoutConversions() {
        Money m = Money.of(new BigDecimal("-12.34"), "USD");

        assertEquals(m, Money.fromString(m.toString()));
    }

    @Test
    void fromStringRejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> Money.fromString("nonsense"));
        assertThrows(NullPointerException.class, () -> Money.fromString(null));
    }
}
