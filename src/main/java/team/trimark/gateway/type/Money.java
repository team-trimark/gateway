package team.trimark.gateway.type;

import team.trimark.gateway.Constants;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * The unit of money.
 */
public class Money extends Number implements Comparable<Number>, Serializable {
    private static final long serialVersionUID = 1L;

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
    // Declared as Map for the API, but always the serializable immutable map from Map.copyOf (String/BigDecimal are
    // serializable), so the whole figure serializes; the [serial] warning on the interface-typed field is a false alarm.
    @SuppressWarnings("serial")
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

    /**
     * Re-expresses this money in the target currency. When a notation for the target currency already exists it becomes
     * the new base amount directly - preserving any residual foreign value even when the current base amount is zero -
     * and the current base is folded into the conversion map.
     *
     * <p>When no notation for the target currency exists a rate would be required: a figure that is zero in every
     * notation still rebases to zero (the operation never fails on a wholly-zero amount), while a figure that holds
     * value in any currency - including a residual that is zero in its base but non-zero in a conversion - throws.
     *
     * @param targetCurrency The target currency
     * @return The rebased money
     * @throws IllegalArgumentException When the figure holds value and no conversion to the target currency exists
     */
    public Money rebase(String targetCurrency) throws IllegalArgumentException {
        if (Objects.equals(targetCurrency, currency)) {
            return this; // already denominated in the target currency
        }

        Optional<BigDecimal> knownTarget = getAmountInCurrency(targetCurrency);
        if (knownTarget.isPresent()) {
            // The target value is already known; re-denominate to it and fold the old base into the conversions.
            Map<String, BigDecimal> newConversions = new HashMap<>();
            newConversions.put(currency, amount);
            conversions.forEach((k, v) -> {
                if (Objects.equals(k, targetCurrency) || Objects.equals(k, currency)) return;
                newConversions.put(k, v);
            });

            return new Money(knownTarget.get(), targetCurrency, newConversions);
        }

        // The target value is unknown, so a rate would be required. Only a figure that is zero in every notation can
        // rebase without one - a residual with value in another currency cannot be converted to an unknown rate.
        if (isZeroInEveryNotation()) {
            return new Money(BigDecimal.ZERO, targetCurrency, Map.of(currency, BigDecimal.ZERO));
        }

        throw new IllegalArgumentException("Cannot convert to currency without exchange rate.");
    }

    /**
     * Returns whether this figure is zero in its base currency and in every conversion.
     *
     * @return {@code true} if every notation is zero
     */
    private boolean isZeroInEveryNotation() {
        return amount.signum() == 0 && conversions.values().stream().allMatch(v -> v.signum() == 0);
    }

    /**
     * Re-expresses this money in the target currency at the given exchange rate. The target becomes the new base
     * currency, the current base is folded into the conversion map, and stale conversions are carried over as-is.
     *
     * @param targetCurrency       The target currency
     * @param exchangeRate         The exchange rate, which must be positive and non-null
     * @param incumbentAsNumerator {@code true} when the rate is expressed with the current (incumbent) currency as the
     *                             numerator (incumbent per target); {@code false} when it is target per incumbent
     * @return The rebased money
     * @throws IllegalArgumentException When the exchange rate is null or not positive
     */
    public Money rebase(String targetCurrency, BigDecimal exchangeRate, boolean incumbentAsNumerator) {
        if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Exchange rate must be positive and non-null.");
        }

        // newAmount = amount * (target per incumbent). When the incumbent is the numerator we must invert the rate.
        BigDecimal rateToTarget = incumbentAsNumerator
                ? BigDecimal.ONE.divide(exchangeRate, Constants.BIG_DECIMAL_SCALE, RoundingMode.HALF_EVEN)
                : exchangeRate;

        BigDecimal newAmount = amount.multiply(rateToTarget);
        Map<String, BigDecimal> newConversions = new HashMap<>();

        newConversions.put(currency, amount);
        conversions.forEach((k, v) -> {
            if (Objects.equals(k, targetCurrency) || Objects.equals(k, currency)) return;
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
            // Prefer expressing the other money in this currency; otherwise express this money in the other currency.
            Optional<BigDecimal> otherInThis = m.getAmountInCurrency(currency);
            if (otherInThis.isPresent()) {
                return amount.compareTo(otherInThis.get());
            }

            Optional<BigDecimal> thisInOther = getAmountInCurrency(m.currency);
            if (thisInOther.isPresent()) {
                return thisInOther.get().compareTo(m.amount);
            }

            throw new RuntimeException("Currency mismatch - cannot compare the two amounts.");
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

    /**
     * Compares two money instances by value. Amounts are compared numerically (scale-insensitive, consistent with
     * {@link #compareTo(Number)}), so {@code 2} and {@code 2.00} in the same currency are equal.
     *
     * @param o The object to compare
     * @return {@code true} if the two represent the same currency, amount, and conversions
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money m)) return false;

        return Objects.equals(currency, m.currency)
                && numericEquals(amount, m.amount)
                && conversionsEqual(m.conversions);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}. Derived from the currency alone, which every equal
     * pair shares, keeping the code stable across differing amount scales.
     *
     * @return The hash code
     */
    @Override
    public int hashCode() {
        return Objects.hashCode(currency);
    }

    /**
     * Returns whether two nullable {@link BigDecimal}s are numerically equal (ignoring scale).
     */
    private static boolean numericEquals(BigDecimal a, BigDecimal b) {
        if (a == null || b == null) return a == b;
        return a.compareTo(b) == 0;
    }

    /**
     * Returns whether this instance's conversions are numerically equal to the other's (same keys, same values).
     */
    private boolean conversionsEqual(Map<String, BigDecimal> other) {
        if (conversions.size() != other.size()) return false;
        for (Map.Entry<String, BigDecimal> e : conversions.entrySet()) {
            if (!numericEquals(e.getValue(), other.get(e.getKey()))) return false;
        }
        return true;
    }

    /**
     * Serializes this money to a string that {@link #fromString(String)} can round-trip. Conversions are emitted in a
     * deterministic (currency-sorted) order. The format assumes currency codes contain none of {@code , = { }}.
     *
     * @return The serialized form, e.g. {@code Money{amount=100,currency=USD,conversions={EUR=90.00}}}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Money{amount=").append(amount)
                .append(",currency=").append(currency)
                .append(",conversions={");

        boolean first = true;
        for (Map.Entry<String, BigDecimal> e : new TreeMap<>(conversions).entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }

        return sb.append("}}").toString();
    }

    /**
     * Deserializes a money from the form produced by {@link #toString()}.
     *
     * @param s The serialized form
     * @return The money instance
     * @throws IllegalArgumentException When the string is not a well-formed serialized money
     */
    public static Money fromString(String s) throws IllegalArgumentException {
        Objects.requireNonNull(s, "Cannot deserialize null.");
        String str = s.trim();

        String prefix = "Money{";
        if (!str.startsWith(prefix) || !str.endsWith("}")) {
            throw new IllegalArgumentException("Not a serialized Money: " + s);
        }

        try {
            // body := amount=<amount>,currency=<currency>,conversions={<conversions>}
            String body = str.substring(prefix.length(), str.length() - 1);

            int amountStart = body.indexOf("amount=") + "amount=".length();
            int currencyMark = body.indexOf(",currency=");
            int currencyStart = currencyMark + ",currency=".length();
            int conversionsMark = body.indexOf(",conversions=");
            int conversionsStart = body.indexOf('{', conversionsMark) + 1;

            BigDecimal amount = new BigDecimal(body.substring(amountStart, currencyMark));
            String currency = body.substring(currencyStart, conversionsMark);

            // The conversions block is the remainder, minus its own closing brace.
            String conversionsBody = body.substring(conversionsStart, body.length() - 1);
            Map<String, BigDecimal> conversions = new HashMap<>();
            if (!conversionsBody.isEmpty()) {
                for (String pair : conversionsBody.split(",")) {
                    int eq = pair.indexOf('=');
                    conversions.put(pair.substring(0, eq), new BigDecimal(pair.substring(eq + 1)));
                }
            }

            return new Money(amount, currency, conversions);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not a serialized Money: " + s, e);
        }
    }
}
