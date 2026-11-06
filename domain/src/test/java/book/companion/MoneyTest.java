package book.companion;

import static org.assertj.core.api.Assertions.*;

import book.companion.domain.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MoneyTest {
  @Test
  void exactMultiplication() {
    assertThat(new Money(new BigDecimal("0.10")).times(3).amount()).isEqualByComparingTo("0.30");
  }

  @Test
  void canonicalEquality() {
    assertThat(new Money(new BigDecimal("2.0"))).isEqualTo(new Money(new BigDecimal("2.00")));
  }

  @Test
  void rejectsNegative() {
    assertThatThrownBy(() -> new Money(new BigDecimal("-1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsRounding() {
    assertThatThrownBy(() -> new Money(new BigDecimal("1.001")))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void rejectsInvalidQuantity() {
    assertThatThrownBy(() -> new Money(BigDecimal.ONE).times(0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
