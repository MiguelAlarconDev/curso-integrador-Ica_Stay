package pe.edu.utp.icastay.gallery;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Uses real PostgreSQL, but migrates only randomly named, test-owned schemas. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhotoGalleryMigrationTest {
    private final String url = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5433/ica_stay");
    private final String user = System.getenv().getOrDefault("DB_USER", "ica_stay");
    private final String password = System.getenv().getOrDefault("DB_PASSWORD", "");
    private final String schema = "photo_test_" + UUID.randomUUID().toString().replace("-", "");
    private final UUID hotel = UUID.randomUUID(), room = UUID.randomUUID();
    private final UUID otherHotel = UUID.randomUUID(), otherRoom = UUID.randomUUID();
    private Connection connection;
    private int upgradeCount;

    private Flyway flyway(String name, String target) {
        return Flyway.configure().dataSource(url, user, password)
                .schemas(name).defaultSchema(name).locations("classpath:db/migration")
                .target(target).load();
    }

    private Connection connect(String name) throws SQLException {
        Connection result = DriverManager.getConnection(url, user, password);
        try (Statement statement = result.createStatement()) {
            statement.execute("SET search_path TO " + checkedSchema(name));
        }
        return result;
    }

    private String checkedSchema(String name) {
        if (!name.matches("photo_test_[a-f0-9]{32}")) throw new IllegalArgumentException("Not a test schema");
        return name;
    }

    @BeforeAll void migrateExistingV2() throws Exception {
        flyway(schema, "2").migrate();
        try (Connection fixture = connect(schema)) {
            seed(fixture, hotel, room);
            seed(fixture, otherHotel, otherRoom);
        }
        upgradeCount = flyway(schema, "3").migrate().migrationsExecuted;
    }

    private void seed(Connection db, UUID hotelId, UUID roomId) throws SQLException {
        UUID admin = UUID.randomUUID();
        execute(db, "INSERT INTO users(id,name,email,password_hash,role) VALUES (?, 'Fixture', ?, 'unused', 'HOTEL_ADMIN')", admin, admin + "@example.test");
        execute(db, "INSERT INTO hotels(id,admin_user_id,name,address,city) VALUES (?, ?, 'Fixture', 'Fixture', 'Ica')", hotelId, admin);
        execute(db, "INSERT INTO rooms(id,hotel_id,number,capacity,price_per_night) VALUES (?, ?, '101', 2, 100)", roomId, hotelId);
    }

    @BeforeEach void begin() throws SQLException {
        connection = connect(schema);
        connection.setAutoCommit(false);
    }

    @AfterEach void rollback() throws SQLException {
        if (connection != null) {
            try { connection.rollback(); } finally { connection.close(); }
        }
    }

    @AfterAll void cleanup() throws SQLException { drop(schema); }

    private void drop(String name) throws SQLException {
        // Never clean public or a user-supplied schema, even if setup fails.
        try (Connection db = DriverManager.getConnection(url, user, password);
             Statement statement = db.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + checkedSchema(name) + " CASCADE");
        }
    }

    private void execute(Connection db, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    private UUID add(String table, String ownerColumn, UUID owner, int position, boolean cover) throws SQLException {
        UUID id = UUID.randomUUID();
        execute(connection, "INSERT INTO " + table + " (id," + ownerColumn
                + ",storage_key,mime_type,size_bytes,width,height,position,is_cover) VALUES (?,?,?,'image/jpeg',5242880,1200,800,?,?)",
                id, owner, table + "/" + id, position, cover);
        return id;
    }

    private UUID owner(String table) { return table.equals("hotel_photos") ? hotel : room; }
    private UUID otherOwner(String table) { return table.equals("hotel_photos") ? otherHotel : otherRoom; }

    private void rejects(String state, String sql, Object... values) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            SQLException error = assertThrows(SQLException.class, () -> execute(connection, sql, values));
            assertEquals(state, error.getSQLState());
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
    }

    private long count(String table) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT count(*) FROM " + table)) {
            result.next(); return result.getLong(1);
        }
    }

    @Test void upgradesV2WithoutChangingOwnersAndIsRepeatable() throws SQLException {
        assertEquals(1, upgradeCount);
        assertEquals(2, count("hotels")); assertEquals(2, count("rooms"));
        assertEquals(0, count("hotel_photos")); assertEquals(0, count("room_photos"));
        Flyway migration = flyway(schema, "3");
        migration.validate();
        assertEquals(0, migration.migrate().migrationsExecuted);
    }

    @Test void migratesAnEmptySchema() throws SQLException {
        String fresh = "photo_test_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway migration = flyway(fresh, "3");
            assertEquals(3, migration.migrate().migrationsExecuted);
            migration.validate();
            try (Connection db = connect(fresh); Statement statement = db.createStatement();
                 ResultSet result = statement.executeQuery("SELECT count(*) FROM hotel_photos CROSS JOIN room_photos")) {
                result.next(); assertEquals(0, result.getInt(1));
            }
        } finally { drop(fresh); }
    }

    @ParameterizedTest
    @CsvSource({"hotel_photos,hotel_id,15", "room_photos,room_id,10"})
    void enforcesCapacityAndPositionUniqueness(String table, String column, int limit) throws SQLException {
        UUID first = null;
        for (int i = 0; i < limit; i++) {
            UUID id = add(table, column, owner(table), i, false);
            if (i == 0) first = id;
        }
        assertEquals(limit, count(table));
        rejects("23514", "UPDATE " + table + " SET position=? WHERE id=?", limit, first);
        rejects("23514", "UPDATE " + table + " SET position=-1 WHERE id=?", first);
        rejects("23505", "UPDATE " + table + " SET position=1 WHERE id=?", first);
        rejects("23505", "INSERT INTO " + table + " SELECT ?, " + column
                + ", ?, mime_type,size_bytes,width,height,alt_text,position,is_cover,created_at FROM " + table + " WHERE id=?",
                UUID.randomUUID(), "extra", first);
        add(table, column, otherOwner(table), 0, false);
        assertEquals(limit + 1, count(table));
    }

    @ParameterizedTest
    @CsvSource({"hotel_photos,hotel_id", "room_photos,room_id"})
    void validatesMetadataDefaultsAndForeignKeys(String table, String column) throws SQLException {
        UUID id = add(table, column, owner(table), 0, false);
        for (String assignment : new String[]{"size_bytes=0", "size_bytes=-1", "size_bytes=5242881",
                "width=0", "height=-1", "mime_type='image/gif'", "mime_type='image/jpg'",
                "storage_key=''", "storage_key='   '", "storage_key=' trailing '"}) {
            rejects("23514", "UPDATE " + table + " SET " + assignment + " WHERE id=?", id);
        }
        for (String field : new String[]{"id", column, "storage_key", "mime_type", "size_bytes", "width", "height",
                "alt_text", "position", "is_cover", "created_at"}) {
            rejects("23502", "UPDATE " + table + " SET " + field + "=NULL WHERE id=?", id);
        }
        rejects("23503", "UPDATE " + table + " SET " + column + "=? WHERE id=?", UUID.randomUUID(), id);
        rejects("22001", "UPDATE " + table + " SET alt_text=? WHERE id=?", "a".repeat(501), id);
        rejects("22001", "UPDATE " + table + " SET storage_key=? WHERE id=?", "a".repeat(513), id);
        for (String mime : new String[]{"image/jpeg", "image/png", "image/webp"}) {
            execute(connection, "UPDATE " + table + " SET mime_type=?, size_bytes=1 WHERE id=?", mime, id);
        }
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT alt_text,is_cover,created_at FROM " + table)) {
            result.next(); assertEquals("", result.getString(1)); assertFalse(result.getBoolean(2)); assertNotNull(result.getTimestamp(3));
        }
        UUID second = add(table, column, owner(table), 1, false);
        rejects("23505", "UPDATE " + table + " SET storage_key=(SELECT storage_key FROM " + table + " WHERE id=?) WHERE id=?", id, second);
        rejects("23505", "UPDATE " + table + " SET id=? WHERE id=?", id, second);
    }

    @ParameterizedTest
    @CsvSource({"hotel_photos,hotel_id", "room_photos,room_id"})
    void allowsOnlyOneCoverPerOwnerAndAtomicReplacement(String table, String column) throws SQLException {
        UUID first = add(table, column, owner(table), 0, true);
        UUID second = add(table, column, owner(table), 1, false);
        add(table, column, otherOwner(table), 0, true);
        rejects("23505", "UPDATE " + table + " SET is_cover=true WHERE id=?", second);
        execute(connection, "UPDATE " + table + " SET is_cover=false WHERE id=?", first);
        execute(connection, "UPDATE " + table + " SET is_cover=true WHERE id=?", second);
        execute(connection, "DELETE FROM " + table + " WHERE id=?", second);
        // No trigger promotes a cover: that is the future service's responsibility.
        try (PreparedStatement statement = connection.prepareStatement("SELECT count(*) FROM " + table + " WHERE " + column + "=? AND is_cover")) {
            statement.setObject(1, owner(table));
            try (ResultSet result = statement.executeQuery()) { result.next(); assertEquals(0, result.getInt(1)); }
        }
    }

    @ParameterizedTest
    @CsvSource({"hotel_photos,hotel_id", "room_photos,room_id"})
    void supportsDeferredReorderButRejectsFinalDuplicates(String table, String column) throws SQLException {
        UUID first = add(table, column, owner(table), 0, false);
        UUID second = add(table, column, owner(table), 1, false);
        String constraint = "uq_" + table + "_position";
        execute(connection, "SET CONSTRAINTS " + constraint + " DEFERRED");
        execute(connection, "UPDATE " + table + " SET position=1 WHERE id=?", first);
        execute(connection, "UPDATE " + table + " SET position=0 WHERE id=?", second);
        execute(connection, "SET CONSTRAINTS " + constraint + " IMMEDIATE");
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT id FROM " + table + " ORDER BY position ASC,id ASC")) {
            result.next(); assertEquals(second, result.getObject(1, UUID.class));
            result.next(); assertEquals(first, result.getObject(1, UUID.class));
        }
        Savepoint valid = connection.setSavepoint();
        execute(connection, "SET CONSTRAINTS " + constraint + " DEFERRED");
        execute(connection, "UPDATE " + table + " SET position=0 WHERE id=?", first);
        SQLException error = assertThrows(SQLException.class, () -> execute(connection, "SET CONSTRAINTS " + constraint + " IMMEDIATE"));
        assertEquals("23505", error.getSQLState());
        connection.rollback(valid);
    }
}
