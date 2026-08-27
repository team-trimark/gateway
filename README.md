# Gateway

Part of the **Gateway Project** — a lightweight, open-source framework for tailored business management
solutions. By minimizing the feature set, we aim to maximize customizability and provide a versatile codebase
for any application.

- **Do you want to design your custom application?** Take this repository and start building.
- **Do you want a tailored solution made for you?** [Contact us!](#contact)

Gateway is a small, dependency-free Java library. It gives you correct, precise primitives for double-entry
bookkeeping and business time — and deliberately stops there, leaving the frontend, storage, and workflow
decisions to you.

## Features

- **Precision bookkeeping** — monetary math is exact (`BigDecimal`), and every inexact division falls back to a
  default of **32 decimal points** (`Constants.BIG_DECIMAL_SCALE`).
- **Extended timestamps** — no arbitrary limits on how far into the past or future a time may reach. `-96:00` and
  `+113:00` are both acceptable times, each booked against its designated business day.
- **Multi-currency support with retroactive rebasing** — carry a figure alongside its conversions and re-express
  it in another currency at a custom exchange rate. Rebasing is structurally lossy and computed at the same
  default 32 decimal points of precision.

## Limitations

- **No convenience features, by design.** The frontend designer implements them, in whatever form the
  application demands.
- **No database support, by design.** The system engineer implements persistence, in whatever architecture is
  appropriate for the application.

## Requirements

- Java 17+
- Maven (for building and running the tests)

Gateway has **no runtime dependencies**. JUnit 5 is used for tests only.

## Building

```bash
mvn test      # compile and run the test suite
mvn package   # build the jar
```

## Using it as a dependency

```xml
<dependency>
    <groupId>team.trimark.gateway</groupId>
    <artifactId>Gateway</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

## Module layout

| Package                          | Contents                                                                                     |
|----------------------------------|----------------------------------------------------------------------------------------------|
| `team.trimark.gateway.type`      | `Money`, `BusinessDateTime`, `BusinessTemporalRange`, `Constants`                             |
| `team.trimark.gateway.accounting`| `FinancialAccount`, `FinancialEntry`, `FinancialEntryLine`, `FinancialAccountType`, and impls |

Accounts and entries are created through static factories on their interfaces (`FinancialAccount.of(...)`,
`FinancialEntry.of(...)`); the value types use the same `of(...)` pattern.

## Example — a balanced journal entry

```java
import team.trimark.gateway.accounting.*;
import team.trimark.gateway.type.*;
import java.util.List;

FinancialAccount cash = FinancialAccount.of("1000", "Cash", "Cash on hand", FinancialAccountType.ASSET);
FinancialAccount sales = FinancialAccount.of("4000", "Sales", "Sales revenue", FinancialAccountType.INCOME);

FinancialEntry sale = FinancialEntry.of(
        "USD",
        List.of(FinancialEntryLine.of(cash, Money.of(100L, "USD"))),   // debit
        List.of(FinancialEntryLine.of(sales, Money.of(100L, "USD"))),  // credit
        BusinessDateTime.of(2026, 8, 27, 45_000_000L),                 // 12:30 on the business day
        "Cash sale",
        "Sold widgets for cash");

sale.isValid(); // true — both sides carry lines, no negatives, debits == credits
```

Debit and credit are ordered lists, so the same account may appear on several lines and identical lines are
permitted.

## Example — extended business time

```java
// 30:00 — i.e. 06:00 the following calendar morning, but still booked on 2026-08-27.
BusinessDateTime overnight = BusinessDateTime.of(2026, 8, 27, 108_000_000L);
overnight.isExtended();       // true — outside the 00:00–24:00 range
overnight.asLocalDateTime();  // clamped (lossy) conversion for display
```

## Example — multi-currency rebasing

```java
import java.math.BigDecimal;

Money usd = Money.of(new BigDecimal("100"), "USD");
Money eur = usd.rebase("EUR", new BigDecimal("0.92"), false); // 0.92 EUR per USD -> 92.00 EUR
eur.getAmountInCurrency("USD"); // the original 100 USD is kept as a conversion
```

## Contact

**Team Trimark of Mirae Research**

- Sales: [biz@sjun.me](mailto:biz@sjun.me) (En/Ko)
- KR BRN 726-15-02574
- Payment methods: PayPal, Korean bank transfer (계좌이체)
