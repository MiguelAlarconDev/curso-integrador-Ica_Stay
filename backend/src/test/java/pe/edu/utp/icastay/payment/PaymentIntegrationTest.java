package pe.edu.utp.icastay.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.icastay.auth.JwtTokenService;
import pe.edu.utp.icastay.reservation.CreateReservationRequest;
import pe.edu.utp.icastay.reservation.ReservationService;
import pe.edu.utp.icastay.user.UserEntity;

@SpringBootTest(properties = "icastay.security.jwt.secret=12345678901234567890123456789012")
class PaymentIntegrationTest {
    @Autowired PaymentService payments;
    @Autowired ReservationService reservations;
    @MockitoSpyBean PaymentAttemptRepository attempts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired JwtTokenService tokens;
    MockMvc mvc;
    UUID guest, other, admin, hotel, room, reservation;
    Clock previousClock;
    final Instant now = Instant.parse("2030-01-01T12:00:00Z");

    @BeforeEach void setup() {
        Object target = AopTestUtils.getUltimateTargetObject(reservations);
        previousClock = (Clock) ReflectionTestUtils.getField(target, "clock");
        ReflectionTestUtils.setField(target, "clock", Clock.fixed(now, ZoneOffset.UTC));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        guest = user("USER"); other = user("USER"); admin = user("HOTEL_ADMIN");
        hotel = UUID.randomUUID(); room = UUID.randomUUID();
        jdbc.update("INSERT INTO hotels(id,admin_user_id,name,address,city) VALUES (?,?,'Payment test','Test','Ica')", hotel, admin);
        jdbc.update("INSERT INTO rooms(id,hotel_id,number,capacity,price_per_night) VALUES (?,?,'101',2,100)", room, hotel);
        reservation = reservations.create(guest, booking()).id();
    }

    @AfterEach void cleanup() {
        reset(attempts);
        Object target = AopTestUtils.getUltimateTargetObject(reservations);
        ReflectionTestUtils.setField(target, "clock", previousClock);
        jdbc.update("DELETE FROM payment_attempts WHERE reservation_id IN (SELECT id FROM reservations WHERE room_id=?)", room);
        jdbc.update("DELETE FROM reservations WHERE room_id=?", room);
        jdbc.update("DELETE FROM rooms WHERE id=?", room);
        jdbc.update("DELETE FROM hotels WHERE id=?", hotel);
        jdbc.update("DELETE FROM users WHERE id IN (?,?,?)", guest, other, admin);
    }

    UUID user(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,name,email,password_hash,role) VALUES (?,'Payment test',?,'unused',?)", id, id + "@example.test", role);
        return id;
    }
    String bearer(UUID id, String role) {
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(id); when(user.getEmail()).thenReturn(id + "@example.test"); when(user.getRole()).thenReturn(role);
        return "Bearer " + tokens.createToken(user);
    }
    CreateReservationRequest booking() {
        return new CreateReservationRequest(room, LocalDate.of(2030, 1, 10), LocalDate.of(2030, 1, 12), 2);
    }
    PaymentView pay(PaymentResult result) {
        return payments.simulate(guest, new SimulatePaymentRequest(reservation, result));
    }
    String state() { return jdbc.queryForObject("SELECT status FROM reservations WHERE id=?", String.class, reservation); }
    int count() { return jdbc.queryForObject("SELECT count(*) FROM payment_attempts WHERE reservation_id=?", Integer.class, reservation); }
    String body(UUID id, String result) { return "{\"reservationId\":\"" + id + "\",\"result\":\"" + result + "\"}"; }
    void conflict(Runnable action) {
        assertEquals(409, assertThrows(ResponseStatusException.class, action::run).getStatusCode().value());
    }

    @Test void approvedHttpConfirmsAndReturnsPersistedAttempt() throws Exception {
        mvc.perform(post("/api/v1/payments/simulate").header("Authorization", bearer(guest, "USER"))
                .contentType("application/json").content(body(reservation, "APPROVED")))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.result").value("APPROVED")).andExpect(jsonPath("$.reference").isNotEmpty());
        assertEquals("CONFIRMED", state()); assertEquals(1, count());
        var row = jdbc.queryForMap("SELECT * FROM payment_attempts WHERE reservation_id=?", reservation);
        assertEquals("APPROVED", row.get("status"));
        assertTrue(row.get("external_reference").toString().startsWith("sim-"));
        assertNotNull(row.get("created_at")); assertNotNull(row.get("updated_at"));
        assertNull(row.get("provider_payment_id"));
        assertEquals(Timestamp.from(now), jdbc.queryForObject("SELECT updated_at FROM reservations WHERE id=?", Timestamp.class, reservation));
        conflict(() -> pay(PaymentResult.APPROVED)); conflict(() -> pay(PaymentResult.REJECTED));
        assertEquals(1, count());
    }

    @Test void rejectedPreservesReservationAndAllowsRetryWithUniqueReference() {
        var before = jdbc.queryForMap("SELECT * FROM reservations WHERE id=?", reservation);
        PaymentView first = pay(PaymentResult.REJECTED);
        PaymentView second = pay(PaymentResult.REJECTED);
        assertEquals(PaymentResult.REJECTED, first.result());
        assertNotEquals(first.reference(), second.reference()); assertNotEquals(first.id(), second.id());
        assertEquals(before, jdbc.queryForMap("SELECT * FROM reservations WHERE id=?", reservation));
        assertEquals(2, count());
        pay(PaymentResult.APPROVED);
        assertEquals("CONFIRMED", state()); assertEquals(3, count());
    }

    @Test void exactExpiryPastExpiryAndTerminalStatesRejectWithoutAttempts() {
        for (long offset : new long[] {0, -1}) {
            jdbc.update("UPDATE reservations SET expires_at=? WHERE id=?", Timestamp.from(now.plusSeconds(offset)), reservation);
            for (PaymentResult result : PaymentResult.values()) conflict(() -> pay(result));
        }
        for (String status : List.of("CONFIRMED", "CANCELLED", "EXPIRED")) {
            jdbc.update("UPDATE reservations SET status=?,expires_at=? WHERE id=?", status, Timestamp.from(now.plusSeconds(900)), reservation);
            for (PaymentResult result : PaymentResult.values()) conflict(() -> pay(result));
        }
        assertEquals(0, count());
    }

    @Test void authorizationAndOwnership() throws Exception {
        String url = "/api/v1/payments/simulate";
        mvc.perform(post(url).contentType("application/json").content(body(reservation, "APPROVED"))).andExpect(status().isUnauthorized());
        mvc.perform(post(url).header("Authorization", "Bearer invalid.jwt").contentType("application/json")
                .content(body(reservation, "APPROVED"))).andExpect(status().isUnauthorized());
        for (String role : List.of("HOTEL_ADMIN", "SUPER_ADMIN")) {
            jdbc.update("UPDATE users SET role=? WHERE id=?", role, admin);
            mvc.perform(post(url).header("Authorization", bearer(admin, role)).contentType("application/json")
                    .content(body(reservation, "APPROVED"))).andExpect(status().isForbidden());
        }
        for (UUID id : List.of(reservation, UUID.randomUUID())) {
            mvc.perform(post(url).header("Authorization", bearer(other, "USER")).contentType("application/json")
                    .content(body(id, "APPROVED"))).andExpect(status().isNotFound());
        }
        assertEquals(0, count()); assertEquals("PENDING_PAYMENT", state());
    }

    @Test void invalidBodiesAndExtraFieldsAreRejected() throws Exception {
        List<String> bodies = new ArrayList<>(List.of("{}", "{\"reservationId\":\"" + reservation + "\"}",
                "{\"result\":\"APPROVED\"}", body(reservation, "UNKNOWN"), body(reservation, "approved"),
                "{\"reservationId\":\"bad\",\"result\":\"APPROVED\"}",
                "{\"reservationId\":\"" + reservation + "\",\"result\":0}",
                "{\"reservationId\":\"" + reservation + "\",\"result\":null}"));
        for (String field : List.of("amount", "guestUserId", "cardNumber")) {
            bodies.add(body(reservation, "APPROVED").replace("}", ",\"" + field + "\":\"unexpected\"}"));
        }
        for (String body : bodies) {
            mvc.perform(post("/api/v1/payments/simulate").header("Authorization", bearer(guest, "USER"))
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(0, count()); assertEquals("PENDING_PAYMENT", state());
    }

    @Test void persistenceFailureRollsBackAttemptAndConfirmation() {
        doAnswer(invocation -> {
            PaymentAttemptEntity saved = attempts.save(invocation.getArgument(0));
            attempts.flush();
            assertEquals("CONFIRMED", state()); assertEquals(1, count());
            // A real database constraint failure after both writes have been flushed.
            jdbc.update("INSERT INTO payment_attempts(id) VALUES (?)", UUID.randomUUID());
            return saved;
        }).when(attempts).saveAndFlush(any(PaymentAttemptEntity.class));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> pay(PaymentResult.APPROVED));
        assertEquals("PENDING_PAYMENT", state()); assertEquals(0, count());
    }

    @Test void simultaneousApprovalsProduceOneAttempt() throws Exception {
        List<Integer> results = race(() -> pay(PaymentResult.APPROVED), () -> pay(PaymentResult.APPROVED));
        assertEquals(List.of(201, 409), results.stream().sorted().toList());
        assertEquals("CONFIRMED", state()); assertEquals(1, count());
    }

    @Test void paymentRacingWithNewBookingCannotOverbook() throws Exception {
        List<Integer> results = race(() -> pay(PaymentResult.APPROVED), () -> reservations.create(other, booking()));
        assertEquals(List.of(201, 409), results);
        assertEquals("CONFIRMED", state()); assertEquals(1, count());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM reservations WHERE room_id=?", Integer.class, room));
    }

    @Test void expiredPaymentRacingWithNewBookingCannotConfirm() throws Exception {
        jdbc.update("UPDATE reservations SET expires_at=? WHERE id=?", Timestamp.from(now), reservation);
        List<Integer> results = race(() -> pay(PaymentResult.APPROVED), () -> reservations.create(other, booking()));
        assertEquals(List.of(409, 201), results);
        assertEquals("PENDING_PAYMENT", state()); assertEquals(0, count());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM reservations WHERE room_id=? AND expires_at>?", Integer.class, room, Timestamp.from(now)));
    }

    @Test void expiryIsReevaluatedAfterWaitingForRoomLock() throws Exception {
        List<Integer> results = race(() -> pay(PaymentResult.APPROVED), () -> pay(PaymentResult.REJECTED), () -> {
            Object target = AopTestUtils.getUltimateTargetObject(reservations);
            ReflectionTestUtils.setField(target, "clock", Clock.fixed(now.plusSeconds(900), ZoneOffset.UTC));
        });
        assertEquals(List.of(409, 409), results);
        assertEquals("PENDING_PAYMENT", state()); assertEquals(0, count());
    }

    @Test void conflictingReservationPreventsApproval() {
        jdbc.update("""
                INSERT INTO reservations(id,guest_user_id,room_id,check_in,check_out,guests,status,
                    nightly_price_snapshot,total_amount,currency)
                VALUES (?,?,?,'2030-01-10','2030-01-12',2,'CONFIRMED',100,200,'PEN')
                """, UUID.randomUUID(), other, room);
        conflict(() -> pay(PaymentResult.APPROVED));
        assertEquals("PENDING_PAYMENT", state()); assertEquals(0, count());
    }

    List<Integer> race(Supplier<?> first, Supplier<?> second) throws Exception {
        return race(first, second, () -> { });
    }

    List<Integer> race(Supplier<?> first, Supplier<?> second, Runnable beforeUnlock) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Integer>> futures = new ArrayList<>();
        CountDownLatch started = new CountDownLatch(2);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM rooms WHERE id=? FOR UPDATE", UUID.class, room);
                for (Supplier<?> action : List.of(first, second)) {
                    futures.add(executor.submit(() -> {
                        started.countDown();
                        try { action.get(); return 201; }
                        catch (ResponseStatusException error) { return error.getStatusCode().value(); }
                    }));
                }
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    for (Future<Integer> future : futures) {
                        assertThrows(TimeoutException.class, () -> future.get(250, TimeUnit.MILLISECONDS));
                    }
                    beforeUnlock.run();
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
            });
            return List.of(futures.get(0).get(10, TimeUnit.SECONDS), futures.get(1).get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(15, TimeUnit.SECONDS));
        }
    }
}
