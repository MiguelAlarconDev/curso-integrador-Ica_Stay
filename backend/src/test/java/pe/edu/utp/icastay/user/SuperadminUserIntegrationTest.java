package pe.edu.utp.icastay.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.utp.icastay.auth.JwtTokenService;
import pe.edu.utp.icastay.user.UserEntity;

@SpringBootTest(properties = {
    "icastay.security.jwt.secret=12345678901234567890123456789012",
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "logging.level.org.hibernate.stat=OFF",
    "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@Transactional
class SuperadminUserIntegrationTest {
    @Autowired UserService users;
    @Autowired JdbcTemplate jdbc;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired JwtTokenService tokens;
    @Autowired EntityManagerFactory entityManagerFactory;
    MockMvc mvc;
    String prefix;

    @BeforeEach void setup() {
        prefix = "Global-" + UUID.randomUUID();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test void pagesAreStableAndUseProjectionWithoutNPlusOne() {
        List<SuperadminUserView> expected = new ArrayList<>();
        for (int i=0;i<23;i++) expected.add(user(prefix + (i==0 ? " B" : " A"), "USER", i%2==0 ? "ACTIVE" : "INACTIVE"));
        expected.sort(Comparator.comparing(SuperadminUserView::name).thenComparing(u -> u.id().toString()));
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics(); stats.clear();
        var first = users.listGlobal(0,prefix,null,null);
        assertEquals(2,stats.getPrepareStatementCount()); assertEquals(0,stats.getEntityLoadCount());
        assertEquals(expected.subList(0,20),first.content()); assertEquals(20,first.size());
        assertEquals(23,first.totalElements()); assertEquals(2,first.totalPages());
        assertEquals(expected.subList(20,23),users.listGlobal(1,prefix,null,null).content());
        assertEquals(first,users.listGlobal(0,prefix,null,null));
        assertTrue(users.listGlobal(4,prefix,null,null).content().isEmpty());
    }
    @Test void searchesNameOrEmailAndCombinesEveryRoleAndState() {
        for (String role: List.of("USER","HOTEL_ADMIN","SUPER_ADMIN")) {
            for (String state: List.of("ACTIVE","INACTIVE")) {
                var row=user(prefix + " %_",role,state);
                assertEquals(List.of(row),users.listGlobal(0,"  " + prefix.toUpperCase() + " %_  ",role,state).content());
                assertEquals(List.of(row),users.listGlobal(0,row.email().substring(0,20).toUpperCase(),null,null).content());
            }
        }
        assertEquals(6,users.listGlobal(0,prefix,null,null).totalElements());
        assertEquals(jdbc.queryForObject("select count(*) from users",Long.class),users.listGlobal(0,"  ",null,null).totalElements());
    }
    @Test void httpContractExposesOnlySafeFieldsAndPages() throws Exception {
        var row=user(prefix,"SUPER_ADMIN","INACTIVE");
        mvc.perform(get("/api/v1/superadmin/users").header("Authorization",bearer("SUPER_ADMIN")).param("q",prefix)
            .param("role","SUPER_ADMIN").param("status","INACTIVE"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].length()").value(5))
            .andExpect(jsonPath("$.content[0].id").value(row.id().toString()))
            .andExpect(jsonPath("$.content[0].name").value(row.name()))
            .andExpect(jsonPath("$.content[0].email").value(row.email()))
            .andExpect(jsonPath("$.content[0].role").value(row.role()))
            .andExpect(jsonPath("$.content[0].status").value(row.status()))
            .andExpect(jsonPath("$.content[0].passwordHash").doesNotExist())
            .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(1));
    }
    @Test void invalidParametersReturn400() throws Exception {
        for (String page: List.of("-1","107374183","2147483648","abc")) {
            mvc.perform(get("/api/v1/superadmin/users").header("Authorization",bearer("SUPER_ADMIN")).param("page",page)).andExpect(status().isBadRequest());
        }
        for (String[] filter: new String[][]{{"q","x".repeat(255)},{"role","ADMIN"},{"role",""},{"status","DISABLED"},{"status",""}}) {
            mvc.perform(get("/api/v1/superadmin/users").header("Authorization",bearer("SUPER_ADMIN")).param(filter[0],filter[1])).andExpect(status().isBadRequest());
        }
    }
    @Test void requiresSuperadmin() throws Exception {
        mvc.perform(get("/api/v1/superadmin/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/superadmin/users").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        for (String role: List.of("USER","HOTEL_ADMIN")) {
            mvc.perform(get("/api/v1/superadmin/users").header("Authorization",bearer(role))).andExpect(status().isForbidden());
        }
    }
    @Test void detailIncludesAllRolesAndStatesWithoutSecrets() throws Exception {
        for (String role : List.of("USER", "HOTEL_ADMIN", "SUPER_ADMIN")) {
            for (String state : List.of("ACTIVE", "INACTIVE")) {
                var row = user(prefix, role, state);
                mvc.perform(get("/api/v1/superadmin/users/" + row.id()).header("Authorization", bearer("SUPER_ADMIN")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6))
                    .andExpect(jsonPath("$.id").value(row.id().toString()))
                    .andExpect(jsonPath("$.name").value(row.name())).andExpect(jsonPath("$.email").value(row.email()))
                    .andExpect(jsonPath("$.role").value(role)).andExpect(jsonPath("$.status").value(state))
                    .andExpect(jsonPath("$.hotel").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());
            }
        }
    }
    @Test void detailProjectsAssignedHotelInOneQueryIncludingInactiveHotel() throws Exception {
        var row = user(prefix, "HOTEL_ADMIN", "INACTIVE");
        UUID hotelId = UUID.randomUUID();
        jdbc.update("insert into hotels(id,admin_user_id,name,address,city,status) values(?,?,?,'Test','Ica','INACTIVE')", hotelId,row.id(),prefix);
        var stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics(); stats.clear();
        var detail = users.findGlobal(row.id());
        assertEquals(hotelId,detail.hotel().id()); assertEquals("INACTIVE",detail.hotel().status());
        assertEquals(1,stats.getPrepareStatementCount()); assertEquals(0,stats.getEntityLoadCount());
        mvc.perform(get("/api/v1/superadmin/users/" + row.id()).header("Authorization", bearer("SUPER_ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.hotel.length()").value(3))
            .andExpect(jsonPath("$.hotel.id").value(hotelId.toString()))
            .andExpect(jsonPath("$.hotel.name").value(prefix)).andExpect(jsonPath("$.hotel.status").value("INACTIVE"));
    }
    @Test void detailRejectsInvalidMissingAndUnauthorizedRequests() throws Exception {
        String path = "/api/v1/superadmin/users/" + UUID.randomUUID();
        mvc.perform(get(path).header("Authorization",bearer("SUPER_ADMIN"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/superadmin/users/not-a-uuid").header("Authorization",bearer("SUPER_ADMIN"))).andExpect(status().isBadRequest());
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for (String role : List.of("USER","HOTEL_ADMIN")) {
            mvc.perform(get(path).header("Authorization",bearer(role))).andExpect(status().isForbidden());
        }
    }

    private SuperadminUserView user(String name,String role,String status) {
        UUID id=UUID.randomUUID(); String email=id+"@example.test";
        jdbc.update("insert into users(id,name,email,password_hash,role,status) values(?,?,?,'never-expose-this',?,?)",id,name,email,role,status);
        return new SuperadminUserView(id,name,email,role,status);
    }

    private String bearer(String role) {
        var user = mock(UserEntity.class);
        var id = UUID.randomUUID();
        jdbc.update("insert into users(id,name,email,password_hash,role) values(?, 'Actor', ?, 'unused', ?)", id, id + "@example.test", role);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(id + "@example.test");
        when(user.getRole()).thenReturn(role);
        return "Bearer " + tokens.createToken(user);
    }
}
