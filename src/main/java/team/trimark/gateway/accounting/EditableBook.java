package team.trimark.gateway.accounting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An editable {@link Book}. Entries may be appended and removed and the default currency may be changed in place.
 * Instances are created through {@link Book#editable(String, List)} or {@link Book#editable(String)}.
 */
public final class EditableBook implements Book {
    /**
     * Package-private constructor, invoked by the {@link Book#editable} factories.
     *
     * @param defaultCurrency The default currency
     * @param entries         The initial entries
     */
    EditableBook(String defaultCurrency, List<FinancialEntry> entries) {
        this.defaultCurrency = defaultCurrency;
        this.entries = new ArrayList<>(entries);
    }

    private String defaultCurrency;
    private final List<FinancialEntry> entries;

    @Override
    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    @Override
    public List<FinancialEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    @Override
    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    @Override
    public void addEntry(FinancialEntry entry) {
        entries.add(Objects.requireNonNull(entry, "Entry must be non-null."));
    }

    @Override
    public void addEntries(Collection<? extends FinancialEntry> entries) {
        entries.forEach(this::addEntry);
    }

    @Override
    public void removeEntryAt(int index) {
        entries.remove(index);
    }

    @Override
    public void clearEntries() {
        entries.clear();
    }

    /**
     * Compares by content - default currency and entries - so a book is equal to any other {@link Book}, of either
     * variant, carrying the same currency and the same entries in the same order. As this book is mutable, its equality
     * and hash code change as it is edited.
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
}
