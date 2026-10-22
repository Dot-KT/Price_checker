package dot.kt.priceChecker.config.security;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GadgetMigrationTest {
    @Test void existingPhoneKeepsItsIdAndIdentityWhenMovedUnderGadgets() throws Exception {
        String url = "jdbc:h2:mem:gadget_migration_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        UUID id = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            var source = new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection, true);
            Flyway.configure().dataSource(source).target("6").load().migrate();
            try (var statement = connection.prepareStatement("INSERT INTO items (id,category,name,brand,brand_key,model,identity_key,created_at) VALUES (?,'PHONE','Galaxy S24','Samsung','samsung','S24','existing-identity',CURRENT_TIMESTAMP)")) {
                statement.setObject(1, id); statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("INSERT INTO phone_items (id,series) VALUES (?,'S')")) {
                statement.setObject(1, id); statement.executeUpdate();
            }
            Flyway.configure().dataSource(source).load().migrate();
            try (var statement = connection.createStatement();
                    var result = statement.executeQuery("SELECT i.id,i.category,i.identity_key,g.gadget_type,g.series FROM items i JOIN gadget_items g ON i.id=g.id")) {
                assertThat(result.next()).isTrue(); assertThat(result.getObject("id", UUID.class)).isEqualTo(id);
                assertThat(result.getString("category")).isEqualTo("GADGET"); assertThat(result.getString("identity_key")).isEqualTo("existing-identity");
                assertThat(result.getString("gadget_type")).isEqualTo("PHONE"); assertThat(result.getString("series")).isEqualTo("S");
            }
        }
    }
}
