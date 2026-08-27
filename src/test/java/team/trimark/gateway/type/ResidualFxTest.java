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
    void businessMathScalarDivideScalesTheResidual() {
        Money quotient = BusinessMath.divide(Money.of(bd("0"), "USD", Map.of("EUR", bd("6"))), bd("2"));

        assertEquals(0, quotient.getAmount().compareTo(BigDecimal.ZERO));                       // 0 / 2 USD
        assertEquals(0, quotient.getAmountInCurrency("EUR").orElseThrow().compareTo(bd("3")));  // 6 / 2 EUR
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

    // ---- mirror case: non-zero base, zero FX notation (a USD position sitting at 0 in a KRW book) ----

    /** 100 USD, but 0 KRW. */
    private static Money usdResidualInKrw() {
        return Money.of(bd("100"), "USD", Map.of("KRW", BigDecimal.ZERO));
    }

    @Test
    void mirrorAccessorsSeeNonZeroBaseAndZeroForeign() {
        Money r = usdResidualInKrw();

        assertEquals(0, r.getAmount().compareTo(bd("100")));
        assertEquals(0, r.getAmountInCurrency("KRW").orElseThrow().compareTo(BigDecimal.ZERO));
        assertTrue(r.hasCurrency("KRW"));
    }

    @Test
    void mirrorRebaseTrustsTheStaleZeroNotation() {
        // rebase(String) uses the known (zero) KRW notation - it does not invent a rate.
        Money asKrw = usdResidualInKrw().rebase("KRW");

        assertEquals("KRW", asKrw.getCurrency());
        assertEquals(0, asKrw.getAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, asKrw.getAmountInCurrency("USD").orElseThrow().compareTo(bd("100"))); // old base folded in
    }

    @Test
    void mirrorRebaseToUnknownCurrencyWithNonZeroBaseThrows() {
        // No EUR notation and a non-zero base -> a rate would be required, so it throws.
        assertThrows(IllegalArgumentException.class, () -> usdResidualInKrw().rebase("EUR"));
    }

    @Test
    void fixingTheUsdResidualWithAnExplicitRate() {
        // Supplying a real rate (1300 KRW per USD) overrides the stale zero notation.
        Money fixed = usdResidualInKrw().rebase("KRW", bd("1300"), false);

        assertEquals("KRW", fixed.getCurrency());
        assertEquals(0, fixed.getAmount().compareTo(bd("130000")));               // 100 * 1300
        assertEquals(0, fixed.getAmountInCurrency("USD").orElseThrow().compareTo(bd("100")));
    }

    @Test
    void krwBookWithUsdResidualIsValidButReadsZeroUntilFixed() {
        FinancialAccount cash = FinancialAccount.of("1000", "Cash", "Cash", FinancialAccountType.ASSET);
        FinancialAccount revenue = FinancialAccount.of("4000", "Rev", "Revenue", FinancialAccountType.INCOME);

        // Booked in USD (100 == 100), but the KRW default notation is a placeholder zero.
        FinancialEntry unfixed = FinancialEntry.of("USD",
                List.of(FinancialEntryLine.of(cash, usdResidualInKrw())),
                List.of(FinancialEntryLine.of(revenue, usdResidualInKrw())),
                BusinessDateTime.of(2026, 1, 1, 0), "usd sale", "");
        Book b = Book.of("KRW", List.of(unfixed));

        assertTrue(b.isValid());
        assertEquals(0, b.getBalance("1000").getAmount().compareTo(BigDecimal.ZERO)); // 0 KRW
        // The USD position is foreign; its weighted-average rate is 0, flagging the unfixed residual.
        assertEquals(0, b.getWeightedAverageExchangeRate("1000", "USD").orElseThrow().compareTo(BigDecimal.ZERO));

        // Fixing the residual: correct the KRW notation (1300 KRW per USD) while the line stays USD-denominated.
        Money fixed = Money.of(bd("100"), "USD", Map.of("KRW", bd("130000")));
        Book fixedBook = Book.of("KRW", List.of(FinancialEntry.of("USD",
                List.of(FinancialEntryLine.of(cash, fixed)),
                List.of(FinancialEntryLine.of(revenue, fixed)),
                BusinessDateTime.of(2026, 1, 1, 0), "usd sale", "")));

        assertEquals(0, fixedBook.getBalance("1000").getAmount().compareTo(bd("130000")));
        assertEquals(0, fixedBook.getWeightedAverageExchangeRate("1000", "USD").orElseThrow().compareTo(bd("1300")));
    }
}
