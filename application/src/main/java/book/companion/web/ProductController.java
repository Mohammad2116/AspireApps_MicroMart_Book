package book.companion.web;

import book.companion.inventory.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/products", "/api/v1/products"})
@Validated
public class ProductController {
  private final ProductService service;

  public ProductController(ProductService service) {
    this.service = service;
  }

  public record Create(
      @NotBlank @Size(max = 120) String name,
      @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal price,
      @Min(0) int stock) {}

  @PostMapping
  public ResponseEntity<ProductService.View> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Create body) {
    var view =
        service.create(
            book.companion.security.OwnerScope.from(jwt), body.name(), body.price(), body.stock());
    return ResponseEntity.created(URI.create("/api/products/" + view.id())).body(view);
  }

  @GetMapping("/{id}")
  public ProductService.View get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return service.get(book.companion.security.OwnerScope.from(jwt), id);
  }

  @GetMapping
  public List<ProductService.View> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.list(book.companion.security.OwnerScope.from(jwt), page, size);
  }
}
