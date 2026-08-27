package team.trimark.gateway.math;

import org.junit.jupiter.api.Test;
import team.trimark.gateway.type.Money;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BusinessMath}.
 */
class BusinessMathTest {
    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    @Test
    void addUsesIncumbentBaseAndBlendsSharedNotations() {
        Money m = Money.of(bd("100"), "USD", Map.of("EUR", bd("90")));
        Money n = Money.of(bd("50"), "USD", Map.of("EUR", bd("45")));

        Money sum = BusinessMath.add(m, n);

        assertEquals("USD", sum.getCurrency());
        assertEquals(0, sum.getAmount().compareTo(bd("150")));
        assertEquals(0, sum.getAmountInCurrency("EUR").orElseThrow().compareTo(bd("135")));
    }

    @Test
    void addDisregardsOneSidedNotations() {
        Money m = Money.of(bd("100"), "USD", Map.of("EUR", bd("90")));
        Money n = Money.of(bd("50"), "USD", Map.of("KRW", bd("65000"))); // no EUR; USD shared only

        Money sum = BusinessMath.add(m, n);

        assertEquals(0, sum.getAmount().compareTo(bd("150")));
        assertTrue(sum.getAmountInCurrency("EUR").isEmpty()); // dropped - not shared
        assertTrue(sum.getAmountInCurrency("KRW").isEmpty());
    }

    @Test
    void addIsNotCommutativeInBaseCurrency() {
        Money usd = Money.of(bd("100"), "USD", Map.of("EUR", bd("90")));
        Money eur = Money.of(bd("90"), "EUR", Map.of("USD", bd("100")));

        assertEquals("USD", BusinessMath.add(usd, eur).getCurrency());
        assertEquals("EUR", BusinessMath.add(eur, usd).getCurrency());
    }

    @Test
    void addFailsWithoutCommonBaseCurrency() {
        Money m = Money.of(bd("100"), "USD");
        Money n = Money.of(bd("90"), "EUR");

        assertThrows(IllegalArgumentException.class, () -> BusinessMath.add(m, n));
        assertThrows(NullPointerException.class, () -> BusinessMath.add(m, null));
    }

    @Test
    void subtract() {
        Money m = Money.of(bd("100"), "USD", Map.of("EUR", bd("90")));
        Money n = Money.of(bd("30"), "USD", Map.of("EUR", bd("27")));

        Money diff = BusinessMath.subtract(m, n);

        assertEquals(0, diff.getAmount().compareTo(bd("70")));
        assertEquals(0, diff.getAmountInCurrency("EUR").orElseThrow().compareTo(bd("63")));
    }

    @Test
    void multiplyScalarScalesEveryNotation() {
        Money m = Money.of(bd("6"), "USD", Map.of("EUR", bd("5")));

        Money product = BusinessMath.multiply(m, bd("2"));

        assertEquals(0, product.getAmount().compareTo(bd("12")));
        assertEquals(0, product.getAmountInCurrency("EUR").orElseThrow().compareTo(bd("10")));
    }

    @Test
    void divideScalarScalesEveryNotationWithRounding() {
        Money numerator = Money.of(bd("10"), "USD", Map.of("EUR", bd("9")));

        Money quotient = BusinessMath.divide(numerator, bd("4"));

        assertEquals(0, quotient.getAmount().compareTo(bd("2.5")));
        assertEquals(0, quotient.getAmountInCurrency("EUR").orElseThrow().compareTo(bd("2.25")));
    }

    @Test
    void divideByZeroScalarThrows() {
        assertThrows(IllegalArgumentException.class, () -> BusinessMath.divide(Money.of(bd("10"), "USD"), bd("0")));
    }

    @Test
    void absAndNegateApplyToEveryNotation() {
        Money m = Money.of(bd("-100"), "USD", Map.of("EUR", bd("-90")));

        Money abs = BusinessMath.abs(m);
        assertEquals(0, abs.getAmount().compareTo(bd("100")));
        assertEquals(0, abs.getAmountInCurrency("EUR").orElseThrow().compareTo(bd("90")));

        Money negated = BusinessMath.negate(m);
        assertEquals(0, negated.getAmount().compareTo(bd("100")));
        assertEquals(0, negated.getAmountInCurrency("EUR").orElseThrow().compareTo(bd("90")));
    }

    @Test
    void nullArgumentsThrow() {
        assertThrows(NullPointerException.class, () -> BusinessMath.abs(null));
        assertThrows(NullPointerException.class, () -> BusinessMath.negate(null));
        assertThrows(NullPointerException.class, () -> BusinessMath.multiply(null, bd("1")));
        assertThrows(NullPointerException.class, () -> BusinessMath.multiply(Money.of(1L, "USD"), null));
        assertThrows(NullPointerException.class, () -> BusinessMath.divide(null, bd("1")));
        assertThrows(NullPointerException.class, () -> BusinessMath.divide(Money.of(1L, "USD"), null));
    }
}
