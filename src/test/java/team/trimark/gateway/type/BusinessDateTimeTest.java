package team.trimark.gateway.type;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BusinessDateTime}.
 */
class BusinessDateTimeTest {
    @Test
    void isExtendedTreatsBelowZeroAndAtOrAbove24hAsExtended() {
        assertFalse(BusinessDateTime.of(2026, 1, 1, 0).isExtended());
        assertFalse(BusinessDateTime.of(2026, 1, 1, 86_399_999L).isExtended());
        assertTrue(BusinessDateTime.of(2026, 1, 1, 86_400_000L).isExtended()); // exactly 24:00
        assertTrue(BusinessDateTime.of(2026, 1, 1, -1L).isExtended());
        assertTrue(BusinessDateTime.atClose(LocalDate.of(2026, 1, 1)).isExtended());
    }

    @Test
    void getMillisecondsClampedCropsToRepresentableRange() {
        assertEquals(0L, BusinessDateTime.of(2026, 1, 1, -5000L).getMillisecondsClamped());
        assertEquals(500L, BusinessDateTime.of(2026, 1, 1, 500L).getMillisecondsClamped());
        assertEquals(86_399_999L, BusinessDateTime.of(2026, 1, 1, 86_400_000L).getMillisecondsClamped());
        assertEquals(86_399_999L, BusinessDateTime.atClose(LocalDate.of(2026, 1, 1)).getMillisecondsClamped());
    }

    @Test
    void asLocalDateTimeIsExactForNormalTimes() {
        BusinessDateTime bdt = BusinessDateTime.of(2026, 1, 1, 45_000_000L); // 12:30:00.000

        assertEquals(LocalDateTime.of(2026, 1, 1, 12, 30, 0), bdt.asLocalDateTime());
    }

    @Test
    void asLocalDateTimeNeverThrowsForExtendedOrClosingTimes() {
        assertDoesNotThrow(() -> BusinessDateTime.of(2026, 1, 1, 86_400_000L).asLocalDateTime());
        assertDoesNotThrow(() -> BusinessDateTime.atClose(LocalDate.of(2026, 1, 1)).asLocalDateTime());
        assertDoesNotThrow(() -> BusinessDateTime.of(2026, 1, 1, -5000L).asLocalDateTime());

        assertEquals(LocalDateTime.of(2026, 1, 1, 23, 59, 59, 999_000_000),
                BusinessDateTime.atClose(LocalDate.of(2026, 1, 1)).asLocalDateTime());
    }

    @Test
    void comparesDateFirstThenTime() {
        BusinessDateTime early = BusinessDateTime.of(2026, 1, 1, 90_000_000L); // extended, late in day 1
        BusinessDateTime late = BusinessDateTime.of(2026, 1, 2, 0);            // start of day 2

        assertTrue(early.compareTo(late) < 0);
        assertTrue(BusinessDateTime.of(2026, 1, 1, 100L).compareTo(BusinessDateTime.of(2026, 1, 1, 200L)) < 0);
        assertEquals(0, BusinessDateTime.of(2026, 1, 1, 100L).compareTo(BusinessDateTime.of(2026, 1, 1, 100L)));
    }

    @Test
    void equalsAndHashCode() {
        BusinessDateTime a = BusinessDateTime.of(2026, 8, 27, 123L);
        BusinessDateTime b = BusinessDateTime.of(2026, 8, 27, 123L);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, BusinessDateTime.of(2026, 8, 27, 124L));
    }

    @Test
    void serializationRoundTrips() {
        BusinessDateTime normal = BusinessDateTime.of(2026, 8, 27, 45_000_000L);
        BusinessDateTime negative = BusinessDateTime.of(2026, 8, 27, -5000L);
        BusinessDateTime closing = BusinessDateTime.atClose(LocalDate.of(2026, 8, 27));

        assertEquals(normal, BusinessDateTime.fromString(normal.toString()));
        assertEquals(negative, BusinessDateTime.fromString(negative.toString()));
        assertEquals(closing, BusinessDateTime.fromString(closing.toString()));
    }

    @Test
    void invalidCalendarDateThrows() {
        assertThrows(IllegalArgumentException.class, () -> BusinessDateTime.of(2026, 13, 1, 0));
    }

    @Test
    void fromStringRejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> BusinessDateTime.fromString("nope"));
    }
}
