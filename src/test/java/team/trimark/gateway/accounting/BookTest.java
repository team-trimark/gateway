package team.trimark.gateway.accounting;

import org.junit.jupiter.api.Test;
import team.trimark.gateway.type.BusinessDateTime;
import team.trimark.gateway.type.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Book}, {@link NonEditableBook}, and {@link EditableBook}.
 */
class BookTest {
    private static final FinancialAccount CASH = FinancialAccount.of("1000", "Cash", "Cash", FinancialAccountType.ASSET);
    private static final FinancialAccount LOAN = FinancialAccount.of("2000", "Loan", "Loan payable", FinancialAccountType.LIABILITY);
    private static final FinancialAccount CAPITAL = FinancialAccount.of("3000", "Cap", "Capital", FinancialAccountType.EQUITY);
    private static final FinancialAccount REVENUE = FinancialAccount.of("4000", "Rev", "Revenue", FinancialAccountType.INCOME);
    private static final FinancialAccount EXPENSE = FinancialAccount.of("5000", "Exp", "Expense", FinancialAccountType.EXPENSE);

    private static FinancialEntryLine line(FinancialAccount account, long amount, String currency) {
        return FinancialEntryLine.of(account, Money.of(amount, currency));
    }

    private static FinancialEntry entry(String summary, int day, List<FinancialEntryLine> debit, List<FinancialEntryLine> credit) {
        return FinancialEntry.of("USD", debit, credit, BusinessDateTime.of(2026, 1, day, 0), summary, "");
    }

    /** A small self-consistent USD book: invest 1000, borrow 500, sell 300, pay 120 expense. */
    private static Book sampleBook() {
        return Book.of("USD", List.of(
                entry("invest", 1, List.of(line(CASH, 1000, "USD")), List.of(line(CAPITAL, 1000, "USD"))),
                entry("borrow", 5, List.of(line(CASH, 500, "USD")), List.of(line(LOAN, 500, "USD"))),
                entry("sale", 10, List.of(line(CASH, 300, "USD")), List.of(line(REVENUE, 300, "USD"))),
                entry("rent", 8, List.of(line(EXPENSE, 120, "USD")), List.of(line(CASH, 120, "USD")))));
    }

    private static BigDecimal amount(Money m) {
        return m.getAmount();
    }

    @Test
    void balancesAreTypeNormalized() {
        Book b = sampleBook();

        assertTrue(b.isValid());
        assertEquals(0, amount(b.getBalance("1000")).compareTo(new BigDecimal("1680"))); // 1000+500+300-120
        assertEquals(0, amount(b.getBalance("2000")).compareTo(new BigDecimal("500")));
        assertEquals(0, amount(b.getBalance("3000")).compareTo(new BigDecimal("1000")));
    }

    @Test
    void rawDebitAndCreditTotals() {
        Book b = sampleBook();

        assertEquals(0, amount(b.getDebitTotal("1000")).compareTo(new BigDecimal("1800"))); // 1000+500+300
        assertEquals(0, amount(b.getCreditTotal("1000")).compareTo(new BigDecimal("120")));
    }

    @Test
    void aggregatesAndAccountingIdentity() {
        Book b = sampleBook();

        assertEquals(0, amount(b.getTotalIncome()).compareTo(new BigDecimal("300")));
        assertEquals(0, amount(b.getTotalExpenditure()).compareTo(new BigDecimal("120")));
        assertEquals(0, amount(b.getChangeInAssets()).compareTo(new BigDecimal("1680")));
        assertEquals(0, amount(b.getChangeInLiabilities()).compareTo(new BigDecimal("500")));
        assertEquals(0, amount(b.getChangeInEquity()).compareTo(new BigDecimal("1000")));
        assertEquals(0, amount(b.getTotalTransactionVolume()).compareTo(new BigDecimal("1920"))); // 1000+500+300+120

        // assets == liabilities + equity + income - expenditure
        BigDecimal rhs = amount(b.getChangeInLiabilities())
                .add(amount(b.getChangeInEquity()))
                .add(amount(b.getTotalIncome()))
                .subtract(amount(b.getTotalExpenditure()));
        assertEquals(0, amount(b.getChangeInAssets()).compareTo(rhs));
    }

    @Test
    void residualIsZeroForSingleCurrencyBook() {
        assertEquals(0, amount(sampleBook().getDefaultCurrencyResidual()).compareTo(BigDecimal.ZERO));
    }

    @Test
    void earliestAndLatestTimestamps() {
        Book b = sampleBook();

        assertEquals(BusinessDateTime.of(2026, 1, 1, 0), b.getEarliestBusinessDateTime().orElseThrow());
        assertEquals(BusinessDateTime.of(2026, 1, 10, 0), b.getLatestBusinessDateTime().orElseThrow());
        assertTrue(Book.of("USD", List.of()).getEarliestBusinessDateTime().isEmpty());
    }

    @Test
    void accountIdsAndTypes() {
        Book b = sampleBook();

        assertEquals(FinancialAccountType.ASSET, b.getAccountType("1000").orElseThrow());
        assertEquals(FinancialAccountType.INCOME, b.getAccountType("4000").orElseThrow());
        assertTrue(b.getAccountType("9999").isEmpty());
        assertTrue(b.getAccountIds().containsAll(List.of("1000", "2000", "3000", "4000", "5000")));
    }

    @Test
    void weightedAverageExchangeRatePerAccount() {
        FinancialAccount eurCash = FinancialAccount.of("1100", "EURCash", "EUR cash", FinancialAccountType.ASSET);
        Money eur100 = Money.of(new BigDecimal("100"), "EUR", Map.of("USD", new BigDecimal("90")));  // 0.90
        Money eur200 = Money.of(new BigDecimal("200"), "EUR", Map.of("USD", new BigDecimal("190"))); // 0.95

        Book b = Book.of("USD", List.of(
                FinancialEntry.of("EUR", List.of(FinancialEntryLine.of(eurCash, eur100)),
                        List.of(FinancialEntryLine.of(CAPITAL, eur100)), BusinessDateTime.of(2026, 2, 1, 0), "e1", ""),
                FinancialEntry.of("EUR", List.of(FinancialEntryLine.of(eurCash, eur200)),
                        List.of(FinancialEntryLine.of(CAPITAL, eur200)), BusinessDateTime.of(2026, 2, 2, 0), "e2", "")));

        assertTrue(b.isValid());
        // (90 + 190) / (100 + 200) = 280 / 300 = 0.9333...
        assertEquals(new BigDecimal("0.933333"),
                b.getWeightedAverageExchangeRate("1100", "EUR").orElseThrow().setScale(6, RoundingMode.HALF_EVEN));
        // Only the foreign currency is reported; the default currency is not a position.
        assertEquals(List.of("EUR"), List.copyOf(b.getWeightedAverageExchangeRates("1100").keySet()));
        assertTrue(b.getWeightedAverageExchangeRate("1100", "JPY").isEmpty());
    }

    @Test
    void crossCurrencyResidualIsReportedButNotFatal() {
        // Balanced in EUR (10 == 10), but the USD notations differ by 1.
        Money debit = Money.of(new BigDecimal("10"), "EUR", Map.of("USD", new BigDecimal("13")));
        Money credit = Money.of(new BigDecimal("10"), "EUR", Map.of("USD", new BigDecimal("12")));
        Book b = Book.of("USD", List.of(FinancialEntry.of("EUR",
                List.of(FinancialEntryLine.of(CASH, debit)), List.of(FinancialEntryLine.of(CAPITAL, credit)),
                BusinessDateTime.of(2026, 3, 1, 0), "drift", "")));

        assertTrue(b.isValid()); // residual drift alone does not invalidate
        assertEquals(0, amount(b.getDefaultCurrencyResidual()).compareTo(BigDecimal.ONE));
    }

    @Test
    void conflictingAccountTypeInvalidatesBook() {
        FinancialAccount cashAsLiability = FinancialAccount.of("1000", "Cash", "Cash", FinancialAccountType.LIABILITY);
        Book b = Book.of("USD", List.of(
                entry("ok", 1, List.of(line(CASH, 10, "USD")), List.of(line(REVENUE, 10, "USD"))),
                entry("bad", 2, List.of(line(cashAsLiability, 10, "USD")), List.of(line(REVENUE, 10, "USD")))));

        assertFalse(b.isValid());
        assertTrue(b.validationErrors().stream().anyMatch(e -> e.contains("conflicting types")));
    }

    @Test
    void missingDefaultCurrencyNotationInvalidatesBook() {
        Book b = Book.of("USD", List.of(FinancialEntry.of("EUR",
                List.of(FinancialEntryLine.of(CASH, Money.of(5L, "EUR"))),
                List.of(FinancialEntryLine.of(CAPITAL, Money.of(5L, "EUR"))),
                BusinessDateTime.of(2026, 1, 1, 0), "noUsd", "")));

        assertFalse(b.isValid());
        assertTrue(b.validationErrors().stream().anyMatch(e -> e.contains("no notation in default currency")));
        // Aggregations over an unconvertible book fail loudly rather than silently dropping a line.
        assertThrows(IllegalStateException.class, b::getTotalTransactionVolume);
    }

    @Test
    void unbalancedEntryInvalidatesBook() {
        Book b = Book.of("USD", List.of(
                entry("bad", 1, List.of(line(CASH, 100, "USD")), List.of(line(REVENUE, 99, "USD")))));

        assertFalse(b.isValid());
        assertTrue(b.validationErrors().stream().anyMatch(e -> e.contains("not balanced")));
    }

    @Test
    void editableBookMutates() {
        Book b = Book.editable("USD");
        FinancialEntry e = entry("invest", 1, List.of(line(CASH, 1000, "USD")), List.of(line(CAPITAL, 1000, "USD")));

        b.addEntry(e);
        b.addEntries(List.of(e, e));
        assertEquals(3, b.getEntries().size());

        b.removeEntryAt(0);
        assertEquals(2, b.getEntries().size());

        b.setDefaultCurrency("EUR");
        assertEquals("EUR", b.getDefaultCurrency());

        b.clearEntries();
        assertTrue(b.getEntries().isEmpty());
    }

    @Test
    void editableBookEntriesViewIsUnmodifiable() {
        Book b = Book.editable("USD", List.of(
                entry("invest", 1, List.of(line(CASH, 1000, "USD")), List.of(line(CAPITAL, 1000, "USD")))));

        assertThrows(UnsupportedOperationException.class, () -> b.getEntries().clear());
    }

    @Test
    void nonEditableBookRejectsEveryMutation() {
        Book b = sampleBook();
        FinancialEntry e = entry("x", 1, List.of(line(CASH, 1, "USD")), List.of(line(REVENUE, 1, "USD")));

        assertThrows(UnsupportedOperationException.class, () -> b.addEntry(e));
        assertThrows(UnsupportedOperationException.class, () -> b.addEntries(List.of(e)));
        assertThrows(UnsupportedOperationException.class, () -> b.removeEntryAt(0));
        assertThrows(UnsupportedOperationException.class, b::clearEntries);
        assertThrows(UnsupportedOperationException.class, () -> b.setDefaultCurrency("EUR"));
    }
}
