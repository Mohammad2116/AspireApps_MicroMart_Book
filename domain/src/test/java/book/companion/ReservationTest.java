package book.companion;

import static org.assertj.core.api.Assertions.*;

import book.companion.domain.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationTest {
  Reservation fresh() {
    return new Reservation(UUID.randomUUID(), Reservation.State.RESERVED);
  }

  @Test
  void releaseIsIdempotent() {
    var r = fresh().release();
    assertThat(r.release()).isEqualTo(r);
  }

  @Test
  void confirmIsIdempotent() {
    var r = fresh().confirm();
    assertThat(r.confirm()).isEqualTo(r);
  }

  @Test
  void cannotConfirmReleased() {
    var r = fresh().release();
    assertThatThrownBy(r::confirm).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void confirmationRequiresExplicitCompensation() {
    var r = fresh().confirm();
    assertThatThrownBy(r::release).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void originalIsImmutable() {
    var r = fresh();
    r.release();
    assertThat(r.state()).isEqualTo(Reservation.State.RESERVED);
  }
}
