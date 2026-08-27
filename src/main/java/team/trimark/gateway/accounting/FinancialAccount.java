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
