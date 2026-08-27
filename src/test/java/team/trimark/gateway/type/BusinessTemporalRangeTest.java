package team.trimark.gateway.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BusinessTemporalRange}.
 */
class BusinessTemporalRangeTest {
    private static BusinessDateTime at(int day, long ms) {
        return BusinessDateTime.of(2026, 1, day, ms);
    }

    @Test
    void ofRejectsNullBounds() {
        assertThrows(IllegalArgumentException.class, () -> BusinessTemporalRange.of(null, at(1, 0)));
        assertThrows(IllegalArgumentException.class, () -> BusinessTemporalRange.of(at(1, 0), null));
    }

    @Test
    void ofRejectsInvertedRange() {
        assertThrows(IllegalArgumentException.class, () -> BusinessTemporalRange.of(at(2, 0), at(1, 0)));
    }

    @Test
    void ofAcceptsEqualBounds() {
        assertDoesNotThrow(() -> BusinessTemporalRange.of(at(1, 100), at(1, 100)));
    }

    @Test
    void containsIsInclusive() {
        BusinessTemporalRange r = BusinessTemporalRange.of(at(1, 0), at(3, 0));

        assertTrue(r.contains(at(1, 0)));
        assertTrue(r.contains(at(2, 0)));
        assertTrue(r.contains(at(3, 0)));
        assertFalse(r.contains(at(1, -1)));
        assertFalse(r.contains(at(4, 0)));
    }

    @Test
    void overlapsIncludingTouchingBounds() {
        BusinessTemporalRange a = BusinessTemporalRange.of(at(1, 0), at(3, 0));
        BusinessTemporalRange overlapping = BusinessTemporalRange.of(at(2, 0), at(5, 0));
        BusinessTemporalRange touching = BusinessTemporalRange.of(at(3, 0), at(5, 0));
        BusinessTemporalRange disjoint = BusinessTemporalRange.of(at(4, 0), at(5, 0));

        assertTrue(a.overlaps(overlapping));
        assertTrue(a.overlaps(touching));
        assertFalse(a.overlaps(disjoint));
    }

    @Test
    void equalsAndHashCode() {
        BusinessTemporalRange a = BusinessTemporalRange.of(at(1, 0), at(3, 0));
        BusinessTemporalRange b = BusinessTemporalRange.of(at(1, 0), at(3, 0));

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, BusinessTemporalRange.of(at(1, 0), at(4, 0)));
    }

    @Test
    void serializationRoundTripsWithNestedTimes() {
        BusinessTemporalRange r = BusinessTemporalRange.of(at(1, 100), at(3, -200));

        assertEquals(r, BusinessTemporalRange.fromString(r.toString()));
    }

    @Test
    void fromStringRejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> BusinessTemporalRange.fromString("BusinessTemporalRange{oops}"));
    }
}
