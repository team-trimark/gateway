package team.trimark.gateway.accounting;

import team.trimark.gateway.type.Money;

/**
 * A single debit or credit line: a monetary amount posted against one account. A {@link FinancialEntry} holds a list of
 * these per side, so the same account may appear on more than one line and identical lines may be repeated.
 */
public final class FinancialEntryLine {
    /**
     * Creates a new line.
     *
     * @param account The account the amount is posted against
     * @param money   The amount
     * @return The line
     */
    public static FinancialEntryLine of(FinancialAccount account, Money money) {
        return new FinancialEntryLine(account, money);
    }

    /**
     * Private constructor.
     *
     * @param account The account
     * @param money   The amount
     */
    private FinancialEntryLine(FinancialAccount account, Money money) {
        this.account = account;
        this.money = money;
    }

    private final FinancialAccount account;
    private final Money money;

    /**
     * Returns the account this line is posted against.
     *
     * @return The account
     */
    public FinancialAccount getAccount() {
        return account;
    }

    /**
     * Returns the amount of this line.
     *
     * @return The amount
     */
    public Money getMoney() {
        return money;
    }
}
