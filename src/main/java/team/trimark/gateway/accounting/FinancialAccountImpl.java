package team.trimark.gateway.accounting;

import team.trimark.gateway.type.BusinessDateTime;
import team.trimark.gateway.type.BusinessTemporalRange;

import java.util.*;

/**
 * The standard {@link FinancialAccount} implementation. The account identifier and type are immutable; names, localized
 * names, and the usage lifecycle may change over the account's life.
 */
public final class FinancialAccountImpl implements FinancialAccount {
    /**
     * Creates a new account with no localized names, no usage window, and no paused ranges.
     *
     * @param accountId        The unique, immutable account identifier
     * @param accountShortName The short name
     * @param accountFullName  The full name
     * @param type             The account type
     * @return The account
     */
    public static FinancialAccountImpl of(String accountId, String accountShortName, String accountFullName, FinancialAccountType type) {
        return of(accountId, accountShortName, accountFullName, type, Map.of(), null, null, List.of());
    }

    /**
     * Creates a new account.
     *
     * @param accountId              The unique, immutable account identifier
     * @param accountShortName       The short name
     * @param accountFullName        The full name
     * @param type                   The account type
     * @param localeMap              The initial locale-to-name map
     * @param usageStartedDateTime   The usage start, or null
     * @param usageEndedDateTime     The usage end, or null
     * @param usagePausedRanges      The ranges during which usage was paused
     * @return The account
     */
    public static FinancialAccountImpl of(String accountId, String accountShortName, String accountFullName, FinancialAccountType type,
                                          Map<String, String> localeMap, BusinessDateTime usageStartedDateTime,
                                          BusinessDateTime usageEndedDateTime, List<BusinessTemporalRange> usagePausedRanges) {
        return new FinancialAccountImpl(accountId, accountShortName, accountFullName, type,
                localeMap, usageStartedDateTime, usageEndedDateTime, usagePausedRanges);
    }

    /**
     * Private constructor.
     *
     * @param accountId              The account identifier
     * @param accountShortName       The short name
     * @param accountFullName        The full name
     * @param type                   The account type
     * @param localeMap              The locale map
     * @param usageStartedDateTime   The usage start
     * @param usageEndedDateTime     The usage end
     * @param usagePausedRanges      The paused ranges
     */
    private FinancialAccountImpl(String accountId, String accountShortName, String accountFullName, FinancialAccountType type,
                                 Map<String, String> localeMap, BusinessDateTime usageStartedDateTime,
                                 BusinessDateTime usageEndedDateTime, List<BusinessTemporalRange> usagePausedRanges) {
        this.accountId = Objects.requireNonNull(accountId, "Account identifier must be non-null.");
        this.accountShortName = accountShortName;
        this.accountFullName = accountFullName;
        this.type = Objects.requireNonNull(type, "Account type must be non-null.");
        this.localeMap = new HashMap<>(localeMap);
        this.usageStartedDateTime = usageStartedDateTime;
        this.usageEndedDateTime = usageEndedDateTime;
        this.usagePausedRanges = new ArrayList<>(usagePausedRanges);
    }

    private final String accountId;
    private String accountShortName;
    private String accountFullName;
    private final FinancialAccountType type;
    private final Map<String, String> localeMap;
    private BusinessDateTime usageStartedDateTime;
    private BusinessDateTime usageEndedDateTime;
    private final List<BusinessTemporalRange> usagePausedRanges;

    @Override
    public String getAccountId() {
        return accountId;
    }

    @Override
    public String getAccountShortName() {
        return accountShortName;
    }

    @Override
    public void setAccountShortName(String name) {
        this.accountShortName = name;
    }

    @Override
    public String getAccountFullName() {
        return accountFullName;
    }

    @Override
    public void setAccountFullName(String name) {
        this.accountFullName = name;
    }

    @Override
    public Optional<String> getAccountLocalizedName(String locale) {
        return Optional.ofNullable(localeMap.get(locale));
    }

    @Override
    public Map<String, String> getLocaleMap() {
        return Collections.unmodifiableMap(localeMap);
    }

    @Override
    public FinancialAccountType getType() {
        return type;
    }

    @Override
    public Optional<BusinessDateTime> getUsageStartedDateTime() {
        return Optional.ofNullable(usageStartedDateTime);
    }

    @Override
    public void setUsageStartedDateTime(BusinessDateTime time) {
        this.usageStartedDateTime = time;
    }

    @Override
    public Optional<BusinessDateTime> getUsageEndedDateTime() {
        return Optional.ofNullable(usageEndedDateTime);
    }

    @Override
    public void setUsageEndedDateTime(BusinessDateTime time) {
        this.usageEndedDateTime = time;
    }

    @Override
    public List<BusinessTemporalRange> getUsagePausedRanges() {
        return Collections.unmodifiableList(usagePausedRanges);
    }

    /**
     * Compares two accounts by their immutable identity (the account identifier).
     *
     * @param o The object to compare
     * @return {@code true} if the two accounts share the same identifier
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FinancialAccount a)) return false;

        return Objects.equals(accountId, a.getAccountId());
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return The hash code
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(accountId);
    }
}
