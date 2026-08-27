package team.trimark.gateway.accounting;

import team.trimark.gateway.type.BusinessDateTime;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The standard {@link FinancialEntry} implementation. An entry is immutable once created; its debit and credit sides are
 * ordered lists that may hold multiple lines against the same account, and identical lines are permitted.
 */
public final class FinancialEntryImpl implements FinancialEntry {
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
    public static FinancialEntryImpl of(String currency, List<FinancialEntryLine> debit, List<FinancialEntryLine> credit,
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
    public static FinancialEntryImpl of(String currency, List<FinancialEntryLine> debit, List<FinancialEntryLine> credit,
                                        BusinessDateTime businessDateTime, String summary, String description, String editReason) {
        return new FinancialEntryImpl(currency, debit, credit, businessDateTime, summary, description, editReason);
    }

    /**
     * Private constructor.
     *
     * @param currency         The currency
     * @param debit            The debit lines
     * @param credit           The credit lines
     * @param businessDateTime The business date and time
     * @param summary          The summary
     * @param description      The description
     * @param editReason       The edit reason
     */
    private FinancialEntryImpl(String currency, List<FinancialEntryLine> debit, List<FinancialEntryLine> credit,
                              BusinessDateTime businessDateTime, String summary, String description, String editReason) {
        this.currency = currency;
        this.debit = List.copyOf(debit);
        this.credit = List.copyOf(credit);
        this.businessDateTime = businessDateTime;
        this.summary = summary;
        this.description = description;
        this.editReason = editReason;
    }

    private final String currency;
    private final List<FinancialEntryLine> debit;
    private final List<FinancialEntryLine> credit;
    private final BusinessDateTime businessDateTime;
    private final String summary;
    private final String description;
    private final String editReason;

    @Override
    public String getCurrency() {
        return currency;
    }

    @Override
    public List<FinancialEntryLine> getDebit() {
        return debit;
    }

    @Override
    public List<FinancialEntryLine> getCredit() {
        return credit;
    }

    @Override
    public BusinessDateTime getBusinessDateTime() {
        return businessDateTime;
    }

    @Override
    public String getSummary() {
        return summary;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Optional<String> getEditReason() {
        return Optional.ofNullable(editReason);
    }
}
