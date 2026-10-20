package pe.edu.utp.icastay.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.utp.icastay.user.UserEntity;

@SpringBootTest(properties = {
        "icastay.security.jwt.secret=12345678901234567890123456789012",
        "icastay.cors.allowed-origin=http://localhost:4200"
})
@org.springframework.transaction.annotation.Transactional
class SecurityAuthorizationIntegrationTest {
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private MockMvc mvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private AuthUserRepository users;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void endpointPublicoHotelesEsAccesibleSinJwt() throws Exception {
        mvc.perform(get("/api/v1/hotels"))
                .andExpect(status().isOk());
    }

    @Test
    void registroEsPublicoYPermiteLoginPosterior() throws Exception {
        String email = "registro-" + UUID.randomUUID() + "@icastay.com";
        try {
            mvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": " Usuario de Prueba ",
                                      "email": "%s",
                                      "password": "ClaveSegura123!"
                                    }
                                    """.formatted(email.toUpperCase())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNotEmpty())
                    .andExpect(jsonPath("$.name").value("Usuario de Prueba"))
                    .andExpect(jsonPath("$.email").value(email))
                    .andExpect(jsonPath("$.role").value("USER"))
                    .andExpect(jsonPath("$.token").doesNotExist());

            mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "%s",
                                      "password": "ClaveSegura123!"
                                    }
                                    """.formatted(email)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"));
        } finally {
            users.findByEmailAndStatus(email, "ACTIVE").ifPresent(users::delete);
        }
    }

    @Test
    void crearReservaSinJwtDevuelve401() throws Exception {
        mvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crearReservaConUserPasaLaCapaDeAutorizacion() throws Exception {
        mvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearReservaConHotelAdminDevuelve403() throws Exception {
        mvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + tokenFor("HOTEL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearReservaConSuperAdminDevuelve403() throws Exception {
        mvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + tokenFor("SUPER_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void jwtInvalidoNoPermiteAccederAEndpointProtegido() throws Exception {
        mvc.perform(post("/api/v1/reservations")
                        .header("Authorization", "Bearer token-manipulado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private String tokenFor(String role) {
        UserEntity user = mock(UserEntity.class);
        var id = UUID.randomUUID();
        jdbc.update("insert into users(id,name,email,password_hash,role) values(?, 'Actor', ?, 'unused', ?)", id, id + "@example.test", role);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(id + "@example.test");
        when(user.getRole()).thenReturn(role);
        return jwtTokenService.createToken(user);
    }

    @Test
    void hotelesPermiteOrigenAngular() throws Exception {
        mvc.perform(get("/api/v1/hotels?page=0&size=20")
                        .header("Origin", "http://localhost:4200"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    @Test
    void preflightPermiteMetodosYHeadersSinJwt() throws Exception {
        for (String method : new String[] {"GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"}) {
            mvc.perform(options("/api/v1/reservations")
                            .header("Origin", "http://localhost:4200")
                            .header("Access-Control-Request-Method", method)
                            .header("Access-Control-Request-Headers", "Content-Type,Authorization,Accept"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                    .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS"))
                    .andExpect(header().string("Access-Control-Allow-Headers", "Content-Type, Authorization, Accept"));
        }
    }

    @Test
    void rechazaOtrosOrigenes() throws Exception {
        for (String origin : new String[] {"http://localhost:4201", "http://127.0.0.1:4200", "https://example.com"}) {
            mvc.perform(get("/api/v1/hotels").header("Origin", origin))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }

    @Test
    void rechazaHeaderNoPermitidoEnPreflight() throws Exception {
        mvc.perform(options("/api/v1/hotels")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "X-Custom-Header"))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsNoEliminaAutorizacionDeReservas() throws Exception {
        mvc.perform(post("/api/v1/reservations").header("Origin", "http://localhost:4200")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
        mvc.perform(post("/api/v1/reservations").header("Origin", "http://localhost:4200")
                        .header("Authorization", "Bearer " + tokenFor("HOTEL_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }
}
