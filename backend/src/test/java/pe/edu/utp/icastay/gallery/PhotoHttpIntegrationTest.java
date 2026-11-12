package pe.edu.utp.icastay.gallery;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pe.edu.utp.icastay.auth.JwtTokenService;
import pe.edu.utp.icastay.user.UserEntity;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "icastay.security.jwt.secret=photo-isolated-test-key-12345678901234567890",
        "logging.level.org.mongodb.driver=OFF", "debug=false", "icastay.photos.cleanup-delay=3600000"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PhotoHttpIntegrationTest {
    private static final String SCHEMA = "photo_http_" + UUID.randomUUID().toString().replace("-", "");
    private static final String DB = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5433/ica_stay");
    private static final String USER = System.getenv().getOrDefault("DB_USER", "ica_stay");
    private static final String PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "");
    private static final Path ROOT;
    static {
        try { ROOT = Files.createTempDirectory("icastay-photo-http-"); }
        catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DB + (DB.contains("?") ? "&" : "?") + "currentSchema=" + SCHEMA);
        registry.add("spring.datasource.username", () -> USER);
        registry.add("spring.datasource.password", () -> PASSWORD);
        registry.add("spring.flyway.schemas", () -> SCHEMA);
        registry.add("spring.flyway.default-schema", () -> SCHEMA);
        registry.add("icastay.photos.root", ROOT::toString);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    @Autowired EntityManager entities;
    @Autowired PhotoDeletionCleanup deletionCleanup;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean LocalPhotoStorage photoStorage;
    @Value("${local.server.port}") int port;
    final HttpClient client = HttpClient.newHttpClient();
    final JsonMapper json = JsonMapper.builder().build();
    UUID admin, hotel, room, otherAdmin, otherHotel, otherRoom;
    String token;
    byte[] png;

    @BeforeEach void fixtures() throws Exception {
        admin = UUID.randomUUID(); hotel = UUID.randomUUID(); room = UUID.randomUUID();
        otherAdmin = UUID.randomUUID(); otherHotel = UUID.randomUUID(); otherRoom = UUID.randomUUID();
        seed(admin, hotel, room); seed(otherAdmin, otherHotel, otherRoom);
        token = token(admin); png = PhotoValidatorTest.image("png", 3, 2);
    }

    private void seed(UUID adminId, UUID hotelId, UUID roomId) {
        jdbc.update("INSERT INTO users(id,name,email,password_hash,role) VALUES (?,'Photo fixture',?,'unused','HOTEL_ADMIN')", adminId, adminId + "@example.test");
        jdbc.update("INSERT INTO hotels(id,admin_user_id,name,address,city) VALUES (?,?,'Photo fixture','Fixture','Ica')", hotelId, adminId);
        jdbc.update("INSERT INTO rooms(id,hotel_id,number,capacity,price_per_night) VALUES (?,?,'101',2,100)", roomId, hotelId);
    }
    private String token(UUID id) { return tokens.createToken(entities.find(UserEntity.class, id)); }

    @AfterEach void clear() throws Exception {
        jdbc.execute("DELETE FROM photo_file_deletions");
        jdbc.execute("DELETE FROM room_photos"); jdbc.execute("DELETE FROM hotel_photos");
        jdbc.execute("DELETE FROM rooms"); jdbc.execute("DELETE FROM hotels"); jdbc.execute("DELETE FROM users");
        try (var paths = Files.walk(ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) if (!path.equals(ROOT)) Files.delete(path);
        }
    }

    @AfterAll static void cleanup() throws Exception {
        if (!SCHEMA.matches("photo_http_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe schema");
        try (var connection = DriverManager.getConnection(DB, USER, PASSWORD); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
        }
        if (!ROOT.toAbsolutePath().normalize().startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize())
                || !ROOT.getFileName().toString().startsWith("icastay-photo-http-")) throw new IllegalStateException("Unsafe test directory");
        try (var paths = Files.walk(ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    private HttpResponse<byte[]> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private HttpResponse<byte[]> upload(String path, String bearer, byte[] bytes, String alt) throws Exception {
        String boundary = "photo-" + UUID.randomUUID();
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"../../untrusted.html\"\r\nContent-Type: text/html\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(bytes);
        body.write(("\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"altText\"\r\n\r\n" + alt + "\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary).POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
    private String adminPath(boolean rooms) { return "/api/v1/admin/hotel" + (rooms ? "/rooms/" + room : "") + "/photos"; }
    private String publicPath(boolean rooms) { return "/api/v1/hotels/" + hotel + (rooms ? "/rooms/" + room : "") + "/photos"; }
    private long files() throws Exception { try (var paths = Files.walk(ROOT)) { return paths.filter(Files::isRegularFile).count(); } }

    @Test void uploadsHotelAndRoomAndReadsPersistentBytesWithoutExposingStorageKey() throws Exception {
        for (boolean rooms : new boolean[]{false, true}) {
            var response = upload(adminPath(rooms), token, png, "Vista del patio");
            assertEquals(201, response.statusCode(), new String(response.body()));
            var photo = json.readTree(response.body());
            assertTrue(photo.get("isCover").asBoolean()); assertEquals(0, photo.get("position").asInt());
            assertEquals("image/png", photo.get("mimeType").asString()); assertEquals(3, photo.get("width").asInt());
            assertFalse(photo.has("storageKey")); assertEquals("Vista del patio", photo.get("altText").asString());
            String location = response.headers().firstValue("Location").orElseThrow();
            var content = get(location); assertEquals(200, content.statusCode()); assertArrayEquals(png, content.body());
            assertEquals("image/png", content.headers().firstValue("Content-Type").orElseThrow());
            assertEquals("no-store", content.headers().firstValue("Cache-Control").orElseThrow());
            assertEquals("nosniff", content.headers().firstValue("X-Content-Type-Options").orElseThrow());
            String key = jdbc.queryForObject("SELECT storage_key FROM " + (rooms ? "room_photos" : "hotel_photos"), String.class);
            assertArrayEquals(png, new LocalPhotoStorage(ROOT.toString()).read(key));
            assertEquals(1, json.readTree(get(publicPath(rooms)).body()).size());
        }
        assertEquals(2, files());
    }

    @Test void enforcesJwtRolesAndOwnerIsolation() throws Exception {
        for (boolean rooms : new boolean[]{false, true}) {
            assertEquals(401, upload(adminPath(rooms), null, png, "").statusCode());
            assertEquals(401, upload(adminPath(rooms), "invalid", png, "").statusCode());
            for (String role : new String[]{"USER", "SUPER_ADMIN"}) {
                jdbc.update("UPDATE users SET role=? WHERE id=?", role, admin);
                assertEquals(403, upload(adminPath(rooms), token(admin), png, "").statusCode());
            }
            jdbc.update("UPDATE users SET role='HOTEL_ADMIN' WHERE id=?", admin);
        }
        assertEquals(404, upload("/api/v1/admin/hotel/rooms/" + otherRoom + "/photos", token, png, "").statusCode());
        assertEquals(404, upload("/api/v1/admin/hotel/rooms/" + UUID.randomUUID() + "/photos", token, png, "").statusCode());
        assertEquals(0, files());
        var response = upload(adminPath(false) + "?hotelId=" + otherHotel + "&adminUserId=" + otherAdmin, token, png, "");
        assertEquals(201, response.statusCode());
        assertEquals(hotel, jdbc.queryForObject("SELECT hotel_id FROM hotel_photos", UUID.class));
    }

    @Test void hidesInactiveParentsAndMismatchedPhotoIds() throws Exception {
        String hotelUrl = upload(adminPath(false), token, png, "").headers().firstValue("Location").orElseThrow();
        String roomUrl = upload(adminPath(true), token, png, "").headers().firstValue("Location").orElseThrow();
        assertEquals(404, get(hotelUrl.replace(hotel.toString(), otherHotel.toString())).statusCode());
        assertEquals(404, get(roomUrl.replace(room.toString(), otherRoom.toString())).statusCode());
        assertEquals(404, get(publicPath(true).replace(hotel.toString(), otherHotel.toString())).statusCode());
        jdbc.update("UPDATE rooms SET status='INACTIVE' WHERE id=?", room);
        assertEquals(404, get(roomUrl).statusCode()); assertEquals(404, get(publicPath(true)).statusCode());
        jdbc.update("UPDATE hotels SET status='INACTIVE' WHERE id=?", hotel);
        assertEquals(404, get(hotelUrl).statusCode()); assertEquals(404, get(publicPath(false)).statusCode());
        assertEquals(201, upload(adminPath(false), token, png, "").statusCode());
        assertEquals(201, upload(adminPath(true), token, png, "").statusCode());
    }

    @Test void rejectsInvalidAndOversizedUploadsWithoutFiles() throws Exception {
        assertEquals(400, upload(adminPath(false), token, "fake".getBytes(), "").statusCode());
        assertEquals(400, upload(adminPath(false), token, png, "x".repeat(501)).statusCode());
        assertEquals(413, upload(adminPath(false), token, new byte[PhotoValidator.MAX_BYTES + 1], "").statusCode());
        assertEquals(0, files());
        assertEquals(201, upload(adminPath(false), token, PhotoValidatorTest.WEBP, "WebP real").statusCode());
        assertEquals("image/webp", json.readTree(get(publicPath(false)).body()).get(0).get("mimeType").asString());
    }

    @Test void fillsBothLimitsAndRejectsExtraWithoutLeakingFiles() throws Exception {
        for (boolean rooms : new boolean[]{false, true}) {
            int limit = rooms ? 10 : 15;
            for (int i = 0; i < limit; i++) assertEquals(201, upload(adminPath(rooms), token, png, "").statusCode());
            long before = files();
            assertEquals(409, upload(adminPath(rooms), token, png, "").statusCode());
            assertEquals(before, files());
            var list = json.readTree(get(publicPath(rooms)).body());
            assertEquals(limit, list.size());
            for (int i = 0; i < limit; i++) {
                assertEquals(i, list.get(i).get("position").asInt());
                assertEquals(i == 0, list.get(i).get("isCover").asBoolean());
            }
        }
    }

    @Test void serializesConcurrentFirstUploads() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) results.add(executor.submit(() -> { gate.await(); return upload(adminPath(false), token, png, "").statusCode(); }));
            gate.countDown();
            for (var result : results) assertEquals(201, result.get(20, TimeUnit.SECONDS));
        }
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM hotel_photos", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM hotel_photos WHERE is_cover", Integer.class));
        assertEquals(2, files());
    }

    @Test void cleansFilesWhenMetadataInsertFails() throws Exception {
        jdbc.execute("ALTER TABLE hotel_photos ADD CONSTRAINT forced_failure CHECK (alt_text <> 'force-failure')");
        try {
            assertEquals(500, upload(adminPath(false), token, png, "force-failure").statusCode());
            assertEquals(0, files());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM hotel_photos", Integer.class));
        } finally { jdbc.execute("ALTER TABLE hotel_photos DROP CONSTRAINT forced_failure"); }
    }

    @Test void concurrentUploadsRespectLastSlotAndRemoveLosingFile() throws Exception {
        for (int i = 0; i < 14; i++) assertEquals(201, upload(adminPath(false), token, png, "").statusCode());
        List<Integer> statuses = new ArrayList<>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) results.add(executor.submit(() -> { gate.await(); return upload(adminPath(false), token, png, "").statusCode(); }));
            gate.countDown();
            for (var result : results) statuses.add(result.get(20, TimeUnit.SECONDS));
        }
        statuses.sort(Integer::compareTo);
        assertEquals(List.of(201, 409), statuses);
        assertEquals(15, files());
        assertEquals(15, jdbc.queryForObject("SELECT count(*) FROM hotel_photos", Integer.class));
    }

    @Test void returnsEmptyGalleryAndNotFoundForMissingOwnerOrFile() throws Exception {
        assertEquals(0, json.readTree(get(publicPath(false)).body()).size());
        assertEquals(404, get("/api/v1/hotels/" + UUID.randomUUID() + "/photos").statusCode());
        String location = upload(adminPath(false), token, png, "").headers().firstValue("Location").orElseThrow();
        String key = jdbc.queryForObject("SELECT storage_key FROM hotel_photos", String.class);
        new LocalPhotoStorage(ROOT.toString()).delete(key);
        assertEquals(404, get(location).statusCode());
        jdbc.update("DELETE FROM rooms WHERE hotel_id=?", otherHotel);
        jdbc.update("DELETE FROM hotels WHERE id=?", otherHotel);
        assertEquals(404, upload(adminPath(false), token(otherAdmin), png, "").statusCode());
    }

    private HttpResponse<byte[]> mutate(String method, String path, String bearer, Object body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (body != null) request.header("Content-Type", "application/json");
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private String addPhoto(boolean rooms) throws Exception {
        var response = upload(adminPath(rooms), token, png, "");
        assertEquals(201, response.statusCode());
        return json.readTree(response.body()).get("id").asString();
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void reordersSelectsCoverAndDeletesWithConsecutivePositions(boolean rooms) throws Exception {
        String first = addPhoto(rooms), second = addPhoto(rooms), third = addPhoto(rooms);
        String base = adminPath(rooms);
        var ordered = mutate("PUT", base + "/order", token, Map.of("photoIds", List.of(third, first, second)));
        assertEquals(200, ordered.statusCode());
        var list = json.readTree(ordered.body());
        assertEquals(third, list.get(0).get("id").asString());
        assertTrue(list.get(1).get("isCover").asBoolean()); // Reordering alone does not choose a cover.
        assertEquals(200, mutate("PUT", base + "/" + second + "/cover", token, null).statusCode());
        assertEquals(200, mutate("PUT", base + "/" + second + "/cover", token, null).statusCode());
        assertEquals(204, mutate("DELETE", base + "/" + second, token, null).statusCode());
        assertEquals(404, get(publicPath(rooms) + "/" + second + "/content").statusCode());
        list = json.readTree(get(publicPath(rooms)).body());
        assertEquals(2, list.size()); assertTrue(list.get(0).get("isCover").asBoolean());
        assertEquals(third, list.get(0).get("id").asString());
        assertEquals(0, list.get(0).get("position").asInt()); assertEquals(1, list.get(1).get("position").asInt());
        assertEquals(2, files());
        assertEquals(204, mutate("DELETE", base + "/" + first, token, null).statusCode()); // Non-cover removal.
        assertEquals(204, mutate("DELETE", base + "/" + third, token, null).statusCode());
        assertEquals(0, files()); assertEquals(0, json.readTree(get(publicPath(rooms)).body()).size());
        assertEquals(404, mutate("DELETE", base + "/" + third, token, null).statusCode());
        String newFirst = addPhoto(rooms);
        list = json.readTree(get(publicPath(rooms)).body());
        assertEquals(newFirst, list.get(0).get("id").asString()); assertEquals(0, list.get(0).get("position").asInt());
        assertTrue(list.get(0).get("isCover").asBoolean());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void rejectsInvalidOrdersAndForeignIdsWithoutMutation(boolean rooms) throws Exception {
        String first = addPhoto(rooms), second = addPhoto(rooms), base = adminPath(rooms);
        byte[] before = get(publicPath(rooms)).body();
        assertEquals(400, mutate("PUT", base + "/order", token, Map.of("photoIds", List.of(first, first))).statusCode());
        assertEquals(400, mutate("PUT", base + "/order", token, Map.of()).statusCode());
        assertEquals(400, mutate("PUT", base + "/order", token, Map.of("photoIds", Arrays.asList(first, null))).statusCode());
        assertEquals(409, mutate("PUT", base + "/order", token, Map.of("photoIds", List.of(first))).statusCode());
        String foreign = UUID.randomUUID().toString();
        assertEquals(404, mutate("PUT", base + "/order", token, Map.of("photoIds", List.of(first, foreign))).statusCode());
        assertEquals(404, mutate("PUT", base + "/" + foreign + "/cover", token, null).statusCode());
        assertEquals(404, mutate("DELETE", base + "/" + foreign, token, null).statusCode());
        // Same photo ID and complete order, but authenticated as the other hotel administrator.
        String otherPath = rooms ? "/api/v1/admin/hotel/rooms/" + otherRoom + "/photos" : base;
        assertEquals(404, mutate("DELETE", otherPath + "/" + first, token(otherAdmin), null).statusCode());
        assertEquals(404, mutate("PUT", otherPath + "/" + first + "/cover", token(otherAdmin), null).statusCode());
        assertEquals(404, mutate("PUT", otherPath + "/order", token(otherAdmin), Map.of("photoIds", List.of(first, second))).statusCode());
        assertArrayEquals(before, get(publicPath(rooms)).body()); assertEquals(2, files());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void allMutationsRequireJwtAndHotelAdmin(boolean rooms) throws Exception {
        String id = addPhoto(rooms), base = adminPath(rooms);
        for (String bearer : new String[]{null, "invalid"}) {
            assertEquals(401, mutate("DELETE", base + "/" + id, bearer, null).statusCode());
            assertEquals(401, mutate("PUT", base + "/" + id + "/cover", bearer, null).statusCode());
            assertEquals(401, mutate("PUT", base + "/order", bearer, Map.of("photoIds", List.of(id))).statusCode());
        }
        for (String role : new String[]{"USER", "SUPER_ADMIN"}) {
            jdbc.update("UPDATE users SET role=? WHERE id=?", role, admin);
            String wrong = token(admin);
            assertEquals(403, mutate("DELETE", base + "/" + id, wrong, null).statusCode());
            assertEquals(403, mutate("PUT", base + "/" + id + "/cover", wrong, null).statusCode());
            assertEquals(403, mutate("PUT", base + "/order", wrong, Map.of("photoIds", List.of(id))).statusCode());
        }
        assertEquals(1, files());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void rollbackPreservesMetadataCoverOrderAndFiles(boolean rooms) throws Exception {
        String first = addPhoto(rooms), second = addPhoto(rooms), base = adminPath(rooms);
        String table = rooms ? "room_photos" : "hotel_photos";
        byte[] before = get(publicPath(rooms)).body();
        jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT force_cover_rollback CHECK (NOT is_cover OR id='" + first + "')");
        try {
            assertEquals(500, mutate("PUT", base + "/" + second + "/cover", token, null).statusCode());
            assertEquals(500, mutate("DELETE", base + "/" + first, token, null).statusCode());
            assertArrayEquals(before, get(publicPath(rooms)).body());
            assertEquals(2, files());
            assertEquals(200, get(publicPath(rooms) + "/" + first + "/content").statusCode());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM photo_file_deletions", Integer.class));
        } finally { jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT force_cover_rollback"); }
        jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT force_order_rollback CHECK (position <> 0 OR id='" + first + "')");
        try {
            assertEquals(500, mutate("PUT", base + "/order", token, Map.of("photoIds", List.of(second, first))).statusCode());
            assertArrayEquals(before, get(publicPath(rooms)).body()); assertEquals(2, files());
        } finally { jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT force_order_rollback"); }
    }

    @Test void deletionFailureIsDurableAndRetryableWithoutRestoringPublicMetadata() throws Exception {
        String id = addPhoto(false);
        String key = jdbc.queryForObject("SELECT storage_key FROM hotel_photos", String.class);
        org.mockito.Mockito.doThrow(new java.io.IOException("simulated disk failure")).when(photoStorage).delete(key);
        try {
            assertEquals(204, mutate("DELETE", adminPath(false) + "/" + id, token, null).statusCode());
            assertEquals(1, files());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM photo_file_deletions", Integer.class));
            assertEquals(404, get(publicPath(false) + "/" + id + "/content").statusCode());
        } finally { org.mockito.Mockito.doCallRealMethod().when(photoStorage).delete(key); }
        new PhotoDeletionCleanup(jdbc, new LocalPhotoStorage(ROOT.toString()), transactionManager).retryPending();
        assertEquals(0, files()); assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM photo_file_deletions", Integer.class));
        // If deletion succeeded before a worker crashed, replaying the intent is safe.
        jdbc.update("INSERT INTO photo_file_deletions(storage_key) VALUES (?)", key);
        deletionCleanup.retryPending();
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM photo_file_deletions", Integer.class));
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void concurrentDeleteAndCoverKeepOneCoverAndConsecutiveOrder(boolean rooms) throws Exception {
        String first = addPhoto(rooms), second = addPhoto(rooms), third = addPhoto(rooms), base = adminPath(rooms);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            var deleted = executor.submit(() -> { gate.await(); return mutate("DELETE", base + "/" + first, token, null).statusCode(); });
            var covered = executor.submit(() -> { gate.await(); return mutate("PUT", base + "/" + third + "/cover", token, null).statusCode(); });
            gate.countDown();
            assertEquals(204, deleted.get(20, TimeUnit.SECONDS)); assertEquals(200, covered.get(20, TimeUnit.SECONDS));
        }
        var list = json.readTree(get(publicPath(rooms)).body());
        assertEquals(2, list.size()); assertEquals(second, list.get(0).get("id").asString());
        assertEquals(0, list.get(0).get("position").asInt()); assertEquals(1, list.get(1).get("position").asInt());
        assertFalse(list.get(0).get("isCover").asBoolean()); assertTrue(list.get(1).get("isCover").asBoolean());
        assertEquals(2, files());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void concurrentReorderAndDeleteSerializeOrRejectStaleList(boolean rooms) throws Exception {
        String first = addPhoto(rooms), second = addPhoto(rooms), third = addPhoto(rooms), base = adminPath(rooms);
        int orderStatus;
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var gate = new CountDownLatch(1);
            var deleted = executor.submit(() -> { gate.await(); return mutate("DELETE", base + "/" + first, token, null).statusCode(); });
            var reordered = executor.submit(() -> { gate.await(); return mutate("PUT", base + "/order", token,
                    Map.of("photoIds", List.of(third, first, second))).statusCode(); });
            gate.countDown();
            assertEquals(204, deleted.get(20, TimeUnit.SECONDS)); orderStatus = reordered.get(20, TimeUnit.SECONDS);
            assertTrue(orderStatus == 200 || orderStatus == 404);
        }
        var list = json.readTree(get(publicPath(rooms)).body());
        assertEquals(2, list.size());
        assertEquals(orderStatus == 200 ? third : second, list.get(0).get("id").asString());
        assertTrue(list.get(0).get("isCover").asBoolean()); assertFalse(list.get(1).get("isCover").asBoolean());
        assertEquals(0, list.get(0).get("position").asInt()); assertEquals(1, list.get(1).get("position").asInt());
        assertEquals(2, files());
    }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void ownerReadsInactiveGalleryWhilePublicAndOtherAdminsCannot(boolean rooms) throws Exception {
        String first = addPhoto(rooms), second = addPhoto(rooms);
        jdbc.update("UPDATE hotels SET status='INACTIVE' WHERE id=?", hotel);
        if (rooms) jdbc.update("UPDATE rooms SET status='INACTIVE' WHERE id=?", room);
        String base = adminPath(rooms);
        assertEquals(404, get(publicPath(rooms)).statusCode());
        assertEquals(404, get(publicPath(rooms) + "/" + first + "/content").statusCode());

        var listed = mutate("GET", base, token, null);
        assertEquals(200, listed.statusCode());
        assertEquals("no-store", listed.headers().firstValue("Cache-Control").orElseThrow());
        var list = json.readTree(listed.body());
        assertEquals(2, list.size()); assertEquals(first, list.get(0).get("id").asString());
        assertTrue(list.get(0).get("isCover").asBoolean());
        String content = list.get(0).get("url").asString();
        assertEquals(base + "/" + first + "/content", content);
        assertFalse(new String(listed.body(), StandardCharsets.UTF_8).contains("storage"));
        var bytes = mutate("GET", content, token, null);
        assertEquals(200, bytes.statusCode()); assertArrayEquals(png, bytes.body());
        assertEquals("nosniff", bytes.headers().firstValue("X-Content-Type-Options").orElseThrow());

        // Mutations on the inactive gallery persist and are visible through the admin read.
        assertEquals(200, mutate("PUT", base + "/order", token, Map.of("photoIds", List.of(second, first))).statusCode());
        assertEquals(200, mutate("PUT", base + "/" + first + "/cover", token, null).statusCode());
        assertEquals(204, mutate("DELETE", base + "/" + second, token, null).statusCode());
        list = json.readTree(mutate("GET", base, token, null).body());
        assertEquals(1, list.size()); assertEquals(first, list.get(0).get("id").asString());
        assertEquals(0, list.get(0).get("position").asInt()); assertTrue(list.get(0).get("isCover").asBoolean());
        assertEquals(404, mutate("GET", base + "/" + second + "/content", token, null).statusCode());

        assertEquals(401, mutate("GET", base, null, null).statusCode());
        assertEquals(401, mutate("GET", content, "invalid", null).statusCode());
        String other = token(otherAdmin);
        assertEquals(0, json.readTree(mutate("GET", "/api/v1/admin/hotel/photos", other, null).body()).size());
        assertEquals(404, mutate("GET", "/api/v1/admin/hotel/photos/" + first + "/content", other, null).statusCode());
        if (rooms) {
            assertEquals(404, mutate("GET", base, other, null).statusCode());
            assertEquals(404, mutate("GET", content, other, null).statusCode());
            assertEquals(404, mutate("GET", "/api/v1/admin/hotel/rooms/" + otherRoom + "/photos", token, null).statusCode());
        }
        assertEquals(400, mutate("GET", base + "/not-a-uuid/content", token, null).statusCode());
        for (String role : new String[]{"USER", "SUPER_ADMIN"}) {
            jdbc.update("UPDATE users SET role=? WHERE id=?", role, admin);
            assertEquals(403, mutate("GET", base, token(admin), null).statusCode());
        }
    }
}
