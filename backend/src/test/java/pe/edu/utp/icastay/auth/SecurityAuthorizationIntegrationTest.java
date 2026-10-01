package pe.edu.utp.icastay.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class SecurityAuthorizationIntegrationTest {

    private MockMvc mvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private JwtTokenService jwtTokenService;

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
        when(user.getId()).thenReturn(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        when(user.getEmail()).thenReturn("user@example.com");
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
