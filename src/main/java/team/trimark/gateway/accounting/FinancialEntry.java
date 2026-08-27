package team.trimark.gateway.accounting;

import team.trimark.gateway.type.BusinessDateTime;

import java.math.BigDecimal;
import java.util.*;

public interface FinancialEntry {
    /**
     * Creates a new entry with no edit reason.
     *
     * @param currency         The entry currency
     * @param debit            The debit lines
     * @param credit           The credit lines
     * @param businessDateTime The business date and time
     * @param summary          The summary
     * @param description      The description
     * @return The entry
     */
    static FinancialEntry of(String currency, List<FinancialEntryLine> debit, List<FinancialEntryLine> credit,
                             BusinessDateTime businessDateTime, String summary, String description) {
        return of(currency, debit, credit, businessDateTime, summary, description, null);
    }

    /**
     * Creates a new entry.
     *
     * @param currency         The entry currency
     * @param debit            The debit lines
     * @param credit           The credit lines
     * @param businessDateTime The business date and time
     * @param summary          The summary
     * @param description      The description
     * @param editReason       The edit reason, or null
     * @return The entry
     */
    static FinancialEntry of(String currency, List<FinancialEntryLine> debit, List<FinancialEntryLine> credit,
                             BusinessDateTime businessDateTime, String summary, String description, String editReason) {
        return new FinancialEntryImpl(currency, debit, credit, businessDateTime, summary, description, editReason);
    }

    /**
     * Returns whether this entry is valid. An entry is valid when:
     * <ul>
     *     <li>it has a currency,</li>
     *     <li>it has at least one debit line and at least one credit line,</li>
     *     <li>every line carries the entry currency (natively or via a conversion),</li>
     *     <li>no line amount is negative (a zero amount is a valid, no-effect line), and</li>
     *     <li>the debit total equals the credit total in the entry currency.</li>
     * </ul>
     * A balanced entry of all-zero lines ({@code 0 == 0}) is valid as long as both sides carry lines.
     *
     * @return {@code true} if this entry is valid
     */
    default boolean isValid() {
        String currency = getCurrency();
        if (currency == null) return false;

        List<FinancialEntryLine> debits = getDebit();
        List<FinancialEntryLine> credits = getCredit();

        // Both sides must carry at least one line - an empty side is not a balanced entry.
        if (debits.isEmpty() || credits.isEmpty()) return false;

        // Every line must carry the entry currency and must not be negative.
        if (!linesAreConvertibleAndNonNegative(debits, currency)) return false;
        if (!linesAreConvertibleAndNonNegative(credits, currency)) return false;

        BigDecimal debitSum = sumInCurrency(debits, currency);
        BigDecimal creditSum = sumInCurrency(credits, currency);

        return debitSum.compareTo(creditSum) == 0;
    }

    /**
     * Returns whether every line carries the currency and has a non-negative amount in it.
     *
     * @param lines    The lines
     * @param currency The entry currency
     * @return {@code true} if all lines are convertible and non-negative
     */
    private static boolean linesAreConvertibleAndNonNegative(List<FinancialEntryLine> lines, String currency) {
        for (FinancialEntryLine line : lines) {
            Optional<BigDecimal> amount = line.getMoney().getAmountInCurrency(currency);
            if (amount.isEmpty()) return false;
            if (amount.get().signum() < 0) return false;
        }

        return true;
    }

    /**
     * Sums the given lines in the entry currency. Assumes every line is convertible (see
     * {@link #linesAreConvertibleAndNonNegative(List, String)}).
     *
     * @param lines    The lines
     * @param currency The entry currency
     * @return The sum
     */
    private static BigDecimal sumInCurrency(List<FinancialEntryLine> lines, String currency) {
        return lines.stream()
                .map(line -> line.getMoney().getAmountInCurrency(currency))
                .map(Optional::orElseThrow)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    String getCurrency();

    List<FinancialEntryLine> getDebit();
    List<FinancialEntryLine> getCredit();

    BusinessDateTime getBusinessDateTime();

    String getSummary();
    String getDescription();

    Optional<String> getEditReason();
}
