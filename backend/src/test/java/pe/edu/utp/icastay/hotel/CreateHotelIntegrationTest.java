package pe.edu.utp.icastay.hotel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.icastay.auth.JwtTokenService;
import pe.edu.utp.icastay.user.UserEntity;

@SpringBootTest(properties = {
    "icastay.security.jwt.secret=12345678901234567890123456789012",
    "logging.level.root=WARN",
    "logging.level.org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor=DEBUG",
    "logging.level.org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver=DEBUG",
    "logging.level.org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver=DEBUG",
    "logging.level.org.springframework.web.method.HandlerMethod=DEBUG"
})
@ExtendWith(OutputCaptureExtension.class)
class CreateHotelIntegrationTest {
    @Autowired CreateHotelService creation;
    @MockitoSpyBean HotelRepository hotels;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtTokenService tokens;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    MockMvc mvc;
    final List<UUID> actors = new ArrayList<>();
    String email;
    static final String PASSWORD = "HotelInitial123!";

    @BeforeEach void setup() {
        email = "create-hotel-" + UUID.randomUUID() + "@example.test";
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }
    @AfterEach void cleanup() {
        reset(hotels);
        for (UUID id : actors) jdbc.update("DELETE FROM users WHERE id=?", id);
        actors.clear();
        jdbc.update("DELETE FROM hotels WHERE admin_user_id IN (SELECT id FROM users WHERE email=?)", email);
        jdbc.update("DELETE FROM users WHERE email=?", email);
    }
    CreateHotelRequest request() {
        return new CreateHotelRequest(" Hotel Nuevo ", " Descripción ", " Calle 123 ", " Ica ",
                new CreateHotelRequest.Admin(" Administrador Nuevo ", " " + email.toUpperCase(Locale.ROOT) + " ", PASSWORD));
    }
    String body() {
        return """
            {"name":" Hotel Nuevo ","description":" Descripción ","address":" Calle 123 ","city":" Ica ",
             "role":"SUPER_ADMIN","status":"INACTIVE","adminUserId":"ignored",
             "admin":{"name":" Administrador Nuevo ","email":" %s ","password":"%s","role":"SUPER_ADMIN","status":"INACTIVE"}}
            """.formatted(email.toUpperCase(Locale.ROOT), PASSWORD);
    }
    String bearer(String role) {
        var user = mock(UserEntity.class);
        var id = UUID.randomUUID(); actors.add(id);
        jdbc.update("INSERT INTO users(id,name,email,password_hash,role) VALUES (?,'Auth test',?,'unused',?)", id, id + "@example.test", role);
        when(user.getId()).thenReturn(id); when(user.getEmail()).thenReturn(id + "@example.test");
        when(user.getRole()).thenReturn(role); return "Bearer " + tokens.createToken(user);
    }
    long users() { return jdbc.queryForObject("SELECT count(*) FROM users WHERE email=?", Long.class, email); }

    @Test void createsBothActiveAndBcryptWithSafe201AndNoRoleEscalation(CapturedOutput output) throws Exception {
        var response = mvc.perform(post("/api/v1/superadmin/hotels").header("Authorization", bearer("SUPER_ADMIN"))
                .contentType("application/json").content(body()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.length()").value(8))
            .andExpect(jsonPath("$.name").value("Hotel Nuevo"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.adminName").value("Administrador Nuevo"))
            .andExpect(jsonPath("$.adminEmail").value(email))
            .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains(PASSWORD)); assertFalse(output.getAll().contains(PASSWORD));
        var user = jdbc.queryForMap("SELECT id,role,status,password_hash FROM users WHERE email=?", email);
        assertEquals("HOTEL_ADMIN", user.get("role")); assertEquals("ACTIVE", user.get("status"));
        String hash = (String) user.get("password_hash");
        assertTrue(hash.startsWith("$2")); assertTrue(encoder.matches(PASSWORD, hash)); assertNotEquals(PASSWORD, hash);
        assertFalse(output.getAll().contains(hash));
        var hotel = jdbc.queryForMap("SELECT name,description,address,city,status FROM hotels WHERE admin_user_id=?", user.get("id"));
        assertEquals("Descripción", hotel.get("description")); assertEquals("Calle 123", hotel.get("address"));
        assertEquals("Ica", hotel.get("city")); assertEquals("ACTIVE", hotel.get("status"));
    }

    @Test void newAdminCanLoginAndAccessOnlyAssignedHotel() throws Exception {
        var hotel = creation.create(request());
        var response = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // The token is the only quoted field between token and tokenType in this existing DTO.
        String token = response.split("\"token\":\"")[1].split("\"")[0];
        mvc.perform(get("/api/v1/admin/hotel").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(hotel.id().toString()));
        mvc.perform(post("/api/v1/superadmin/hotels").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(body())).andExpect(status().isForbidden());
    }

    @Test void duplicateEmailAcrossRolesAndInactiveAccountsReturns409WithoutHotel() throws Exception {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,name,email,password_hash,role,status) VALUES (?,'Existing',?,'unused','USER','INACTIVE')", id, email);
        mvc.perform(post("/api/v1/superadmin/hotels").header("Authorization", bearer("SUPER_ADMIN"))
                .contentType("application/json").content(body())).andExpect(status().isConflict());
        assertEquals(1, users());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM hotels WHERE admin_user_id=?", Long.class, id));
    }

    @Test void invalidFieldsDoNotPersistAnything() {
        var good = request(); var admin = good.admin();
        List<CreateHotelRequest> invalid = new ArrayList<>();
        invalid.add(null);
        invalid.add(new CreateHotelRequest("Hotel", null, "Address", "Ica", null));
        for (String value : new String[]{null, " ", "x".repeat(181)}) invalid.add(new CreateHotelRequest(value, null, "Address", "Ica", admin));
        for (String value : new String[]{null, " ", "x".repeat(256)}) invalid.add(new CreateHotelRequest("Hotel", null, value, "Ica", admin));
        for (String value : new String[]{null, " ", "x".repeat(121)}) invalid.add(new CreateHotelRequest("Hotel", null, "Address", value, admin));
        invalid.add(new CreateHotelRequest("Hotel", "x".repeat(5001), "Address", "Ica", admin));
        for (String value : new String[]{null, " ", "x".repeat(161)}) invalid.add(withAdmin(new CreateHotelRequest.Admin(value, email, PASSWORD)));
        for (String value : new String[]{null, " ", "invalid", "a@b", "x".repeat(250) + "@a.test"}) invalid.add(withAdmin(new CreateHotelRequest.Admin("Admin", value, PASSWORD)));
        for (String value : new String[]{null, "short", "x".repeat(73), "é".repeat(37)}) invalid.add(withAdmin(new CreateHotelRequest.Admin("Admin", email, value)));
        for (var value : invalid) assertEquals(400, assertThrows(ResponseStatusException.class, () -> creation.create(value)).getStatusCode().value());
        assertEquals(0, users());
    }

    @Test void acceptsLimitsAndOptionalDescription() {
        var result = creation.create(new CreateHotelRequest("x".repeat(180), "  ", "x".repeat(255), "x".repeat(120),
                new CreateHotelRequest.Admin("x".repeat(160), email, "é".repeat(36))));
        assertEquals(180, result.name().length());
        assertNull(jdbc.queryForObject("SELECT description FROM hotels WHERE id=?", String.class, result.id()));
        assertTrue(encoder.matches("é".repeat(36), jdbc.queryForObject("SELECT password_hash FROM users WHERE email=?", String.class, email)));
    }

    @Test void malformedBodyIs400WithoutLoggingPassword(CapturedOutput output) throws Exception {
        for (String content : new String[]{"", "{}", "{\"admin\":{\"password\":\"" + PASSWORD + "\"},broken", "{\"admin\":[]}", "{\"admin\":\"" + PASSWORD + "\"}"}) {
            mvc.perform(post("/api/v1/superadmin/hotels").header("Authorization", bearer("SUPER_ADMIN"))
                    .contentType("application/json").content(content)).andExpect(status().isBadRequest());
        }
        assertEquals(0, users()); assertFalse(output.getAll().contains(PASSWORD));
        assertFalse(request().toString().contains(PASSWORD)); assertFalse(request().admin().toString().contains(PASSWORD));
    }

    @Test void deniesMissingInvalidAndOtherRoleTokens() throws Exception {
        mvc.perform(post("/api/v1/superadmin/hotels").contentType("application/json").content(body())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/superadmin/hotels").header("Authorization", "Bearer invalid").contentType("application/json").content(body())).andExpect(status().isUnauthorized());
        for (String role : new String[]{"USER", "HOTEL_ADMIN"}) mvc.perform(post("/api/v1/superadmin/hotels")
            .header("Authorization", bearer(role)).contentType("application/json").content(body())).andExpect(status().isForbidden());
        assertEquals(0, users());
    }

    @Test void assignmentUniquenessFailureRollsBackBothInserts() {
        doAnswer(invocation -> {
            HotelEntity hotel = invocation.getArgument(0);
            UUID adminId = (UUID) ReflectionTestUtils.getField(hotel, "adminUserId");
            // Force a real DB failure after the account and first hotel have been inserted.
            for (int i = 0; i < 2; i++) jdbc.update(
                "INSERT INTO hotels(id,admin_user_id,name,address,city) VALUES (?,?,'Rollback','Address','Ica')", UUID.randomUUID(), adminId);
            return hotel;
        }).when(hotels).saveAndFlush(any(HotelEntity.class));
        assertThrows(DataIntegrityViolationException.class, () -> creation.create(request()));
        assertEquals(0, users());
        // A non-rolled-back hotel would retain its account through the mandatory FK.
        verify(hotels).saveAndFlush(any(HotelEntity.class));
    }

    @Test void simultaneousDuplicateRequestsCreateExactlyOnePair() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Integer> attempt = () -> {
            start.await();
            try { creation.create(request()); return 201; }
            catch (ResponseStatusException error) { return error.getStatusCode().value(); }
        };
        try {
            var a = executor.submit(attempt); var b = executor.submit(attempt); start.countDown();
            assertEquals(Set.of(201, 409), Set.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)));
            assertEquals(1, users());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM hotels h JOIN users u ON u.id=h.admin_user_id WHERE u.email=?", Long.class, email));
        } finally { executor.shutdownNow(); executor.awaitTermination(10, TimeUnit.SECONDS); }
    }

    CreateHotelRequest withAdmin(CreateHotelRequest.Admin admin) { return new CreateHotelRequest("Hotel", null, "Address", "Ica", admin); }
}
