package pe.edu.utp.icastay.user;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.utp.icastay.auth.*;
@SpringBootTest(properties={"icastay.security.jwt.secret=12345678901234567890123456789012", "logging.level.root=WARN", "logging.level.icastay.audit.user-status=INFO"})
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@Transactional
class UserStatusIntegrationTest {
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService users;
    @Autowired JwtTokenService tokens;
    @Autowired AuthService auth;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    MockMvc mvc;
    @BeforeEach void setup() { mvc=MockMvcBuilders.webAppContextSetup(context).addFilters(security).build(); }
    UUID account(String role) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into users(id,name,email,password_hash,role) values(?,'Status Test',?,?,?)",id,id+"@example.test",encoder.encode("TestPassword123!"),role);
        return id;
    }
    AuthenticatedUser actor(UUID id) { var u=users.access(id).orElseThrow();return new AuthenticatedUser(id,u.email(),u.role(),u.tokenVersion()); }
    String token(UUID id) { var u=users.access(id).orElseThrow();var mock=mock(UserEntity.class);
        when(mock.getId()).thenReturn(id); when(mock.getEmail()).thenReturn(u.email());when(mock.getRole()).thenReturn(u.role());when(mock.getTokenVersion()).thenReturn(u.tokenVersion());return tokens.createToken(mock); }
    String path(UUID id) { return "/api/v1/superadmin/users/"+id+"/status"; }
    void patchStatus(UUID target, String token, String status) throws Exception {
        mvc.perform(patch(path(target)).header("Authorization","Bearer "+token).contentType("application/json").content("{\"status\":\""+status+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(status)).andExpect(jsonPath("$.tokenVersion").doesNotExist());
    }
    @Test void changesAreIdempotentAndPreserveUserHotelAndReservations() throws Exception {
        UUID admin=account("SUPER_ADMIN"), owner=account("HOTEL_ADMIN"), guest=account("USER"), hotel=UUID.randomUUID(),room=UUID.randomUUID(),reservation=UUID.randomUUID();
        jdbc.update("insert into hotels(id,admin_user_id,name,address,city) values(?,?,'Test','Test','Ica')",hotel,owner);
        jdbc.update("insert into rooms(id,hotel_id,number,capacity,price_per_night) values(?,?,'1',2,100)",room,hotel);
        jdbc.update("insert into reservations(id,guest_user_id,room_id,check_in,check_out,guests,status,nightly_price_snapshot,total_amount,currency) values(?,?,?,CURRENT_DATE+1,CURRENT_DATE+2,1,'CONFIRMED',100,100,'PEN')",reservation,guest,room);
        var hotelBefore=jdbc.queryForMap("select * from hotels where id=?",hotel);
        var bookingBefore=jdbc.queryForMap("select * from reservations where id=?",reservation);
        for(UUID id:List.of(owner,guest)) {
            var before=jdbc.queryForMap("select name,email,password_hash,role,created_at from users where id=?",id);
            patchStatus(id,token(admin),"INACTIVE");patchStatus(id,token(admin),"INACTIVE");
            assertEquals(1,users.access(id).orElseThrow().tokenVersion());
            patchStatus(id,token(admin),"ACTIVE");assertEquals(2,users.access(id).orElseThrow().tokenVersion());
            assertEquals(before,jdbc.queryForMap("select name,email,password_hash,role,created_at from users where id=?",id));
        }
        assertEquals(hotelBefore,jdbc.queryForMap("select * from hotels where id=?",hotel));
        assertEquals(bookingBefore,jdbc.queryForMap("select * from reservations where id=?",reservation));
    }
    @Test void inactiveLoginAndOldTokensStayRejectedAfterReactivation() throws Exception {
        UUID admin=account("SUPER_ADMIN");
        for(String role:List.of("USER","HOTEL_ADMIN","SUPER_ADMIN")) {
            UUID target=account(role);String old=auth.login(new LoginRequest(target+"@example.test","TestPassword123!")).token();
            // A protected URL without a controller yields 404 only after successful authentication.
            mvc.perform(get("/protected-status-test").header("Authorization","Bearer "+old)).andExpect(status().isNotFound());
            patchStatus(target,token(admin),"INACTIVE");
            mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"email\":\""+target+"@example.test\",\"password\":\"TestPassword123!\"}")).andExpect(status().isUnauthorized());
            mvc.perform(get("/protected-status-test").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());
            patchStatus(target,token(admin),"ACTIVE");
            mvc.perform(get("/protected-status-test").header("Authorization","Bearer "+old)).andExpect(status().isUnauthorized());
            String fresh=auth.login(new LoginRequest(target+"@example.test","TestPassword123!")).token();
            mvc.perform(get("/protected-status-test").header("Authorization","Bearer "+fresh)).andExpect(status().isNotFound());
        }
    }
    @Test void legacyJwtRemainsValidOnlyAtVersionZero() throws Exception {
        UUID target=account("USER"),admin=account("SUPER_ADMIN");
        String current=token(target);String[] parts=current.split("\\.");
        String payload=new String(Base64.getUrlDecoder().decode(parts[1]),java.nio.charset.StandardCharsets.UTF_8).replace("\"ver\":0,","");
        String unsigned=parts[0]+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec("12345678901234567890123456789012".getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256"));
        String legacy=unsigned+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(unsigned.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        mvc.perform(get("/protected-status-test").header("Authorization","Bearer "+legacy)).andExpect(status().isNotFound());
        patchStatus(target,token(admin),"INACTIVE");patchStatus(target,token(admin),"ACTIVE");
        mvc.perform(get("/protected-status-test").header("Authorization","Bearer "+legacy)).andExpect(status().isUnauthorized());
    }
    @Test void invalidRequestsMissingUserSelfAndPermissions() throws Exception {
        UUID admin=account("SUPER_ADMIN"),target=account("USER");String bearer="Bearer "+token(admin);
        for(String body:List.of("{}","null","{\"status\":null}","{\"status\":12}","{\"status\":\"OTHER\"}","{\"status\":\"INACTIVE\",\"role\":\"SUPER_ADMIN\"}")) {
            mvc.perform(patch(path(target)).header("Authorization",bearer).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/v1/superadmin/users/invalid/status").header("Authorization",bearer).contentType("application/json").content("{\"status\":\"ACTIVE\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch(path(UUID.randomUUID())).header("Authorization",bearer).contentType("application/json").content("{\"status\":\"ACTIVE\"}")).andExpect(status().isNotFound());
        mvc.perform(patch(path(admin)).header("Authorization",bearer).contentType("application/json").content("{\"status\":\"INACTIVE\"}")).andExpect(status().isBadRequest());
        assertEquals("ACTIVE",users.access(admin).orElseThrow().status());
        mvc.perform(patch(path(target)).contentType("application/json").content("{\"status\":\"ACTIVE\"}")).andExpect(status().isUnauthorized());
        for(String role:List.of("USER","HOTEL_ADMIN")) mvc.perform(patch(path(target)).header("Authorization","Bearer "+token(account(role))).contentType("application/json").content("{\"status\":\"ACTIVE\"}")).andExpect(status().isForbidden());
        assertEquals(0,users.access(target).orElseThrow().tokenVersion());
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentCrossDeactivationPreservesAnActiveSuperadminAndRechecksActor() throws Exception {
        UUID first=account("SUPER_ADMIN"),second=account("SUPER_ADMIN");var a=actor(first);var b=actor(second);
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(()->attempt(start,second,a));var two=pool.submit(()->attempt(start,first,b));start.countDown();
            var results=List.of(one.get(15,TimeUnit.SECONDS),two.get(15,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter(code->code==200).count());assertEquals(1,results.stream().filter(code->code==403).count());
            assertEquals(1,jdbc.queryForObject("select count(*) from users where id in (?,?) and status='ACTIVE'",Integer.class,first,second));
        } finally { pool.shutdownNow();pool.awaitTermination(5,TimeUnit.SECONDS);jdbc.update("delete from users where id in (?,?)",first,second); }
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void concurrentRepeatedTransitionIncrementsVersionOnce() throws Exception {
        UUID admin=account("SUPER_ADMIN"),target=account("USER");var actor=actor(admin);var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var one=pool.submit(()->attempt(start,target,actor));var two=pool.submit(()->attempt(start,target,actor));start.countDown();
            assertEquals(200,one.get(15,TimeUnit.SECONDS));assertEquals(200,two.get(15,TimeUnit.SECONDS));
            assertEquals(1,users.access(target).orElseThrow().tokenVersion());
        } finally {pool.shutdownNow();pool.awaitTermination(5,TimeUnit.SECONDS);jdbc.update("delete from users where id in (?,?)",admin,target);}
    }
    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void auditReportsCommitAndRollbackWithoutSecrets(org.springframework.boot.test.system.CapturedOutput output) {
        UUID admin=account("SUPER_ADMIN"), target=account("USER");
        try {
            users.changeStatus(target,new UserStatusRequest("INACTIVE"),actor(admin));
            String event="actor="+admin+" target="+target;
            assertTrue(output.getAll().contains(event+" result=CHANGED"));
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
                users.changeStatus(target,new UserStatusRequest("ACTIVE"),actor(admin)); tx.setRollbackOnly();
            });
            assertTrue(output.getAll().contains(event+" result=ROLLED_BACK"));
            assertEquals("INACTIVE",users.access(target).orElseThrow().status());
            assertEquals(1,users.access(target).orElseThrow().tokenVersion());
            var audit=output.getAll().lines().filter(line -> line.contains("icastay.audit.user-status")).toList();
            assertFalse(audit.isEmpty());
            for(String line:audit) { assertFalse(line.contains("@"));assertFalse(line.contains("TestPassword123!"));assertFalse(line.contains("Bearer"));assertFalse(line.contains("$2")); }
        } finally { jdbc.update("delete from users where id in (?,?)",admin,target); }
    }
    int attempt(CountDownLatch start,UUID target,AuthenticatedUser actor) throws Exception {
        start.await();try {users.changeStatus(target,new UserStatusRequest("INACTIVE"),actor);return 200;}
        catch(org.springframework.web.server.ResponseStatusException e){return e.getStatusCode().value();}
    }
}
