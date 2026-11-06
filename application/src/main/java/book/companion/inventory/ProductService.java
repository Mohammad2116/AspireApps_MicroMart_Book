package book.companion.inventory;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
  private final ProductRepository products;

  public ProductService(ProductRepository products) {
    this.products = products;
  }

  public record View(UUID id, String name, BigDecimal price, int stock, long version) {
    static View of(Product p) {
      return new View(p.id, p.name, p.price, p.stock, p.version);
    }
  }

  @Transactional
  public View create(String owner, String name, BigDecimal price, int stock) {
    return View.of(products.save(new Product(owner, name, price, stock)));
  }

  @Transactional(readOnly = true)
  public View get(String owner, UUID id) {
    return View.of(products.findByIdAndOwner(id, owner).orElseThrow(NotFound::new));
  }

  @Transactional(readOnly = true)
  public List<View> list(String owner, int page, int size) {
    return products
        .findByOwner(
            owner, PageRequest.of(page, size, Sort.by("name").ascending().and(Sort.by("id"))))
        .map(View::of)
        .getContent();
  }

  public static class NotFound extends RuntimeException {}

  public static class Conflict extends RuntimeException {
    public Conflict(String message) {
      super(message);
    }
  }
}
