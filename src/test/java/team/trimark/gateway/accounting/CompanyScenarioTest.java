package team.trimark.gateway.accounting;

import org.junit.jupiter.api.Test;
import team.trimark.gateway.type.BusinessDateTime;
import team.trimark.gateway.type.Money;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An end-to-end scenario for a fictional company, "Aurora Bakes Ltd." (books kept in USD), across one fiscal year.
 * It mixes single-line entries with entries that fan out into tens of debit/credit lines (bulk inventory, payroll,
 * credit sales) and finishes with a multi-line closing entry, then checks that the income, expense, asset, liability,
 * and equity figures sum correctly - both before closing and after the income and expense accounts are closed to
 * retained earnings.
 */
class CompanyScenarioTest {
    // Chart of accounts ------------------------------------------------------------------------------------------
    private static final FinancialAccount CASH = account("1000", "Cash", FinancialAccountType.ASSET);
    private static final FinancialAccount RECEIVABLE = account("1100", "Accounts Receivable", FinancialAccountType.ASSET);
    private static final FinancialAccount INVENTORY = account("1200", "Inventory", FinancialAccountType.ASSET);
    private static final FinancialAccount EQUIPMENT = account("1500", "Equipment", FinancialAccountType.ASSET);

    private static final FinancialAccount PAYABLE = account("2000", "Accounts Payable", FinancialAccountType.LIABILITY);
    private static final FinancialAccount LOAN = account("2100", "Loan Payable", FinancialAccountType.LIABILITY);

    private static final FinancialAccount COMMON_STOCK = account("3000", "Common Stock", FinancialAccountType.EQUITY);
    private static final FinancialAccount RETAINED_EARNINGS = account("3100", "Retained Earnings", FinancialAccountType.EQUITY);

    private static final FinancialAccount SALES = account("4000", "Sales Revenue", FinancialAccountType.INCOME);
    private static final FinancialAccount INTEREST_INCOME = account("4100", "Interest Income", FinancialAccountType.INCOME);

    private static final FinancialAccount COGS = account("5000", "Cost of Goods Sold", FinancialAccountType.EXPENSE);
    private static final FinancialAccount WAGES = account("5100", "Wages Expense", FinancialAccountType.EXPENSE);
    private static final FinancialAccount RENT = account("5200", "Rent Expense", FinancialAccountType.EXPENSE);
    private static final FinancialAccount UTILITIES = account("5300", "Utilities Expense", FinancialAccountType.EXPENSE);
    private static final FinancialAccount INTEREST_EXPENSE = account("5400", "Interest Expense", FinancialAccountType.EXPENSE);
    private static final FinancialAccount MARKETING = account("5500", "Marketing Expense", FinancialAccountType.EXPENSE);

    // Expected figures (hand-computed) ---------------------------------------------------------------------------
    private static final BigDecimal TOTAL_INCOME = bd(425_000);       // 420,000 sales + 5,000 interest
    private static final BigDecimal TOTAL_EXPENSE = bd(266_000);      // 90+100+36+12+20+8 (thousands)
    private static final BigDecimal NET_INCOME = bd(159_000);
    private static final BigDecimal CHANGE_ASSETS = bd(909_000);
    private static final BigDecimal CHANGE_LIABILITIES = bd(250_000); // 50,000 AP + 200,000 loan
    private static final BigDecimal EQUITY_BEFORE_CLOSING = bd(500_000);
    private static final BigDecimal EQUITY_AFTER_CLOSING = bd(659_000); // 500,000 stock + 159,000 retained
    private static final BigDecimal TRANSACTION_VOLUME = bd(1_791_000);

    // ------------------------------------------------------------------------------------------------------------

    @Test
    void figuresSumCorrectlyBeforeClosing() {
        Book book = Book.of("USD", operatingEntries());

        assertTrue(book.isValid(), () -> "book invalid: " + book.validationErrors());

        assertEqualsMoney(TOTAL_INCOME, book.getTotalIncome());
        assertEqualsMoney(TOTAL_EXPENSE, book.getTotalExpenditure());
        assertEqualsMoney(CHANGE_ASSETS, book.getChangeInAssets());
        assertEqualsMoney(CHANGE_LIABILITIES, book.getChangeInLiabilities());
        assertEqualsMoney(EQUITY_BEFORE_CLOSING, book.getChangeInEquity());
        assertEqualsMoney(TRANSACTION_VOLUME, book.getTotalTransactionVolume());

        // The books cross-foot: total debits equal total credits.
        assertEqualsMoney(BigDecimal.ZERO, book.getDefaultCurrencyResidual());

        // Fundamental identity: assets = liabilities + equity + (income - expenses).
        assertAccountingIdentity(book);
    }

    @Test
    void perAccountBalancesAreCorrect() {
        Book book = Book.of("USD", operatingEntries());

        assertEqualsMoney(bd(669_000), book.getBalance("1000")); // Cash
        assertEqualsMoney(bd(60_000), book.getBalance("1100"));  // Receivable
        assertEqualsMoney(bd(30_000), book.getBalance("1200"));  // Inventory
        assertEqualsMoney(bd(150_000), book.getBalance("1500")); // Equipment
        assertEqualsMoney(bd(50_000), book.getBalance("2000"));  // Payable
        assertEqualsMoney(bd(200_000), book.getBalance("2100")); // Loan
        assertEqualsMoney(bd(500_000), book.getBalance("3000")); // Common Stock
        assertEqualsMoney(bd(420_000), book.getBalance("4000")); // Sales Revenue
        assertEqualsMoney(bd(100_000), book.getBalance("5100")); // Wages Expense

        // Sum of asset balances equals the aggregate change in assets.
        BigDecimal assetSum = book.getBalance("1000").getAmount()
                .add(book.getBalance("1100").getAmount())
                .add(book.getBalance("1200").getAmount())
                .add(book.getBalance("1500").getAmount());
        assertEquals(0, assetSum.compareTo(CHANGE_ASSETS));
    }

    @Test
    void closingEntryZeroesIncomeAndExpenseAndRollsIntoEquity() {
        List<FinancialEntry> withClosing = new ArrayList<>(operatingEntries());
        withClosing.add(closingEntry());
        Book book = Book.of("USD", withClosing);

        assertTrue(book.isValid(), () -> "book invalid: " + book.validationErrors());

        // Income and expense accounts are closed out to zero.
        assertEqualsMoney(BigDecimal.ZERO, book.getTotalIncome());
        assertEqualsMoney(BigDecimal.ZERO, book.getTotalExpenditure());

        // Net income has rolled into retained earnings; assets and liabilities are untouched by closing.
        assertEqualsMoney(NET_INCOME, book.getBalance("3100"));       // Retained Earnings
        assertEqualsMoney(EQUITY_AFTER_CLOSING, book.getChangeInEquity());
        assertEqualsMoney(CHANGE_ASSETS, book.getChangeInAssets());
        assertEqualsMoney(CHANGE_LIABILITIES, book.getChangeInLiabilities());
        assertEqualsMoney(BigDecimal.ZERO, book.getDefaultCurrencyResidual());

        // With income and expense zeroed, the identity reduces to assets = liabilities + equity.
        assertAccountingIdentity(book);
        assertEquals(0, book.getChangeInAssets().getAmount()
                .compareTo(book.getChangeInLiabilities().getAmount().add(book.getChangeInEquity().getAmount())));
    }

    @Test
    void multiLineEntriesReallyDoFanOut() {
        // Sanity-check that the "tens of lines" entries are what the scenario claims.
        List<FinancialEntry> entries = operatingEntries();
        assertEquals(24, lineCount(entries.get(3)));  // bulk inventory: 12 + 12
        assertEquals(40, lineCount(entries.get(4)));  // payroll: 20 + 20
        assertEquals(30, lineCount(entries.get(6)));  // credit sales: 15 + 15
    }

    // Scenario construction --------------------------------------------------------------------------------------

    /** The year's operating journal (E1..E15): a mix of single-line and many-line entries. */
    private static List<FinancialEntry> operatingEntries() {
        List<FinancialEntry> entries = new ArrayList<>();

        // E1 - owner invests capital (single line each side)
        entries.add(entry("Capital injection", 1, 2, List.of(line(CASH, 500_000)), List.of(line(COMMON_STOCK, 500_000))));
        // E2 - bank loan drawn down
        entries.add(entry("Bank loan", 1, 15, List.of(line(CASH, 200_000)), List.of(line(LOAN, 200_000))));
        // E3 - buy equipment for cash
        entries.add(entry("Buy ovens", 1, 20, List.of(line(EQUIPMENT, 150_000)), List.of(line(CASH, 150_000))));

        // E4 - bulk inventory from 12 suppliers on account (24 lines)
        entries.add(entry("Bulk flour & supplies", 2, 1, repeat(INVENTORY, 10_000, 12), repeat(PAYABLE, 10_000, 12)));
        // E5 - monthly payroll across 20 employees (40 lines)
        entries.add(entry("Payroll", 2, 28, repeat(WAGES, 5_000, 20), repeat(CASH, 5_000, 20)));

        // E6 - cash sales (single)
        entries.add(entry("Counter sales", 3, 31, List.of(line(CASH, 300_000)), List.of(line(SALES, 300_000))));
        // E7 - credit sales to 15 wholesale customers (30 lines)
        entries.add(entry("Wholesale sales", 4, 30, repeat(RECEIVABLE, 8_000, 15), repeat(SALES, 8_000, 15)));

        // E8 - cost of goods sold (single)
        entries.add(entry("Cost of goods sold", 4, 30, List.of(line(COGS, 90_000)), List.of(line(INVENTORY, 90_000))));
        // E9..E11 - operating expenses (single)
        entries.add(entry("Rent", 5, 1, List.of(line(RENT, 36_000)), List.of(line(CASH, 36_000))));
        entries.add(entry("Utilities", 5, 2, List.of(line(UTILITIES, 12_000)), List.of(line(CASH, 12_000))));
        entries.add(entry("Marketing", 5, 3, List.of(line(MARKETING, 20_000)), List.of(line(CASH, 20_000))));

        // E12 - interest income (single)
        entries.add(entry("Interest income", 6, 30, List.of(line(CASH, 5_000)), List.of(line(INTEREST_INCOME, 5_000))));
        // E13 - interest on the loan (single)
        entries.add(entry("Interest expense", 6, 30, List.of(line(INTEREST_EXPENSE, 8_000)), List.of(line(CASH, 8_000))));

        // E14 - collect some receivables (single)
        entries.add(entry("Collect receivables", 7, 15, List.of(line(CASH, 60_000)), List.of(line(RECEIVABLE, 60_000))));
        // E15 - pay down some payables (single)
        entries.add(entry("Pay suppliers", 8, 15, List.of(line(PAYABLE, 70_000)), List.of(line(CASH, 70_000))));

        return entries;
    }

    /** The multi-line closing entry: close income and expense to retained earnings at year end. */
    private static FinancialEntry closingEntry() {
        List<FinancialEntryLine> debit = List.of(
                line(SALES, 420_000),
                line(INTEREST_INCOME, 5_000));
        List<FinancialEntryLine> credit = List.of(
                line(COGS, 90_000),
                line(WAGES, 100_000),
                line(RENT, 36_000),
                line(UTILITIES, 12_000),
                line(MARKETING, 20_000),
                line(INTEREST_EXPENSE, 8_000),
                line(RETAINED_EARNINGS, 159_000));

        return FinancialEntry.of("USD", debit, credit,
                BusinessDateTime.atClose(LocalDate.of(2026, 12, 31)), "Closing entry", "Close income and expense to retained earnings");
    }

    // Helpers --------------------------------------------------------------------------------------------------

    private static FinancialAccount account(String id, String name, FinancialAccountType type) {
        return FinancialAccount.of(id, name, name, type);
    }

    private static FinancialEntryLine line(FinancialAccount account, long amount) {
        return FinancialEntryLine.of(account, Money.of(amount, "USD"));
    }

    private static List<FinancialEntryLine> repeat(FinancialAccount account, long amount, int count) {
        List<FinancialEntryLine> lines = new ArrayList<>();
        for (int i = 0; i < count; i++) lines.add(line(account, amount));
        return lines;
    }

    private static FinancialEntry entry(String summary, int month, int day,
                                        List<FinancialEntryLine> debit, List<FinancialEntryLine> credit) {
        return FinancialEntry.of("USD", debit, credit, BusinessDateTime.of(2026, month, day, 0), summary, "");
    }

    private static int lineCount(FinancialEntry entry) {
        return entry.getDebit().size() + entry.getCredit().size();
    }

    private static BigDecimal bd(long value) {
        return BigDecimal.valueOf(value);
    }

    private static void assertEqualsMoney(BigDecimal expected, Money actual) {
        assertEquals("USD", actual.getCurrency());
        assertEquals(0, actual.getAmount().compareTo(expected),
                () -> "expected " + expected + " but was " + actual.getAmount());
    }

    private static void assertAccountingIdentity(Book book) {
        BigDecimal assets = book.getChangeInAssets().getAmount();
        BigDecimal rhs = book.getChangeInLiabilities().getAmount()
                .add(book.getChangeInEquity().getAmount())
                .add(book.getTotalIncome().getAmount())
                .subtract(book.getTotalExpenditure().getAmount());
        assertEquals(0, assets.compareTo(rhs), () -> "identity broken: assets " + assets + " != " + rhs);
    }
}
