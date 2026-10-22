package pe.edu.utp.icastay.ai;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import pe.edu.utp.icastay.auth.JwtTokenService;
import pe.edu.utp.icastay.auth.SecurityConfig;
import pe.edu.utp.icastay.user.UserService;

@SpringJUnitWebConfig(AiChatEndpointTest.Config.class)
@TestPropertySource(properties = "icastay.cors.allowed-origin=http://localhost:4200")
class AiChatEndpointTest {
    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, AiChatController.class, AiChatService.class, AiSessionService.class})
    static class Config {
        @Bean OllamaClient ollamaClient() { return mock(OllamaClient.class); }
        @Bean pe.edu.utp.icastay.ai.catalog.AiCatalogTool catalog() { return mock(pe.edu.utp.icastay.ai.catalog.AiCatalogTool.class); }
        @Bean JwtTokenService jwtTokenService() { return mock(JwtTokenService.class); }
        @Bean UserService userService() { return mock(UserService.class); }
        @Bean pe.edu.utp.icastay.ai.history.AiHistoryService history() { return mock(pe.edu.utp.icastay.ai.history.AiHistoryService.class); }
        @Bean AiProperties properties() { return OllamaClientTest.properties(); }
        @Bean tools.jackson.databind.ObjectMapper mapper() { return tools.jackson.databind.json.JsonMapper.builder().build(); }
    }

    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired OllamaClient ollama;
    @Autowired JwtTokenService tokens;
    @Autowired UserService users;
    @Autowired pe.edu.utp.icastay.ai.history.AiHistoryService history;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reset(ollama, tokens, users, history);
        when(history.findActive(anyString(), anyString(), anyInt(), anyInt())).thenReturn(
                new pe.edu.utp.icastay.ai.history.AiHistoryService.HistoryPage(true, java.util.List.of(), 0, 5, false));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void anonymousChatReturnsOnlyAnswer() throws Exception {
        when(ollama.chat("consulta hotelera")).thenReturn("No dispongo de datos verificados.");
        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"  consulta hotelera  \"}"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"answer\":\"No dispongo de datos verificados.\"}"))
                .andExpect(jsonPath("$.length()").value(1));
        verify(ollama).chat("consulta hotelera");
        verifyNoInteractions(tokens, users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "{}", "[]", "{", "{\"message\":null}",
            "{\"message\":\"\"}", "{\"message\":\"  \\n \\t\"}", "{\"message\":123}",
            "{\"message\":[]}", "{\"message\":{}}"})
    void invalidInputNeverCallsOllama(String body) throws Exception {
        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ollama);
    }

    @Test
    void lengthBoundaryIsEnforcedBeforeTrimming() throws Exception {
        String message = "a".repeat(1000);
        when(ollama.chat(message)).thenReturn("orientación");
        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + message + "\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + message + " \"}")).andExpect(status().isBadRequest());
        verify(ollama, times(1)).chat(message);
    }

    @Test
    void invalidJwtDoesNotBlockPublicChat() throws Exception {
        when(tokens.validateAndReadClaims("invalid")).thenThrow(new IllegalArgumentException());
        when(ollama.chat("consulta")).thenReturn("orientación");
        mvc.perform(post("/api/v1/ai/chat").header("Authorization", "Bearer invalid")
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"consulta\"}"))
                .andExpect(status().isOk());
        verifyNoInteractions(users);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "HOTEL_ADMIN", "SUPER_ADMIN"})
    void authenticatedRolesKeepSamePublicScopeAndExistingPermissions(String role) throws Exception {
        var id = java.util.UUID.randomUUID();
        when(tokens.validateAndReadClaims("valid")).thenReturn(java.util.Map.of(
                "sub", "actor@example.test", "userId", id.toString(), "role", role, "ver", 0L));
        when(users.access(id)).thenReturn(java.util.Optional.of(new pe.edu.utp.icastay.user.UserAccess(
                id, "actor@example.test", role, "ACTIVE", 0L)));
        when(ollama.chat("consulta")).thenReturn("orientación pública");
        mvc.perform(post("/api/v1/ai/chat").header("Authorization", "Bearer valid")
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"consulta\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("orientación pública"));
        verify(ollama).chat("consulta");
        if (!"USER".equals(role)) {
            mvc.perform(post("/api/v1/reservations").header("Authorization", "Bearer valid"))
                    .andExpect(status().isForbidden());
        }
        if (!"SUPER_ADMIN".equals(role)) {
            mvc.perform(get("/api/v1/superadmin/dashboard").header("Authorization", "Bearer valid"))
                    .andExpect(status().isForbidden());
        }
        if (!"HOTEL_ADMIN".equals(role)) {
            mvc.perform(get("/api/v1/admin/hotel").header("Authorization", "Bearer valid"))
                    .andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {502, 503, 504})
    void upstreamFailuresPreservePublicStatus(int status) throws Exception {
        when(ollama.chat("consulta")).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.valueOf(status), "Asistente no disponible"));
        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"consulta\"}"))
                .andExpect(status().is(status));
    }

    @Test
    void onlyExactPostIsPublicAndExistingRoutesRemainProtected() throws Exception {
        mvc.perform(get("/api/v1/ai/chat")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/ai/chat")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/ai/other")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/ai/chat/extra")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/reservations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/payments/simulate")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/admin/hotel")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/superadmin/dashboard")).andExpect(status().isUnauthorized());
        verifyNoInteractions(ollama);
    }

    @Test
    void corsPreflightAndOriginRulesArePreserved() throws Exception {
        mvc.perform(options("/api/v1/ai/chat").header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(post("/api/v1/ai/chat").header("Origin", "https://example.com")
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"consulta\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ollama);
    }
}
