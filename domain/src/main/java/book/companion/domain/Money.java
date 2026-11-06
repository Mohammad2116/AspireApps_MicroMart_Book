package book.companion.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Chapters 2.3 and 12.23: exact monetary arithmetic, no silent rounding. */
public record Money(BigDecimal amount) {
  public Money {
    Objects.requireNonNull(amount);
    amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    if (amount.signum() < 0) throw new IllegalArgumentException("Negative amount");
  }

  public Money times(int quantity) {
    if (quantity <= 0) throw new IllegalArgumentException("Positive quantity required");
    return new Money(amount.multiply(BigDecimal.valueOf(quantity)));
  }
}
