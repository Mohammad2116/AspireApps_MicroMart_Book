package book.companion.inventory;

import book.companion.domain.Money;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {
  @Id UUID id;

  @Column(nullable = false)
  String owner;

  @Column(nullable = false, length = 120)
  String name;

  @Column(nullable = false, precision = 19, scale = 2)
  BigDecimal price;

  @Column(nullable = false)
  int stock;

  @Version long version;

  public java.math.BigDecimal price() {
    return price;
  }

  public void rename(String value) {
    name = value;
  }

  protected Product() {}

  Product(String owner, String name, BigDecimal price, int stock) {
    this.id = UUID.randomUUID();
    this.owner = owner;
    this.name = name;
    this.price = new Money(price).amount();
    this.stock = stock;
  }
}
