package book.companion.web;

import book.companion.orders.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/orders", "/api/v1/orders"})
@Validated
public class OrderController {
  private final OrderService orders;

  public OrderController(OrderService orders) {
    this.orders = orders;
  }

  public record Request(@NotNull UUID productId, @Min(1) int quantity) {}

  @PostMapping
  public ResponseEntity<OrderService.OrderView> place(
      @AuthenticationPrincipal Jwt jwt,
      @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String key,
      @Valid @RequestBody Request request) {
    var order =
        orders.place(
            book.companion.security.OwnerScope.from(jwt),
            key,
            request.productId(),
            request.quantity());
    return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(order);
  }

  @GetMapping("/{id}")
  public OrderService.OrderView get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return orders.get(book.companion.security.OwnerScope.from(jwt), id);
  }
}
