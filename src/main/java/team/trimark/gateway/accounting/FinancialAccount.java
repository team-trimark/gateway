package team.trimark.gateway.accounting;

import team.trimark.gateway.type.BusinessDateTime;
import team.trimark.gateway.type.BusinessTemporalRange;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A financial account.
 */
public interface FinancialAccount {
    /**
     * Creates a new account with no localized names, no usage window, and no paused ranges.
     *
     * @param accountId        The unique, immutable account identifier
     * @param accountShortName The short name
     * @param accountFullName  The full name
     * @param type             The account type
     * @return The account
     */
    static FinancialAccount of(String accountId, String accountShortName, String accountFullName, FinancialAccountType type) {
        return of(accountId, accountShortName, accountFullName, type, Map.of(), null, null, List.of());
    }

    /**
     * Creates a new account.
     *
     * @param accountId            The unique, immutable account identifier
     * @param accountShortName     The short name
     * @param accountFullName      The full name
     * @param type                 The account type
     * @param localeMap            The initial locale-to-name map
     * @param usageStartedDateTime The usage start, or null
     * @param usageEndedDateTime   The usage end, or null
     * @param usagePausedRanges    The ranges during which usage was paused
     * @return The account
     */
    static FinancialAccount of(String accountId, String accountShortName, String accountFullName, FinancialAccountType type,
                               Map<String, String> localeMap, BusinessDateTime usageStartedDateTime,
                               BusinessDateTime usageEndedDateTime, List<BusinessTemporalRange> usagePausedRanges) {
        return new FinancialAccountImpl(accountId, accountShortName, accountFullName, type,
                localeMap, usageStartedDateTime, usageEndedDateTime, usagePausedRanges);
    }

    /**
     * Returns the unique account identifier.
     * @return The unique account identifier
     */
    String getAccountId();

    /**
     * Returns the account short name.
     * @return The account short name
     */
    String getAccountShortName();

    /**
     * Sets the account short name.
     * @param name The name
     */
    void setAccountShortName(String name);

    /**
     * Returns the account full name.
     * @return The account full name
     */
    String getAccountFullName();

    /**
     * Sets the account full name.
     * @param name The account full name
     */
    void setAccountFullName(String name);

    /**
     * Returns the localized account name.
     * @param locale The locale code
     * @return The localized name
     */
    Optional<String> getAccountLocalizedName(String locale);

    /**
     * Returns the locale map.
     * @return The locale map
     */
    Map<String, String> getLocaleMap();

    /**
     * Returns the type of this account.
     * @return The type of this account
     */
    FinancialAccountType getType();

    Optional<BusinessDateTime> getUsageStartedDateTime();

    void setUsageStartedDateTime(BusinessDateTime time);

    Optional<BusinessDateTime> getUsageEndedDateTime();

    void setUsageEndedDateTime(BusinessDateTime time);

    List<BusinessTemporalRange> getUsagePausedRanges();
}
