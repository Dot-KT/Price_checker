package dot.kt.priceChecker.config.security;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExistingAccountMigrationTest {
    @Test
    void migratesExistingSellerIdentityWithoutLosingCredentialsOrInventory() throws Exception {
        String url = "jdbc:h2:mem:migration_" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Flyway.configure().dataSource(url, "sa", "").target("2").load().migrate();
        UUID sellerId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            try (var statement = connection.prepareStatement("INSERT INTO sellers (id, email, password_hash, display_name, default_currency, created_at) VALUES (?, 'old@example.com', 'existing-password-hash', 'Old shop', 'NGN', CURRENT_TIMESTAMP)")) {
                statement.setObject(1, sellerId);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("INSERT INTO inventory_items (id, seller_id, seller_sku, name, price_minor, currency, source_row, active, created_at, updated_at) VALUES (?, ?, 'PHONE', 'Phone', 10000, 'NGN', 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
                statement.setObject(1, itemId);
                statement.setObject(2, sellerId);
                statement.executeUpdate();
            }
        }
        Flyway.configure().dataSource(url, "sa", "").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT u.id, u.email, u.password_hash, u.role, u.enabled, i.id AS item_id FROM app_users u JOIN sellers s ON s.id = u.id JOIN inventory_items i ON i.seller_id = s.id")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getObject("id", UUID.class)).isEqualTo(sellerId);
            assertThat(result.getString("email")).isEqualTo("old@example.com");
            assertThat(result.getString("password_hash")).isEqualTo("existing-password-hash");
            assertThat(result.getString("role")).isEqualTo("SELLER");
            assertThat(result.getBoolean("enabled")).isTrue();
            assertThat(result.getObject("item_id", UUID.class)).isEqualTo(itemId);
        }
    }
}
