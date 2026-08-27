package team.trimark.gateway.type;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;

/**
 * Business date and time. No time zone, no bounds for time. -48:00 hours, 12:00 hours, +90 hours are all valid timestamps.
 * Always compare date first - then time. Signed {@code long} for the time, {@link LocalDate} for the date.
 */
public class BusinessDateTime implements Comparable<BusinessDateTime>, Serializable {
    /**
     * Arbitrary number chosen to mark closing entries, error corrections, or other non-standard entries.
     */
    public static final long CLOSING_MILLISECONDS = 9_999_999_999_999L;

    /**
     * Returns the closing BDT of the date.
     *
     * @param date The date
     * @return The BDT
     * @see #CLOSING_MILLISECONDS
     */
    public static BusinessDateTime atClose(LocalDate date) {
        return new BusinessDateTime(date, CLOSING_MILLISECONDS);
    }

    /**
     * Returns a BDT from a {@link TemporalAccessor}.
     *
     * @param t The timestamp
     * @return The BDT
     * @throws IllegalArgumentException When a necessary temporal field is not supported.
     */
    public static BusinessDateTime fromTemporal(TemporalAccessor t) throws IllegalArgumentException {
        if (!t.isSupported(ChronoField.MILLI_OF_DAY)) throw new IllegalArgumentException("MICRO_OF_DAY not supported.");
        if (!t.isSupported(ChronoField.DAY_OF_YEAR)) throw new IllegalArgumentException("DAY_OF_YEAR not supported.");

        return new BusinessDateTime(LocalDate.from(t), t.get(ChronoField.MILLI_OF_DAY));
    }

    public static BusinessDateTime of(int year, int month, int day, long milliseconds) {
        try {
            return new BusinessDateTime(LocalDate.of(year, month, day), milliseconds);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid parameters.", e);
        }
    }

    /**
     * Private constructor.
     *
     * @param date         The date
     * @param milliseconds The milliseconds
     */
    private BusinessDateTime(LocalDate date, long milliseconds) {
        this.date = date;
        this.milliseconds = milliseconds;
    }

    private static final long MILLISECONDS_AT_24_00 = 86400000;

    private final LocalDate date;
    private final long milliseconds;

    /**
     * Returns the date in the format {@link LocalDate}.
     *
     * @return The date
     */
    public LocalDate getDate() {
        return date;
    }

    /**
     * Returns the milliseconds in the format {@code long}.
     *
     * @return The milliseconds
     */
    public long getMilliseconds() {
        return milliseconds;
    }

    /**
     * Returns whether this BDT is extended beyond the usual {@code 00:00-24:00} range.
     *
     * @return {@code true} if it is extended
     */
    public boolean isExtended() {
        return milliseconds < 0 || milliseconds > MILLISECONDS_AT_24_00;
    }

    /**
     * Returns the clamped milliseconds in the format {@code long}, where it is cropped to {@code [0, 86,400,000}.
     *
     * @return The clamped milliseconds.
     */
    public long getMillisecondsClamped() {
        return Math.min(Math.max(milliseconds, 0), MILLISECONDS_AT_24_00);
    }

    /**
     * Converts and returns the BDT as a {@link LocalDateTime}. This operation may be lossy, check {@link #isExtended()} first.
     *
     * @return The converted BDT
     */
    public LocalDateTime asLocalDateTime() {
        long nanos = milliseconds * 1_000_000;
        LocalTime time = LocalTime.ofNanoOfDay(nanos);

        return date.atTime(time);
    }

    /**
     * Compares this BDT to the other BDT. Compares date first, then time.
     *
     * @param bdt The BDT to compare to.
     * @return The comparison result, ranging {@code [-1, 1]}.
     */
    @Override
    public int compareTo(BusinessDateTime bdt) {
        int dateCompare = date.compareTo(bdt.date);
        if (dateCompare != 0) return dateCompare;

        return Long.compare(milliseconds, bdt.milliseconds);
    }
}
