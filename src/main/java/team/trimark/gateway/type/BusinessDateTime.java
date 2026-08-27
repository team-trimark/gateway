package team.trimark.gateway.type;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.util.Objects;

/**
 * Business date and time. No time zone, no bounds for time. -48:00 hours, 12:00 hours, +90 hours are all valid timestamps.
 * Always compare date first - then time. Signed {@code long} for the time, {@link LocalDate} for the date.
 */
public class BusinessDateTime implements Comparable<BusinessDateTime>, Serializable {
    private static final long serialVersionUID = 1L;

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
     * Returns a BDT from a {@link TemporalAccessor}. The accessor must supply a date - i.e. support
     * {@link ChronoField#EPOCH_DAY}, which is what {@link LocalDate#from} requires - and a time-of-day, i.e. support
     * {@link ChronoField#MILLI_OF_DAY}. Note that {@code EPOCH_DAY} is the field {@code LocalDate.from} actually needs;
     * partial fields such as {@code DAY_OF_YEAR} without a year cannot reconstruct a date. Anything else that prevents
     * a date or time from being derived (an unavailable field, an out-of-range value) is reported as an
     * {@link IllegalArgumentException} rather than propagating a raw {@link java.time.DateTimeException}.
     *
     * @param t The timestamp
     * @return The BDT
     * @throws IllegalArgumentException When the accessor is null, lacks a required field, or a value cannot be derived
     */
    public static BusinessDateTime fromTemporal(TemporalAccessor t) throws IllegalArgumentException {
        if (t == null) throw new IllegalArgumentException("Temporal must be non-null.");
        if (!t.isSupported(ChronoField.EPOCH_DAY)) throw new IllegalArgumentException("EPOCH_DAY not supported - the temporal has no date component.");
        if (!t.isSupported(ChronoField.MILLI_OF_DAY)) throw new IllegalArgumentException("MILLI_OF_DAY not supported - the temporal has no time-of-day component.");

        try {
            return new BusinessDateTime(LocalDate.from(t), t.get(ChronoField.MILLI_OF_DAY));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Cannot derive a BusinessDateTime from the given temporal.", e);
        }
    }

    /**
     * Returns a BDT from calendar fields and a millisecond-of-day.
     *
     * @param year         The proleptic year
     * @param month        The month, from 1 (January) to 12 (December)
     * @param day          The day of month, from 1 to 31
     * @param milliseconds The signed millisecond-of-day; may be extended (negative, or at/beyond 24:00)
     * @return The BDT
     * @throws IllegalArgumentException When the calendar date (year, month, day) is invalid
     */
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

    /**
     * The last millisecond-of-day that maps to a valid {@link LocalTime} ({@code 23:59:59.999}). {@code 24:00} itself
     * has no exact {@link LocalTime}, so it is the exclusive upper bound of the representable range.
     */
    private static final long MAX_REPRESENTABLE_MILLISECOND = MILLISECONDS_AT_24_00 - 1;

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
     * Returns whether this BDT falls outside the range representable as a {@link LocalTime}, i.e. anything below
     * {@code 00:00} or at/above {@code 24:00}. When this returns {@code false}, {@link #asLocalDateTime()} is exact;
     * {@code 24:00} counts as extended because it has no exact {@link LocalTime}.
     *
     * @return {@code true} if it is extended
     */
    public boolean isExtended() {
        return milliseconds < 0 || milliseconds >= MILLISECONDS_AT_24_00;
    }

    /**
     * Returns the clamped milliseconds in the format {@code long}, where it is cropped to {@code [0, 86,399,999]} - the
     * range that maps to a valid {@link LocalTime}.
     *
     * @return The clamped milliseconds.
     */
    public long getMillisecondsClamped() {
        return Math.min(Math.max(milliseconds, 0), MAX_REPRESENTABLE_MILLISECOND);
    }

    /**
     * Converts and returns the BDT as a {@link LocalDateTime}. This operation may be lossy for extended values - the
     * time is clamped to {@link #getMillisecondsClamped()} so the conversion never throws; check {@link #isExtended()}
     * first if an exact conversion is required.
     *
     * @return The converted BDT
     */
    public LocalDateTime asLocalDateTime() {
        long nanos = getMillisecondsClamped() * 1_000_000L;
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

    /**
     * Compares this BDT to another by value.
     *
     * @param o The object to compare
     * @return {@code true} if the two share the same date and milliseconds
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BusinessDateTime bdt)) return false;

        return milliseconds == bdt.milliseconds && Objects.equals(date, bdt.date);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return The hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(date, milliseconds);
    }

    /**
     * Serializes this BDT to a string that {@link #fromString(String)} can round-trip.
     *
     * @return The serialized form, e.g. {@code BusinessDateTime{date=2026-01-01,milliseconds=123}}
     */
    @Override
    public String toString() {
        return "BusinessDateTime{date=" + date + ",milliseconds=" + milliseconds + "}";
    }

    /**
     * Deserializes a BDT from the form produced by {@link #toString()}.
     *
     * @param s The serialized form
     * @return The BDT
     * @throws IllegalArgumentException When the string is not a well-formed serialized BDT
     */
    public static BusinessDateTime fromString(String s) throws IllegalArgumentException {
        Objects.requireNonNull(s, "Cannot deserialize null.");
        String str = s.trim();

        String prefix = "BusinessDateTime{";
        if (!str.startsWith(prefix) || !str.endsWith("}")) {
            throw new IllegalArgumentException("Not a serialized BusinessDateTime: " + s);
        }

        try {
            // body := date=<date>,milliseconds=<milliseconds>
            String body = str.substring(prefix.length(), str.length() - 1);

            int dateStart = body.indexOf("date=") + "date=".length();
            int millisecondsMark = body.indexOf(",milliseconds=");
            int millisecondsStart = millisecondsMark + ",milliseconds=".length();

            LocalDate date = LocalDate.parse(body.substring(dateStart, millisecondsMark));
            long milliseconds = Long.parseLong(body.substring(millisecondsStart));

            return new BusinessDateTime(date, milliseconds);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not a serialized BusinessDateTime: " + s, e);
        }
    }
}
