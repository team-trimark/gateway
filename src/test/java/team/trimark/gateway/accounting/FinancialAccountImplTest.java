package team.trimark.gateway.accounting;

import org.junit.jupiter.api.Test;
import team.trimark.gateway.type.BusinessDateTime;
import team.trimark.gateway.type.BusinessTemporalRange;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link FinancialAccountImpl}.
 */
class FinancialAccountImplTest {
    @Test
    void identityAndTypeAreExposed() {
        FinancialAccount a = FinancialAccount.of("1000", "Cash", "Cash on hand", FinancialAccountType.ASSET);

        assertEquals("1000", a.getAccountId());
        assertEquals("Cash", a.getAccountShortName());
        assertEquals("Cash on hand", a.getAccountFullName());
        assertEquals(FinancialAccountType.ASSET, a.getType());
    }

    @Test
    void renamingIsAllowed() {
        FinancialAccount a = FinancialAccount.of("1000", "Cash", "Cash on hand", FinancialAccountType.ASSET);

        a.setAccountShortName("Petty cash");
        a.setAccountFullName("Petty cash box");

        assertEquals("Petty cash", a.getAccountShortName());
        assertEquals("Petty cash box", a.getAccountFullName());
        // The identity does not change on rename.
        assertEquals("1000", a.getAccountId());
    }

    @Test
    void localizedNamesAndUnmodifiableLocaleMap() {
        FinancialAccount a = FinancialAccount.of("1000", "Cash", "Cash on hand", FinancialAccountType.ASSET,
                Map.of("ko", "현금"), null, null, List.of());

        assertEquals("현금", a.getAccountLocalizedName("ko").orElseThrow());
        assertTrue(a.getAccountLocalizedName("ja").isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> a.getLocaleMap().put("en", "Cash"));
    }

    @Test
    void usageLifecycleSetters() {
        FinancialAccount a = FinancialAccount.of("1000", "Cash", "Cash on hand", FinancialAccountType.ASSET);
        assertTrue(a.getUsageStartedDateTime().isEmpty());

        BusinessDateTime start = BusinessDateTime.of(2026, 1, 1, 0);
        BusinessDateTime end = BusinessDateTime.of(2026, 12, 31, 0);
        a.setUsageStartedDateTime(start);
        a.setUsageEndedDateTime(end);

        assertEquals(start, a.getUsageStartedDateTime().orElseThrow());
        assertEquals(end, a.getUsageEndedDateTime().orElseThrow());
    }

    @Test
    void pausedRangesAreUnmodifiable() {
        BusinessTemporalRange range = BusinessTemporalRange.of(
                BusinessDateTime.of(2026, 6, 1, 0), BusinessDateTime.of(2026, 6, 30, 0));
        FinancialAccount a = FinancialAccount.of("1000", "Cash", "Cash on hand", FinancialAccountType.ASSET,
                Map.of(), null, null, List.of(range));

        assertEquals(List.of(range), a.getUsagePausedRanges());
        assertThrows(UnsupportedOperationException.class, () -> a.getUsagePausedRanges().clear());
    }

    @Test
    void equalityIsByAccountId() {
        FinancialAccount a = FinancialAccount.of("1000", "Cash", "Cash on hand", FinancialAccountType.ASSET);
        FinancialAccount sameId = FinancialAccount.of("1000", "Different name", "Different full", FinancialAccountType.LIABILITY);
        FinancialAccount otherId = FinancialAccount.of("2000", "Cash", "Cash on hand", FinancialAccountType.ASSET);

        assertEquals(a, sameId);
        assertEquals(a.hashCode(), sameId.hashCode());
        assertNotEquals(a, otherId);
    }

    @Test
    void nullIdentityIsRejected() {
        assertThrows(NullPointerException.class,
                () -> FinancialAccount.of(null, "Cash", "Cash on hand", FinancialAccountType.ASSET));
    }
}
