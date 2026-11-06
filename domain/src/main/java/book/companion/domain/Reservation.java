package book.companion.domain;

import java.util.Objects;
import java.util.UUID;

/** Ch 6.11/6.14.2: immutable local transition model; persistence/CAS is a separate boundary. */
public record Reservation(UUID id, State state) {
  public enum State {
    RESERVED,
    CONFIRMED,
    RELEASED
  }

  public Reservation {
    Objects.requireNonNull(id);
    Objects.requireNonNull(state);
  }

  public Reservation confirm() {
    if (state == State.RELEASED)
      throw new IllegalStateException("Released reservation cannot be confirmed");
    return new Reservation(id, State.CONFIRMED);
  }

  public Reservation release() {
    if (state == State.CONFIRMED)
      throw new IllegalStateException("Confirmed reservation requires a separate compensation");
    return new Reservation(id, State.RELEASED);
  }
}
