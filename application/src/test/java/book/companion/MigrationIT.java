package book.companion;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.*;
import java.sql.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class MigrationIT {
  @Container
  static PostgreSQLContainer<?> pg =
      new PostgreSQLContainer<>(
          "postgres@sha256:57c72fd2a128e416c7fcc499958864df5301e940bca0a56f58fddf30ffc07777");

  @Test
  void upgradePreservesOldRowsAndIndexesServeScopedPagination() throws Exception {
    var config = Flyway.configure().dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
    config.target("1").load().migrate();
    try (var c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        var sql = c.createStatement()) {
      sql.execute(
          "insert into products values('00000000-0000-0000-0000-000000000001','alice','old',1,2,0)");
      sql.execute(
          "insert into outbox(id,aggregate_id,payload,published_at) values('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000001','{}',CURRENT_TIMESTAMP)");
      Flyway.configure()
          .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
          .load()
          .migrate();
      try (var r = sql.executeQuery("select owner,stock from products")) {
        r.next();
        assertThat(r.getString(1)).isEqualTo("legacy#alice");
        assertThat(r.getInt(2)).isEqualTo(2);
      }
      try (var r = sql.executeQuery("select status from outbox")) {
        r.next();
        assertThat(r.getString(1)).isEqualTo("PUBLISHED");
      }
      sql.execute(
          "insert into products(id,owner,name,price,stock,version) select md5(i::text)::uuid,'tenant-'||(i%100),'item-'||i,1,5,0 from generate_series(1,10000)i");
      sql.execute("analyze products");
      StringBuilder plan = new StringBuilder();
      try (var r =
          sql.executeQuery(
              "explain (analyze,buffers,format text) select id,name from products where owner='tenant-1' order by name,id limit 20")) {
        while (r.next()) plan.append(r.getString(1)).append('\n');
      }
      assertThat(plan.toString()).contains("products_owner_name_id");
      Files.writeString(Path.of("target/pagination-query-plan.txt"), plan);
      Flyway.configure()
          .dataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword())
          .load()
          .validate();
    }
  }
}
