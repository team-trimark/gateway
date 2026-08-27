package team.trimark.gateway.type;

import org.junit.jupiter.api.Test;
import team.trimark.gateway.accounting.*;
import team.trimark.gateway.math.BusinessMath;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for "residual FX" money - a figure whose base-currency amount is zero while a foreign notation is non-zero.
 * Such a value is structurally lossy (its implied rate is undefined), so these tests pin down how it flows through
 * {@link Money}, {@link BusinessMath}, and {@link Book}.
 */
class ResidualFxTest {
    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    /** 0 USD, but 5 EUR. */
    private static Money residual() {
        return Money.of(BigDecimal.ZERO, "USD", Map.of("EUR", bd("5")));
    }

    @Test
    void accessorsSeeZeroBaseAndNonZeroForeign() {
        Money r = residual();

        assertEquals(0, r.getAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, r.getAmountInCurrency("USD").orElseThrow().compareTo(BigDecimal.ZERO));
        assertEquals(0, r.getAmountInCurrency("EUR").orElseThrow().compareTo(bd("5")));
        assertTrue(r.hasCurrency("USD"));
        assertTrue(r.hasCurrency("EUR"));
    }

    @Test
    void rebaseToKnownForeignPreservesTheResidualValue() {
        Money asEur = residual().rebase("EUR");

        assertEquals("EUR", asEur.getCurrency());
        assertEquals(0, asEur.getAmount().compareTo(bd("5")));                       // NOT zeroed out
        assertEquals(0, asEur.getAmountInCurrency("USD").orElseThrow().compareTo(BigDecimal.ZERO)); // old base folded in
    }

    @Test
    void rebaseRoundTripsBackToTheResidual() {
        Money back = residual().rebase("EUR").rebase("USD");

        assertEquals("USD", back.getCurrency());
        assertEquals(0, back.getAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, back.getAmountInCurrency("EUR").orElseThrow().compareTo(bd("5")));
    }

    @Test
    void rebaseToUnknownCurrencyWithZeroBaseStillYieldsZero() {
        // No KRW notation and the base is zero -> failure-proof zero (there is no rate to invent).
        Money krw = residual().rebase("KRW");

        assertEquals("KRW", krw.getCurrency());
        assertEquals(0, krw.getAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    void equalsHashCodeAndSerializationRoundTrip() {
        Money r = residual();

        assertEquals(r, Money.of(BigDecimal.ZERO, "USD", Map.of("EUR", bd("5.00")))); // numeric equality
        assertEquals(r.hashCode(), Money.of(BigDecimal.ZERO, "USD", Map.of("EUR", bd("5"))).hashCode());
        assertEquals(r, Money.fromString(r.toString()));
    }

    @Test
    void compareToUsesTheSharedForeignNotation() {
        Money r = residual(); // 0 USD / 5 EUR

        assertEquals(1, Integer.signum(r.compareTo(Money.of(3L, "EUR"))));  // 5 EUR > 3 EUR
        assertEquals(-1, Integer.signum(r.compareTo(Money.of(9L, "EUR")))); // 5 EUR < 9 EUR
        assertEquals(0, r.compareTo(Money.of(5L, "EUR")));
    }

    @Test
    void businessMathBlendsResiduals() {
        Money sum = BusinessMath.add(residual(), residual());
        assertEquals(0, sum.getAmount().compareTo(BigDecimal.ZERO));                      // 0 USD
        assertEquals(0, sum.getAmountInCurrency("EUR").orElseThrow().compareTo(bd("10"))); // 5 + 5 EUR

        Money diff = BusinessMath.subtract(residual(), Money.of(BigDecimal.ZERO, "USD", Map.of("EUR", bd("2"))));
        assertEquals(0, diff.getAmountInCurrency("EUR").orElseThrow().compareTo(bd("3")));
    }

    @Test
    void businessMathAddsResidualIntoARealPosition() {
        Money real = Money.of(bd("100"), "USD", Map.of("EUR", bd("90")));
        Money sum = BusinessMath.add(real, residual());

        assertEquals(0, sum.getAmount().compareTo(bd("100")));                             // 100 + 0 USD
        assertEquals(0, sum.getAmountInCurrency("EUR").orElseThrow().compareTo(bd("95"))); // 90 + 5 EUR
    }

    @Test
    void businessMathDivideKeepsForeignButFailsOnZeroBaseDenominator() {
        Money numerator = Money.of(bd("0"), "USD", Map.of("EUR", bd("6")));
        Money denominator = Money.of(bd("2"), "USD", Map.of("EUR", bd("3")));

        Money quotient = BusinessMath.divide(numerator, denominator);
        assertEquals(0, quotient.getAmount().compareTo(BigDecimal.ZERO));                       // 0 / 2 USD
        assertEquals(0, quotient.getAmountInCurrency("EUR").orElseThrow().compareTo(bd("2")));  // 6 / 3 EUR

        // A residual as the denominator: zero in the base currency -> division by zero.
        assertThrows(IllegalArgumentException.class, () -> BusinessMath.divide(denominator, residual()));
    }

    @Test
    void negateAndAbsOnASignedResidual() {
        Money signed = Money.of(BigDecimal.ZERO, "USD", Map.of("EUR", bd("-5")));

        assertEquals(0, BusinessMath.negate(signed).getAmountInCurrency("EUR").orElseThrow().compareTo(bd("5")));
        assertEquals(0, BusinessMath.abs(signed).getAmountInCurrency("EUR").orElseThrow().compareTo(bd("5")));
    }

    @Test
    void bookWithResidualLinesIsValidAndZeroInDefaultCurrency() {
        FinancialAccount cash = FinancialAccount.of("1000", "Cash", "Cash", FinancialAccountType.ASSET);
        FinancialAccount revenue = FinancialAccount.of("4000", "Rev", "Revenue", FinancialAccountType.INCOME);

        // Entry booked in EUR (balances 5 == 5 EUR), but every line reads 0 in the USD default.
        FinancialEntry e = FinancialEntry.of("EUR",
                List.of(FinancialEntryLine.of(cash, residual())),
                List.of(FinancialEntryLine.of(revenue, residual())),
                BusinessDateTime.of(2026, 1, 1, 0), "residual sale", "");
        Book b = Book.of("USD", List.of(e));

        assertTrue(b.isValid());
        assertEquals(0, b.getBalance("1000").getAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, b.getTotalIncome().getAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, b.getDefaultCurrencyResidual().getAmount().compareTo(BigDecimal.ZERO));
    }
}
