package book.companion.inventory;

import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {
  Optional<Product> findByIdAndOwner(UUID id, String owner);

  Page<Product> findByOwner(String owner, Pageable pageable);

  // Chapter 4.8: the predicate, not an expected-version comparison, prevents overselling.
  @Modifying(flushAutomatically = true, clearAutomatically = true)
  @Query(
      "update Product p set p.stock=p.stock-:quantity, p.version=p.version+1 where p.id=:id and"
          + " p.owner=:owner and p.stock>=:quantity")
  int reserveStock(
      @Param("id") UUID id, @Param("owner") String owner, @Param("quantity") int quantity);
}
