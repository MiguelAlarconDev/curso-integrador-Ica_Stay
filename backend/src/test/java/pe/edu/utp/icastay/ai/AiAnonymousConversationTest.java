package pe.edu.utp.icastay.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.utp.icastay.ai.history.*;

@SpringJUnitWebConfig(AiChatEndpointTest.Config.class)
@TestPropertySource(properties = "icastay.cors.allowed-origin=http://localhost:4200")
class AiAnonymousConversationTest {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired OllamaClient ollama;
    @Autowired AiHistoryService history;
    @Autowired AiSessionService sessions;
    private MockMvc mvc;
    private final List<AiConversationDocument> stored = new ArrayList<>();

    @BeforeEach void setup() {
        reset(ollama, history); stored.clear();
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        when(ollama.chat(anyString())).thenReturn("Orientación hotelera");
        when(ollama.chat(anyString(), anyList())).thenReturn("Orientación contextual");
        when(history.storeCompleted(anyString(), anyString(), anyString(), anyString(), anyString())).thenAnswer(call -> {
            stored.add(new AiConversationDocument(UUID.randomUUID().toString(), 1, call.getArgument(0),
                    AnonymousOwnerReference.derive(call.getArgument(1)), call.getArgument(2), call.getArgument(3),
                    call.getArgument(4), Instant.now(), Instant.now().plusSeconds(2592000)));
            return AiHistoryService.StoreResult.STORED;
        });
        when(history.findActive(anyString(), anyString(), anyInt(), anyInt())).thenAnswer(call -> {
            String conversation = call.getArgument(0); String owner = AnonymousOwnerReference.derive(call.getArgument(1));
            int page = call.getArgument(2); int size = call.getArgument(3);
            var matches = stored.stream().filter(item -> item.conversationId().equals(conversation)
                    && item.ownerReference().equals(owner) && item.expiresAt().isAfter(Instant.now()))
                    .sorted(java.util.Comparator.comparing(AiConversationDocument::createdAt).reversed()).toList();
            return new AiHistoryService.HistoryPage(true, matches.stream().skip((long) page * size).limit(size).toList(),
                    page, size, matches.size() > (long) (page + 1) * size);
        });
    }

    private MvcResult chat(Cookie cookie, String question) throws Exception {
        var request = post("/api/v1/ai/chat").header("Origin", "http://localhost:4200")
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"" + question + "\"}");
        if (cookie != null) request.cookie(cookie);
        return mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.answer").isString())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
    }
    private Cookie cookie(MvcResult result) {
        return new Cookie(AiSessionService.COOKIE_NAME, AiSessionServiceTest.value(result.getResponse().getHeader("Set-Cookie")));
    }

    @Test void createsReusesPersistsAndIsolatesTwoVisitors() throws Exception {
        var first = cookie(chat(null, "Primera pregunta"));
        var firstSession = sessions.decode(first.getValue()).orElseThrow();
        var reused = cookie(chat(first, "Segunda pregunta"));
        assertTrue(sessions.decode(reused.getValue()).orElseThrow().secret().equals(firstSession.secret()));
        var second = cookie(chat(null, "Otro visitante"));
        assertEquals(3, stored.size());
        mvc.perform(get("/api/v1/ai/conversations/current").cookie(first).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].question").value("Segunda pregunta"))
                .andExpect(jsonPath("$.hasNext").value(true)).andExpect(jsonPath("$.content[0].ownerReference").doesNotExist());
        mvc.perform(get("/api/v1/ai/conversations/current").cookie(first).param("page", "1").param("size", "1"))
                .andExpect(jsonPath("$.content[0].question").value("Primera pregunta"));
        mvc.perform(get("/api/v1/ai/conversations/current").cookie(second).param("conversationId", firstSession.conversationId()))
                .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.content[0].question").value("Otro visitante"));
        mvc.perform(get("/api/v1/ai/conversations/current").param("conversationId", firstSession.conversationId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.conversationId").isEmpty());
    }

    @Test void invalidCookieCannotSelectAnExistingConversation() throws Exception {
        var valid = cookie(chat(null, "Pregunta"));
        var parts = valid.getValue().split("\\."); parts[1] = sessions.create().secret();
        var altered = new Cookie(AiSessionService.COOKIE_NAME, String.join(".", parts));
        mvc.perform(get("/api/v1/ai/conversations/current").cookie(altered)).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(header().exists("Set-Cookie"));
        var fresh = cookie(chat(altered, "Nueva pregunta"));
        assertFalse(sessions.decode(valid.getValue()).orElseThrow().conversationId()
                .equals(sessions.decode(fresh.getValue()).orElseThrow().conversationId()));
    }

    @Test void cookieFromAnotherSigningKeyCannotRetrieveRecords() throws Exception {
        var old = new AiSessionService("", false, "http://localhost:4200");
        // A signed cookie from a different key is rejected, and expiry itself is covered with a fixed clock.
        mvc.perform(get("/api/v1/ai/conversations/current").cookie(new Cookie(AiSessionService.COOKIE_NAME,
                AiSessionServiceTest.value(old.cookie(old.create()))))).andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1001", "bad"})
    void invalidPageIsRejected(String page) throws Exception {
        mvc.perform(get("/api/v1/ai/conversations/current").param("page", page)).andExpect(status().isBadRequest());
    }
    @ParameterizedTest
    @ValueSource(strings = {"0", "51", "bad"})
    void invalidSizeIsRejected(String size) throws Exception {
        mvc.perform(get("/api/v1/ai/conversations/current").param("size", size)).andExpect(status().isBadRequest());
    }

    @Test void mongoFailureDegradesChatButReturns503ForHistory() throws Exception {
        var cookie = cookie(chat(null, "Pregunta"));
        doReturn(new AiHistoryService.HistoryPage(false, List.of(), 0, 5, false))
                .when(history).findActive(anyString(), anyString(), anyInt(), anyInt());
        doReturn(AiHistoryService.StoreResult.UNAVAILABLE)
                .when(history).storeCompleted(anyString(), anyString(), anyString(), anyString(), anyString());
        chat(cookie, "Otra pregunta");
        verify(ollama).chat("Otra pregunta");
        mvc.perform(get("/api/v1/ai/conversations/current").cookie(cookie)).andExpect(status().isServiceUnavailable());
    }

    @Test void refusesCsrfWithCookieOrBrowserMetadataAndKeepsOtherCorsUnchanged() throws Exception {
        Cookie cookie = cookie(chat(null, "Pregunta"));
        for (String origin : new String[] {"https://evil.example", "null", "http://localhost:9999"}) {
            mvc.perform(post("/api/v1/ai/chat").cookie(cookie).header("Origin", origin)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Pregunta\"}")).andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/ai/chat").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Pregunta\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/ai/chat").header("Sec-Fetch-Site", "same-site").contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Pregunta\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/ai/chat").header("Origin", "http://localhost:4200")
                .contentType(MediaType.TEXT_PLAIN).content("{\"message\":\"Pregunta\"}")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(options("/api/v1/ai/conversations/current").header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "GET")).andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/v1/reservations").header("Origin", "http://localhost:4200")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(post("/api/v1/ai/conversations/current")).andExpect(status().isUnauthorized());
    }

    @Test void sensitiveContentAndIncompleteGenerationAreNotStored() throws Exception {
        chat(null, "Mi contraseña es ejemplo"); assertTrue(stored.isEmpty());
        when(ollama.chat("Pregunta")).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT));
        mvc.perform(post("/api/v1/ai/chat").header("Origin", "http://localhost:4200")
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Pregunta\"}")).andExpect(status().isGatewayTimeout());
        assertTrue(stored.isEmpty());
    }
}
