package team.trimark.gateway.type;

import java.util.Objects;

/**
 * A closed range between two {@link BusinessDateTime}s. The start must not be after the end.
 */
public class BusinessTemporalRange {
    /**
     * Creates a new range.
     *
     * @param start The start, inclusive
     * @param end   The end, inclusive
     * @return The range
     * @throws IllegalArgumentException When either bound is null, or the start is after the end
     */
    public static BusinessTemporalRange of(BusinessDateTime start, BusinessDateTime end) throws IllegalArgumentException {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Range bounds must be non-null.");
        }
        if (start.compareTo(end) > 0) {
            throw new IllegalArgumentException("Range start must not be after its end.");
        }

        return new BusinessTemporalRange(start, end);
    }

    /**
     * Private constructor.
     *
     * @param start The start
     * @param end   The end
     */
    private BusinessTemporalRange(BusinessDateTime start, BusinessDateTime end) {
        this.start = start;
        this.end = end;
    }

    private final BusinessDateTime start;
    private final BusinessDateTime end;

    /**
     * Returns the start of this range, inclusive.
     *
     * @return The start
     */
    public BusinessDateTime getStart() {
        return start;
    }

    /**
     * Returns the end of this range, inclusive.
     *
     * @return The end
     */
    public BusinessDateTime getEnd() {
        return end;
    }

    /**
     * Returns whether the given timestamp falls within this range, bounds inclusive.
     *
     * @param time The timestamp
     * @return {@code true} if {@code start <= time <= end}
     */
    public boolean contains(BusinessDateTime time) {
        return start.compareTo(time) <= 0 && end.compareTo(time) >= 0;
    }

    /**
     * Returns whether this range overlaps another. Two ranges that merely touch at a shared bound (one's end equals the
     * other's start) count as overlapping, since both bounds are inclusive.
     *
     * @param other The other range
     * @return {@code true} if the two ranges share at least one instant
     */
    public boolean overlaps(BusinessTemporalRange other) {
        return start.compareTo(other.end) <= 0 && other.start.compareTo(end) <= 0;
    }

    /**
     * Compares this range to another by value.
     *
     * @param o The object to compare
     * @return {@code true} if the two share the same start and end
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BusinessTemporalRange r)) return false;

        return Objects.equals(start, r.start) && Objects.equals(end, r.end);
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return The hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }

    /**
     * Serializes this range to a string that {@link #fromString(String)} can round-trip.
     *
     * @return The serialized form
     */
    @Override
    public String toString() {
        return "BusinessTemporalRange{start=" + start + ",end=" + end + "}";
    }

    /**
     * Deserializes a range from the form produced by {@link #toString()}.
     *
     * @param s The serialized form
     * @return The range
     * @throws IllegalArgumentException When the string is not a well-formed serialized range
     */
    public static BusinessTemporalRange fromString(String s) throws IllegalArgumentException {
        Objects.requireNonNull(s, "Cannot deserialize null.");
        String str = s.trim();

        String prefix = "BusinessTemporalRange{";
        if (!str.startsWith(prefix) || !str.endsWith("}")) {
            throw new IllegalArgumentException("Not a serialized BusinessTemporalRange: " + s);
        }

        try {
            // body := start=<bdt>,end=<bdt>, where each <bdt> carries its own balanced braces.
            String body = str.substring(prefix.length(), str.length() - 1);

            int startMark = body.indexOf("start=") + "start=".length();
            int startOpen = body.indexOf('{', startMark);
            int startClose = matchBrace(body, startOpen);
            String startStr = body.substring(startMark, startClose + 1);

            int endMark = body.indexOf("end=", startClose) + "end=".length();
            int endOpen = body.indexOf('{', endMark);
            int endClose = matchBrace(body, endOpen);
            String endStr = body.substring(endMark, endClose + 1);

            return of(BusinessDateTime.fromString(startStr), BusinessDateTime.fromString(endStr));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Not a serialized BusinessTemporalRange: " + s, e);
        }
    }

    /**
     * Returns the index of the {@code '}'} matching the {@code '{'} at {@code openIndex}, accounting for nesting.
     *
     * @param s         The string
     * @param openIndex The index of the opening brace
     * @return The index of the matching closing brace
     */
    private static int matchBrace(String s, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }

        throw new IllegalArgumentException("Unbalanced braces in: " + s);
    }
}
