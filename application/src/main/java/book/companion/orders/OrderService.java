package book.companion.orders;

import book.companion.domain.Money;
import book.companion.inventory.Product;
import book.companion.inventory.ProductRepository;
import book.companion.inventory.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
  private final JdbcTemplate db;
  private final ProductRepository products;
  private final ObjectMapper json;

  public OrderService(JdbcTemplate db, ProductRepository products, ObjectMapper json) {
    this.db = db;
    this.products = products;
    this.json = json;
  }

  public record OrderView(UUID id, UUID productId, int quantity, BigDecimal total) {}

  public record OrderEvent(UUID eventId, UUID orderId, BigDecimal total) {}

  @Transactional
  @PreAuthorize("hasAuthority('SCOPE_products:write')")
  public OrderView place(String owner, String requestKey, UUID productId, int quantity) {
    if (quantity <= 0 || requestKey == null || requestKey.isBlank() || requestKey.length() > 128)
      throw new IllegalArgumentException("Invalid order input");
    // Serialize only this owner's request key. Hash collisions reduce throughput, not correctness.
    db.queryForObject(
        "select pg_advisory_xact_lock(hashtextextended(?,0))",
        Object.class,
        owner + ":" + requestKey);
    var existing =
        db.query(
            "select * from orders where owner=? and request_key=?",
            (rs, n) ->
                new OrderView(
                    rs.getObject("id", UUID.class),
                    rs.getObject("product_id", UUID.class),
                    rs.getInt("quantity"),
                    rs.getBigDecimal("total")),
            owner,
            requestKey);
    if (!existing.isEmpty()) {
      var order = existing.getFirst();
      if (!order.productId().equals(productId) || order.quantity() != quantity)
        throw new ProductService.Conflict("Idempotency key reused with different input");
      return order;
    }
    Product p =
        products.findByIdAndOwner(productId, owner).orElseThrow(ProductService.NotFound::new);
    BigDecimal total = new Money(p.price()).times(quantity).amount();
    if (total.precision() > 19)
      throw new ProductService.Conflict("Order total exceeds supported amount");
    if (products.reserveStock(productId, owner, quantity) != 1)
      throw new ProductService.Conflict("Insufficient stock");
    var order = new OrderView(UUID.randomUUID(), productId, quantity, total);
    db.update(
        "insert into orders(id,owner,request_key,product_id,quantity,total) values(?,?,?,?,?,?)",
        order.id(),
        owner,
        requestKey,
        productId,
        quantity,
        total);
    UUID eventId = UUID.randomUUID();
    try {
      db.update(
          "insert into outbox(id,aggregate_id,payload) values(?,?,?)",
          eventId,
          order.id(),
          json.writeValueAsString(new OrderEvent(eventId, order.id(), total)));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize order event", e);
    }
    return order;
  }

  @Transactional(readOnly = true)
  public OrderView get(String owner, UUID id) {
    return db
        .query(
            "select * from orders where owner=? and id=?",
            (rs, n) ->
                new OrderView(
                    rs.getObject("id", UUID.class),
                    rs.getObject("product_id", UUID.class),
                    rs.getInt("quantity"),
                    rs.getBigDecimal("total")),
            owner,
            id)
        .stream()
        .findFirst()
        .orElseThrow(ProductService.NotFound::new);
  }
}
