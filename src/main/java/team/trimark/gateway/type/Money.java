package team.trimark.gateway.type;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * The unit of money.
 */
public class Money extends Number implements Comparable<Number> {
    /**
     * Creates a new money instance.
     *
     * @param amount   The amount
     * @param currency The currency
     * @return The money instance
     */
    public static Money of(double amount, String currency) {
        return new Money(BigDecimal.valueOf(amount), currency, new HashMap<>());
    }

    /**
     * Creates a new money instance.
     *
     * @param amount   The amount
     * @param currency The currency
     * @return The money instance
     */
    public static Money of(long amount, String currency) {
        return new Money(BigDecimal.valueOf(amount), currency, new HashMap<>());
    }

    /**
     * Creates a new money instance.
     *
     * @param amount   The amount
     * @param currency The currency
     * @return The money instance
     */
    public static Money of(BigDecimal amount, String currency) {
        return new Money(amount, currency, new HashMap<>());
    }

    /**
     * Creates a new money instance.
     *
     * @param amount      The amount
     * @param currency    The currency
     * @param conversions The map of conversions
     * @return The money instance
     */
    public static Money of(BigDecimal amount, String currency, Map<String, BigDecimal> conversions) {
        return new Money(amount, currency, conversions);
    }

    /**
     * Private constructor.
     *
     * @param amount      The amount
     * @param currency    The currency
     * @param conversions The conversions
     */
    private Money(BigDecimal amount, String currency, Map<String, BigDecimal> conversions) {
        this.amount = amount;
        this.currency = currency;
        this.conversions = Map.copyOf(conversions);
    }

    private final BigDecimal amount;
    private final String currency;
    private final Map<String, BigDecimal> conversions;

    /**
     * Returns the raw amount.
     *
     * @return The raw amount
     */
    public BigDecimal getAmount() {
        return amount;
    }

    /**
     * Returns the amount in the currency.
     *
     * @param currency The currency
     * @return The amount
     */
    public Optional<BigDecimal> getAmountInCurrency(String currency) {
        if (Objects.equals(this.currency, currency)) {
            return Optional.of(amount);
        }

        if (conversions.containsKey(currency)) {
            return Optional.of(conversions.get(currency));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Returns the {@code double} value in the currency. May be lossy.
     *
     * @param currency The currency
     * @return The {@code double} value
     */
    public OptionalDouble doubleValueInCurrency(String currency) {
        if (Objects.equals(this.currency, currency)) {
            return OptionalDouble.of(amount.doubleValue());
        }

        if (conversions.containsKey(currency)) {
            return OptionalDouble.of(conversions.get(currency).doubleValue());
        } else {
            return OptionalDouble.empty();
        }
    }

    /**
     * Returns the currency.
     *
     * @return The currency
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * Returns whether this figure has the currency.
     *
     * @param currency The currency
     * @return {@code true} if the currency exists
     */
    public boolean hasCurrency(String currency) {
        return Objects.equals(this.currency, currency) || conversions.containsKey(currency);
    }

    /**
     * Returns the immutable map of conversions.
     *
     * @return The immutable map of conversions
     */
    public Map<String, BigDecimal> getConversions() {
        return conversions;
    }

    public Money rebase(String targetCurrency) throws IllegalArgumentException {
        BigDecimal target = getAmountInCurrency(targetCurrency).orElseThrow(() -> new IllegalArgumentException("Cannot convert to currency without exchange rate."));
        BigDecimal exchangeRate = target.divide(amount, RoundingMode.HALF_EVEN);

        return rebase(targetCurrency, exchangeRate, false);
    }

    public Money rebase(String targetCurrency, BigDecimal exchangeRate, boolean incumbentAsNumerator) {
        if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be positive and non-null.");
        }

        BigDecimal rateToTarget = incumbentAsNumerator
                ? exchangeRate
                : BigDecimal.ONE.divide(exchangeRate, RoundingMode.HALF_EVEN);

        BigDecimal newAmount = amount.multiply(rateToTarget);
        Map<String, BigDecimal> newConversions = new HashMap<>();

        newConversions.put(currency, amount);
        conversions.forEach((k, v) -> {
            if (Objects.equals(k, targetCurrency)) return;
            newConversions.put(k, v);
        });

        return new Money(newAmount, targetCurrency, newConversions);
    }

    /**
     * Compares to another money.
     *
     * @param o the object to be compared
     * @return {@code [-1, 0, 1]}
     * @throws RuntimeException When the target is a {@link Money}, and there are no currency pairs
     */
    @Override
    public int compareTo(Number o) throws RuntimeException {
        if (o instanceof Money m) {
            return m.getAmountInCurrency(currency)
                    .or(() -> getAmountInCurrency(m.currency))
                    .map(amount::compareTo)
                    .orElseThrow(() -> new RuntimeException("Currency mismatch - cannot compare the two amounts."));
        } else if (o instanceof BigDecimal d) {
            return amount.compareTo(d);
        } else {
            return Double.compare(amount.doubleValue(), o.doubleValue());
        }
    }

    @Override
    public int intValue() {
        return amount.intValue();
    }

    @Override
    public long longValue() {
        return amount.longValue();
    }

    @Override
    public float floatValue() {
        return amount.floatValue();
    }

    @Override
    public double doubleValue() {
        return amount.doubleValue();
    }
}
