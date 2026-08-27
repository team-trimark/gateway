package team.trimark.gateway.math;

import team.trimark.gateway.Constants;
import team.trimark.gateway.type.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

/**
 * Contains business math.
 *
 * <p>Additive operations ({@link #add} and {@link #subtract}) treat each {@link Money} as a bundle of currency
 * notations and operate elementwise over the currencies that <b>both</b> operands carry (the FX notations are blended);
 * a notation held by only one operand is disregarded, and the result is denominated in the incumbent's base currency,
 * which is why they are not commutative. Scalar operations ({@link #multiply} and {@link #divide}) scale every notation
 * of a single {@link Money} by a {@link BigDecimal}.
 */
public final class BusinessMath {
    /**
     * Adds money, *not* commutative. Fails if the two values do not have a common currency. FX rates are blended - FX notations which only one parameter have are disregarded.
     * @param m The incumbent - the return value will have its default currency
     * @param n The money to add to the incumbent
     * @return The sum {@code m + n}.
     * @throws NullPointerException When a {@code null} parameter is given
     * @throws IllegalArgumentException When the calculation is not possible
     */
    public static Money add(Money m, Money n) throws NullPointerException, IllegalArgumentException {
        return combine(m, n, "add", BigDecimal::add);
    }

    /**
     * Subtracts money, *not* commutative. Fails if the two values do not have a common currency. FX notations which
     * only one parameter has are disregarded.
     * @param m The incumbent - the return value will have its default currency
     * @param n The money to subtract from the incumbent
     * @return The difference {@code m - n}.
     * @throws NullPointerException When a {@code null} parameter is given
     * @throws IllegalArgumentException When the calculation is not possible
     */
    public static Money subtract(Money m, Money n) throws NullPointerException, IllegalArgumentException {
        return combine(m, n, "subtract", BigDecimal::subtract);
    }

    /**
     * Multiplies money by a scalar - every currency notation is multiplied by the factor.
     * @param m      The money
     * @param factor The scalar factor
     * @return The product {@code m * factor}
     * @throws NullPointerException When a {@code null} parameter is given
     */
    public static Money multiply(Money m, BigDecimal factor) throws NullPointerException {
        Objects.requireNonNull(m);
        Objects.requireNonNull(factor);
        return mapNotations(m, v -> v.multiply(factor));
    }

    /**
     * Divides money by a scalar - every currency notation is divided by the divisor. Inexact quotients are rounded to
     * {@link Constants#BIG_DECIMAL_SCALE} decimal places.
     * @param numerator The money
     * @param divisor   The scalar divisor
     * @return The quotient {@code numerator / divisor}
     * @throws NullPointerException When a {@code null} parameter is given
     * @throws IllegalArgumentException When the divisor is zero
     */
    public static Money divide(Money numerator, BigDecimal divisor) throws NullPointerException, IllegalArgumentException {
        Objects.requireNonNull(numerator);
        Objects.requireNonNull(divisor);
        if (divisor.signum() == 0) throw new IllegalArgumentException("Division by zero.");
        return mapNotations(numerator, v -> v.divide(divisor, Constants.BIG_DECIMAL_SCALE, RoundingMode.HALF_EVEN));
    }

    /**
     * Returns the absolute value of the money - every currency notation is taken absolute.
     * @param m The money
     * @return {@code |m|}
     * @throws NullPointerException When a {@code null} parameter is given
     */
    public static Money abs(Money m) throws NullPointerException {
        Objects.requireNonNull(m);
        return mapNotations(m, BigDecimal::abs);
    }

    /**
     * Returns the negation of the money - every currency notation is negated.
     * @param m The money
     * @return {@code -m}
     * @throws NullPointerException When a {@code null} parameter is given
     */
    public static Money negate(Money m) throws NullPointerException {
        Objects.requireNonNull(m);
        return mapNotations(m, BigDecimal::negate);
    }

    /**
     * Combines two monies elementwise over their shared currencies, denominating the result in {@code m}'s base
     * currency.
     *
     * @param m         The incumbent
     * @param n         The other operand
     * @param operation The operation name, for error messages
     * @param operator  The per-currency operator
     * @return The combined money
     */
    private static Money combine(Money m, Money n, String operation, BinaryOperator<BigDecimal> operator) {
        Objects.requireNonNull(m);
        Objects.requireNonNull(n);

        String baseCurrency = m.getCurrency();
        if (!n.hasCurrency(baseCurrency)) {
            throw new IllegalArgumentException("The right operand of " + operation
                    + " does not have a notation in the incumbent's base currency.");
        }

        Map<String, BigDecimal> conversions = new HashMap<>();
        BigDecimal baseAmount = null;
        for (String currency : commonCurrencies(m, n)) {
            BigDecimal value = operator.apply(
                    m.getAmountInCurrency(currency).orElseThrow(),
                    n.getAmountInCurrency(currency).orElseThrow());

            if (currency.equals(baseCurrency)) {
                baseAmount = value;
            } else {
                conversions.put(currency, value);
            }
        }

        return Money.of(baseAmount, baseCurrency, conversions);
    }

    /**
     * Applies the operator to every currency notation, preserving the base currency.
     *
     * @param m        The money
     * @param operator The per-notation operator
     * @return The mapped money
     */
    private static Money mapNotations(Money m, UnaryOperator<BigDecimal> operator) {
        String baseCurrency = m.getCurrency();
        Map<String, BigDecimal> conversions = new HashMap<>();
        m.getConversions().forEach((currency, amount) -> {
            if (!currency.equals(baseCurrency)) conversions.put(currency, operator.apply(amount));
        });

        return Money.of(operator.apply(m.getAmount()), baseCurrency, conversions);
    }

    /**
     * Returns the currencies both monies can express (base currency plus conversions), intersected.
     *
     * @param a The first money
     * @param b The second money
     * @return The shared currencies
     */
    private static Set<String> commonCurrencies(Money a, Money b) {
        Set<String> currencies = currenciesOf(a);
        currencies.retainAll(currenciesOf(b));
        return currencies;
    }

    /**
     * Returns every currency the money can express: its base currency and every conversion.
     *
     * @param m The money
     * @return The currencies
     */
    private static Set<String> currenciesOf(Money m) {
        Set<String> currencies = new HashSet<>(m.getConversions().keySet());
        currencies.add(m.getCurrency());
        return currencies;
    }

    /**
     * Not instantiable.
     */
    private BusinessMath() {
        throw new AssertionError("No team.trimark.gateway.math.BusinessMath instances for you!");
    }
}
