package team.trimark.gateway;

/**
 * Project-wide constants.
 */
public final class Constants {
    /**
     * The scale ({@code 32} decimal points) used for {@link java.math.BigDecimal} divisions that would otherwise be
     * non-terminating. Paired with {@link java.math.RoundingMode#HALF_EVEN} wherever an inexact quotient is produced.
     */
    public static final int BIG_DECIMAL_SCALE = 32;

    /**
     * Not instantiable.
     */
    private Constants() {
        throw new AssertionError("No team.trimark.gateway.Constants instances for you!");
    }
}
