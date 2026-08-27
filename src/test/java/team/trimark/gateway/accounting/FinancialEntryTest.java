package team.trimark.gateway.accounting;

import org.junit.jupiter.api.Test;
import team.trimark.gateway.type.BusinessDateTime;
import team.trimark.gateway.type.Money;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FinancialEntry#isValid()}.
 */
class FinancialEntryTest {
    private static final FinancialAccount CASH = FinancialAccount.of("1000", "Cash", "Cash on hand", FinancialAccountType.ASSET);
    private static final FinancialAccount REVENUE = FinancialAccount.of("4000", "Rev", "Revenue", FinancialAccountType.INCOME);

    private static FinancialEntryLine line(FinancialAccount account, long amount, String currency) {
        return FinancialEntryLine.of(account, Money.of(amount, currency));
    }

    private static FinancialEntry entry(List<FinancialEntryLine> debit, List<FinancialEntryLine> credit) {
        return FinancialEntry.of("USD", debit, credit, BusinessDateTime.of(2026, 1, 1, 0), "summary", "description");
    }

    @Test
    void balancedEntryWithMultipleDebitsToSameAccountIsValid() {
        FinancialEntry e = entry(
                List.of(line(CASH, 60, "USD"), line(CASH, 40, "USD")),
                List.of(line(REVENUE, 100, "USD")));

        assertTrue(e.isValid());
    }

    @Test
    void duplicateIdenticalLinesAreAllowed() {
        FinancialEntry e = entry(
                List.of(line(CASH, 50, "USD"), line(CASH, 50, "USD")),
                List.of(line(REVENUE, 50, "USD"), line(REVENUE, 50, "USD")));

        assertTrue(e.isValid());
    }

    @Test
    void emptySideIsInvalid() {
        assertFalse(entry(List.of(), List.of(line(REVENUE, 100, "USD"))).isValid());
        assertFalse(entry(List.of(line(CASH, 100, "USD")), List.of()).isValid());
    }

    @Test
    void allZeroButNonEmptyEntryIsValid() {
        FinancialEntry e = entry(
                List.of(line(CASH, 0, "USD")),
                List.of(line(REVENUE, 0, "USD")));

        assertTrue(e.isValid());
    }

    @Test
    void negativeLineIsInvalid() {
        FinancialEntry e = entry(
                List.of(line(CASH, -100, "USD")),
                List.of(line(REVENUE, -100, "USD")));

        assertFalse(e.isValid());
    }

    @Test
    void unbalancedEntryIsInvalid() {
        FinancialEntry e = entry(
                List.of(line(CASH, 100, "USD")),
                List.of(line(REVENUE, 99, "USD")));

        assertFalse(e.isValid());
    }

    @Test
    void lineMissingEntryCurrencyIsInvalid() {
        FinancialEntry e = entry(
                List.of(FinancialEntryLine.of(CASH, Money.of(100L, "EUR"))), // no USD conversion
                List.of(line(REVENUE, 100, "USD")));

        assertFalse(e.isValid());
    }

    @Test
    void crossCurrencyLineBalancesViaConversion() {
        Money hundredUsdAsEur = Money.of(new BigDecimal("90"), "EUR", Map.of("USD", new BigDecimal("100")));
        FinancialEntry e = entry(
                List.of(FinancialEntryLine.of(CASH, hundredUsdAsEur)),
                List.of(line(REVENUE, 100, "USD")));

        assertTrue(e.isValid());
    }

    @Test
    void nullCurrencyIsInvalid() {
        FinancialEntry e = FinancialEntry.of(null, List.of(line(CASH, 100, "USD")),
                List.of(line(REVENUE, 100, "USD")), BusinessDateTime.of(2026, 1, 1, 0), "s", "d");

        assertFalse(e.isValid());
    }
}
