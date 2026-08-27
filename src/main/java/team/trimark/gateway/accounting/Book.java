package team.trimark.gateway.accounting;

import team.trimark.gateway.Constants;
import team.trimark.gateway.type.BusinessDateTime;
import team.trimark.gateway.type.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * A book of account: a default currency plus an ordered list of {@link FinancialEntry entries}. All derived figures
 * (balances, totals, movements) are computed in the default currency using each line's notation in that currency.
 *
 * <p>The interface is editable - it declares mutation methods. The two implementations differ only in whether those
 * mutations are honored: {@link EditableBook} applies them, while {@link NonEditableBook} throws
 * {@link UnsupportedOperationException}. Everything else (validity and all the derived figures) is shared as default
 * methods driven by {@link #getDefaultCurrency()} and {@link #getEntries()}.
 *
 * <p><b>Sign conventions.</b> A "change in" figure is the net movement produced by this book's entries (a book has no
 * opening balances). Balances and per-type movements are type-normalized so that a positive figure means the account
 * sits on its normal side:
 * <ul>
 *     <li>{@code ASSET}, {@code EXPENSE}: normal debit, so debit - credit</li>
 *     <li>{@code LIABILITY}, {@code EQUITY}, {@code INCOME}: normal credit, so credit - debit</li>
 * </ul>
 */
public interface Book {
    /**
     * Creates a non-editable book. Its mutation methods throw {@link UnsupportedOperationException}.
     *
     * @param defaultCurrency The default currency
     * @param entries         The entries
     * @return The book
     */
    static Book of(String defaultCurrency, List<FinancialEntry> entries) {
        return new NonEditableBook(defaultCurrency, entries);
    }

    /**
     * Creates an editable book.
     *
     * @param defaultCurrency The default currency
     * @param entries         The initial entries
     * @return The book
     */
    static Book editable(String defaultCurrency, List<FinancialEntry> entries) {
        return new EditableBook(defaultCurrency, entries);
    }

    /**
     * Creates an empty editable book.
     *
     * @param defaultCurrency The default currency
     * @return The book
     */
    static Book editable(String defaultCurrency) {
        return new EditableBook(defaultCurrency, List.of());
    }

    /**
     * Creates a non-editable copy of the given book. The copy is indistinguishable from the source other than its
     * concrete type: it carries the same default currency and the same entries in the same order, and therefore is
     * {@link Object#equals(Object) equal} to the source. The copy is a snapshot - later mutations to an editable source
     * do not affect it.
     *
     * @param source The book to copy
     * @return A non-editable copy
     */
    static Book copyOf(Book source) {
        Objects.requireNonNull(source, "Source book must be non-null.");
        return new NonEditableBook(source.getDefaultCurrency(), source.getEntries());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Core state
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Returns the default currency of this book.
     *
     * @return The default currency
     */
    String getDefaultCurrency();

    /**
     * Returns the entries of this book, in order. The returned list is unmodifiable; use the mutation methods to change
     * an editable book.
     *
     * @return The entries
     */
    List<FinancialEntry> getEntries();

    // ------------------------------------------------------------------------------------------------------------
    // Mutation (honored by EditableBook, rejected by NonEditableBook)
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Sets the default currency.
     *
     * @param defaultCurrency The default currency
     * @throws UnsupportedOperationException When the book is not editable
     */
    void setDefaultCurrency(String defaultCurrency);

    /**
     * Appends an entry.
     *
     * @param entry The entry
     * @throws UnsupportedOperationException When the book is not editable
     */
    void addEntry(FinancialEntry entry);

    /**
     * Appends the given entries, in order.
     *
     * @param entries The entries
     * @throws UnsupportedOperationException When the book is not editable
     */
    void addEntries(Collection<? extends FinancialEntry> entries);

    /**
     * Removes the entry at the given index.
     *
     * @param index The index
     * @throws UnsupportedOperationException When the book is not editable
     */
    void removeEntryAt(int index);

    /**
     * Removes all entries.
     *
     * @throws UnsupportedOperationException When the book is not editable
     */
    void clearEntries();

    // ------------------------------------------------------------------------------------------------------------
    // Validity
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Returns whether this book is valid - see {@link #validationErrors()} for the exact conditions.
     *
     * @return {@code true} if this book is valid
     */
    default boolean isValid() {
        return validationErrors().isEmpty();
    }

    /**
     * Returns the reasons this book is invalid, one message per problem (empty when valid). A book is valid when:
     * <ul>
     *     <li>the default currency is set,</li>
     *     <li>each account id maps to a single {@link FinancialAccountType} across every line,</li>
     *     <li>every entry is balanced and well-formed ({@link FinancialEntry#isValid()}, in the entry's own currency),
     *         and</li>
     *     <li>every line carries an explicit notation in the default currency.</li>
     * </ul>
     * A cross-currency entry balances in its own currency but may drift by rounding in the default currency; that
     * residual is reported by {@link #getDefaultCurrencyResidual()} and does not, by itself, make the book invalid.
     *
     * @return The validation errors
     */
    default List<String> validationErrors() {
        List<String> errors = new ArrayList<>();
        String currency = getDefaultCurrency();
        if (currency == null) errors.add("Default currency is null.");

        // Each account id must resolve to a single type.
        Map<String, Set<FinancialAccountType>> typesById = new LinkedHashMap<>();
        for (FinancialEntryLine line : allLines()) {
            typesById.computeIfAbsent(line.getAccount().getAccountId(), k -> new LinkedHashSet<>())
                    .add(line.getAccount().getType());
        }
        typesById.forEach((accountId, types) -> {
            if (types.size() > 1) errors.add("Account " + accountId + " has conflicting types: " + types + ".");
        });

        List<FinancialEntry> entries = getEntries();
        for (int i = 0; i < entries.size(); i++) {
            FinancialEntry entry = entries.get(i);
            if (!entry.isValid()) errors.add("Entry " + i + " (" + entry.getSummary() + ") is not balanced or well-formed.");
            if (currency != null) {
                for (FinancialEntryLine line : linesOf(entry)) {
                    if (!line.getMoney().hasCurrency(currency)) {
                        errors.add("Entry " + i + " line for account " + line.getAccount().getAccountId()
                                + " has no notation in default currency " + currency + ".");
                    }
                }
            }
        }

        return errors;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Per-account figures (default currency)
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Returns every account id that appears in this book, in first-seen order.
     *
     * @return The account ids
     */
    default Set<String> getAccountIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (FinancialEntryLine line : allLines()) ids.add(line.getAccount().getAccountId());
        return Collections.unmodifiableSet(ids);
    }

    /**
     * Returns the type of the given account, as recorded on its lines.
     *
     * @param accountId The account id
     * @return The type, or empty when the account does not appear in this book
     */
    default Optional<FinancialAccountType> getAccountType(String accountId) {
        for (FinancialEntryLine line : allLines()) {
            if (matches(line, accountId)) return Optional.of(line.getAccount().getType());
        }
        return Optional.empty();
    }

    /**
     * Returns the raw total of debit lines for the given account, in the default currency.
     *
     * @param accountId The account id
     * @return The debit total
     */
    default Money getDebitTotal(String accountId) {
        BigDecimal sum = BigDecimal.ZERO;
        for (FinancialEntry entry : getEntries()) {
            for (FinancialEntryLine line : entry.getDebit()) {
                if (matches(line, accountId)) sum = sum.add(amountInDefault(line));
            }
        }
        return Money.of(sum, getDefaultCurrency());
    }

    /**
     * Returns the raw total of credit lines for the given account, in the default currency.
     *
     * @param accountId The account id
     * @return The credit total
     */
    default Money getCreditTotal(String accountId) {
        BigDecimal sum = BigDecimal.ZERO;
        for (FinancialEntry entry : getEntries()) {
            for (FinancialEntryLine line : entry.getCredit()) {
                if (matches(line, accountId)) sum = sum.add(amountInDefault(line));
            }
        }
        return Money.of(sum, getDefaultCurrency());
    }

    /**
     * Returns the type-normalized balance of the given account, in the default currency (positive when the account
     * sits on its normal side).
     *
     * @param accountId The account id
     * @return The balance
     */
    default Money getBalance(String accountId) {
        BigDecimal sum = BigDecimal.ZERO;
        for (FinancialEntry entry : getEntries()) {
            for (FinancialEntryLine line : entry.getDebit()) {
                if (matches(line, accountId)) sum = sum.add(normalContribution(line, true));
            }
            for (FinancialEntryLine line : entry.getCredit()) {
                if (matches(line, accountId)) sum = sum.add(normalContribution(line, false));
            }
        }
        return Money.of(sum, getDefaultCurrency());
    }

    /**
     * Returns the type-normalized balance of every account, in first-seen order.
     *
     * @return The balances keyed by account id
     */
    default Map<String, Money> getBalances() {
        Map<String, Money> balances = new LinkedHashMap<>();
        for (String accountId : getAccountIds()) balances.put(accountId, getBalance(accountId));
        return Collections.unmodifiableMap(balances);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Aggregate figures (default currency)
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Returns the total transaction volume: the sum of all debit lines in the default currency (equal to the sum of all
     * credit lines when the book cross-foots exactly).
     *
     * @return The total transaction volume
     */
    default Money getTotalTransactionVolume() {
        BigDecimal sum = BigDecimal.ZERO;
        for (FinancialEntry entry : getEntries()) {
            for (FinancialEntryLine line : entry.getDebit()) sum = sum.add(amountInDefault(line));
        }
        return Money.of(sum, getDefaultCurrency());
    }

    /**
     * Returns the total income recognized in this book (net credit movement of {@code INCOME} accounts).
     *
     * @return The total income
     */
    default Money getTotalIncome() {
        return Money.of(sumNormalByType(FinancialAccountType.INCOME), getDefaultCurrency());
    }

    /**
     * Returns the total expenditure in this book (net debit movement of {@code EXPENSE} accounts).
     *
     * @return The total expenditure
     */
    default Money getTotalExpenditure() {
        return Money.of(sumNormalByType(FinancialAccountType.EXPENSE), getDefaultCurrency());
    }

    /**
     * Returns the change in asset balance across this book's entries.
     *
     * @return The change in assets
     */
    default Money getChangeInAssets() {
        return Money.of(sumNormalByType(FinancialAccountType.ASSET), getDefaultCurrency());
    }

    /**
     * Returns the change in liability balance across this book's entries.
     *
     * @return The change in liabilities
     */
    default Money getChangeInLiabilities() {
        return Money.of(sumNormalByType(FinancialAccountType.LIABILITY), getDefaultCurrency());
    }

    /**
     * Returns the change in equity across this book's entries.
     *
     * @return The change in equity
     */
    default Money getChangeInEquity() {
        return Money.of(sumNormalByType(FinancialAccountType.EQUITY), getDefaultCurrency());
    }

    /**
     * Returns the default-currency residual: total debits minus total credits in the default currency. Zero when the
     * book cross-foots exactly; a non-zero value is the rounding drift introduced by translating foreign-currency
     * entries into the default currency.
     *
     * @return The residual
     */
    default Money getDefaultCurrencyResidual() {
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        for (FinancialEntry entry : getEntries()) {
            for (FinancialEntryLine line : entry.getDebit()) debits = debits.add(amountInDefault(line));
            for (FinancialEntryLine line : entry.getCredit()) credits = credits.add(amountInDefault(line));
        }
        return Money.of(debits.subtract(credits), getDefaultCurrency());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Temporal extent
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Returns the earliest business date and time among this book's entries.
     *
     * @return The earliest timestamp, or empty when the book has no entries
     */
    default Optional<BusinessDateTime> getEarliestBusinessDateTime() {
        return getEntries().stream().map(FinancialEntry::getBusinessDateTime).min(Comparator.naturalOrder());
    }

    /**
     * Returns the latest business date and time among this book's entries.
     *
     * @return The latest timestamp, or empty when the book has no entries
     */
    default Optional<BusinessDateTime> getLatestBusinessDateTime() {
        return getEntries().stream().map(FinancialEntry::getBusinessDateTime).max(Comparator.naturalOrder());
    }

    // ------------------------------------------------------------------------------------------------------------
    // Foreign-currency exchange rates
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Returns the weighted average exchange rate of each foreign-currency position held by the given account, keyed by
     * that foreign currency. A line is a foreign position when its money is denominated in a currency other than the
     * default. The rate is expressed as <b>default currency per one unit of the foreign currency</b> and is weighted by
     * position size: {@code sum(default-currency value) / sum(foreign-currency units)}. Currencies whose foreign total
     * is zero are omitted (no rate is defined).
     *
     * @param accountId The account id
     * @return The weighted average rates keyed by foreign currency
     */
    default Map<String, BigDecimal> getWeightedAverageExchangeRates(String accountId) {
        String currency = getDefaultCurrency();
        Map<String, BigDecimal> defaultSums = new LinkedHashMap<>();
        Map<String, BigDecimal> foreignSums = new LinkedHashMap<>();

        for (FinancialEntryLine line : allLines()) {
            if (!matches(line, accountId)) continue;

            Money money = line.getMoney();
            String base = money.getCurrency();
            if (Objects.equals(base, currency)) continue; // denominated in the default currency - not a foreign position

            defaultSums.merge(base, amountInDefault(line), BigDecimal::add);
            foreignSums.merge(base, money.getAmount(), BigDecimal::add);
        }

        Map<String, BigDecimal> rates = new LinkedHashMap<>();
        foreignSums.forEach((base, foreignTotal) -> {
            if (foreignTotal.signum() == 0) return; // no position, no rate
            rates.put(base, defaultSums.get(base).divide(foreignTotal, Constants.BIG_DECIMAL_SCALE, RoundingMode.HALF_EVEN));
        });

        return Collections.unmodifiableMap(rates);
    }

    /**
     * Returns the weighted average exchange rate of the given account's position in a single foreign currency.
     *
     * @param accountId The account id
     * @param currency  The foreign currency
     * @return The weighted average rate, or empty when the account holds no such position
     */
    default Optional<BigDecimal> getWeightedAverageExchangeRate(String accountId, String currency) {
        return Optional.ofNullable(getWeightedAverageExchangeRates(accountId).get(currency));
    }

    /**
     * Returns the weighted average exchange rates of every account that holds a foreign-currency position, keyed by
     * account id then by foreign currency. Accounts with no foreign position are omitted.
     *
     * @return The weighted average rates
     */
    default Map<String, Map<String, BigDecimal>> getWeightedAverageExchangeRates() {
        Map<String, Map<String, BigDecimal>> rates = new LinkedHashMap<>();
        for (String accountId : getAccountIds()) {
            Map<String, BigDecimal> accountRates = getWeightedAverageExchangeRates(accountId);
            if (!accountRates.isEmpty()) rates.put(accountId, accountRates);
        }
        return Collections.unmodifiableMap(rates);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Returns every line in the book, debit then credit, across all entries.
     *
     * @return The lines
     */
    private List<FinancialEntryLine> allLines() {
        List<FinancialEntryLine> lines = new ArrayList<>();
        for (FinancialEntry entry : getEntries()) lines.addAll(linesOf(entry));
        return lines;
    }

    /**
     * Returns the debit and credit lines of one entry, debit first.
     *
     * @param entry The entry
     * @return The lines
     */
    private static List<FinancialEntryLine> linesOf(FinancialEntry entry) {
        List<FinancialEntryLine> lines = new ArrayList<>(entry.getDebit());
        lines.addAll(entry.getCredit());
        return lines;
    }

    /**
     * Returns the line's amount in the default currency, or throws when it carries no such notation.
     *
     * @param line The line
     * @return The amount in the default currency
     */
    private BigDecimal amountInDefault(FinancialEntryLine line) {
        String currency = getDefaultCurrency();
        return line.getMoney().getAmountInCurrency(currency).orElseThrow(() -> new IllegalStateException(
                "Entry line for account " + line.getAccount().getAccountId() + " has no notation in default currency "
                        + currency + "; check isValid()/validationErrors()."));
    }

    /**
     * Returns whether the given account type keeps its normal balance on the debit side.
     *
     * @param type The type
     * @return {@code true} for {@code ASSET} and {@code EXPENSE}
     */
    private static boolean isDebitNormal(FinancialAccountType type) {
        return type == FinancialAccountType.ASSET || type == FinancialAccountType.EXPENSE;
    }

    /**
     * Returns the line's type-normalized contribution to a balance, in the default currency: positive when it moves the
     * account toward its normal side, negative otherwise.
     *
     * @param line      The line
     * @param debitSide {@code true} when the line is on the debit side
     * @return The signed contribution
     */
    private BigDecimal normalContribution(FinancialEntryLine line, boolean debitSide) {
        BigDecimal amount = amountInDefault(line);
        return debitSide == isDebitNormal(line.getAccount().getType()) ? amount : amount.negate();
    }

    /**
     * Sums the type-normalized contributions of every line whose account is of the given type.
     *
     * @param type The type
     * @return The sum
     */
    private BigDecimal sumNormalByType(FinancialAccountType type) {
        BigDecimal sum = BigDecimal.ZERO;
        for (FinancialEntry entry : getEntries()) {
            for (FinancialEntryLine line : entry.getDebit()) {
                if (line.getAccount().getType() == type) sum = sum.add(normalContribution(line, true));
            }
            for (FinancialEntryLine line : entry.getCredit()) {
                if (line.getAccount().getType() == type) sum = sum.add(normalContribution(line, false));
            }
        }
        return sum;
    }

    /**
     * Returns whether the line is posted against the given account id.
     *
     * @param line      The line
     * @param accountId The account id
     * @return {@code true} on a match
     */
    private static boolean matches(FinancialEntryLine line, String accountId) {
        return Objects.equals(line.getAccount().getAccountId(), accountId);
    }
}
