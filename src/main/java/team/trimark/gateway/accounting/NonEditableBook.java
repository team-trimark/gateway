package team.trimark.gateway.accounting;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * A non-editable {@link Book}. Its default currency and entries are fixed at construction; every mutation method throws
 * {@link UnsupportedOperationException}. Instances are created through {@link Book#of(String, List)}.
 */
public final class NonEditableBook implements Book {
    /**
     * Package-private constructor, invoked by {@link Book#of(String, List)}.
     *
     * @param defaultCurrency The default currency
     * @param entries         The entries
     */
    NonEditableBook(String defaultCurrency, List<FinancialEntry> entries) {
        this.defaultCurrency = defaultCurrency;
        this.entries = List.copyOf(entries);
    }

    private final String defaultCurrency;
    private final List<FinancialEntry> entries;

    @Override
    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    @Override
    public List<FinancialEntry> getEntries() {
        return entries;
    }

    @Override
    public void setDefaultCurrency(String defaultCurrency) {
        throw notEditable();
    }

    @Override
    public void addEntry(FinancialEntry entry) {
        throw notEditable();
    }

    @Override
    public void addEntries(Collection<? extends FinancialEntry> entries) {
        throw notEditable();
    }

    @Override
    public void removeEntryAt(int index) {
        throw notEditable();
    }

    @Override
    public void clearEntries() {
        throw notEditable();
    }

    /**
     * Compares by content - default currency and entries - so a book is equal to any other {@link Book}, of either
     * variant, carrying the same currency and the same entries in the same order.
     *
     * @param o The object to compare
     * @return {@code true} when the two books have the same content
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Book other)) return false;

        return Objects.equals(defaultCurrency, other.getDefaultCurrency()) && entries.equals(other.getEntries());
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     *
     * @return The hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(defaultCurrency, entries);
    }

    /**
     * Returns the exception thrown by every mutation on a non-editable book.
     *
     * @return The exception
     */
    private static UnsupportedOperationException notEditable() {
        return new UnsupportedOperationException("This book is not editable; create an editable book with Book.editable(...).");
    }
}
