package pe.edu.utp.icastay.ai.history;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

class AiHistoryServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-21T12:00:00Z");
    private final AiHistoryRepository repository = mock(AiHistoryRepository.class);
    private final AiHistoryService service = new AiHistoryService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    private final String conversation = UUID.randomUUID().toString();
    private final String secret = token(1);

    static String token(int marker) {
        byte[] bytes = new byte[32]; java.util.Arrays.fill(bytes, (byte) marker);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Test
    void derivesDomainSeparatedOwnerWithoutRetainingSecret() {
        String owner = AnonymousOwnerReference.derive(secret);
        assertTrue(owner.matches("[0-9a-f]{64}"));
        assertNotEquals(secret, owner);
        assertEquals(owner, AnonymousOwnerReference.derive(secret));
        assertNotEquals(owner, AnonymousOwnerReference.derive(token(2)));
        assertThrows(IllegalArgumentException.class, () -> AnonymousOwnerReference.derive(owner));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "password", "user@example.test", "ey.fake.jwt", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAB"})
    void rejectsInvalidSessionTokensWithoutEchoingInput(String token) {
        var failure = assertThrows(IllegalArgumentException.class, () -> AnonymousOwnerReference.derive(token));
        assertEquals("Identidad anónima inválida", failure.getMessage());
    }

    @Test
    void storesCompletedExchangeWithThirtyDaysAndOpaqueIds() {
        assertEquals(AiHistoryService.StoreResult.STORED,
                service.storeCompleted(conversation, secret, "Pregunta", "Respuesta", "qwen2.5:1.5b"));
        var capture = ArgumentCaptor.forClass(AiConversationDocument.class);
        verify(repository).insertAndRenew(capture.capture());
        var document = capture.getValue();
        assertEquals(4, UUID.fromString(document.id()).version());
        assertEquals(1, document.schemaVersion());
        assertEquals(conversation, document.conversationId());
        assertEquals(AnonymousOwnerReference.derive(secret), document.ownerReference());
        assertEquals(NOW, document.createdAt());
        assertEquals(NOW.plus(Duration.ofDays(30)), document.expiresAt());
        assertFalse(document.toString().contains("Pregunta"));
        assertFalse(document.toString().contains(secret));
    }

    @Test
    void validatesFieldLimitsBeforeAccessingMongo() {
        for (String question : List.of("", " ", "a".repeat(1001))) {
            assertThrows(IllegalArgumentException.class, () -> service.storeCompleted(conversation, secret, question, "answer", "model"));
        }
        assertThrows(IllegalArgumentException.class, () -> service.storeCompleted(conversation, secret, "question", "a".repeat(16001), "model"));
        assertThrows(IllegalArgumentException.class, () -> service.storeCompleted(conversation, secret, "question", "answer", "m".repeat(129)));
        assertThrows(IllegalArgumentException.class, () -> service.storeCompleted(conversation, null, "question", "answer", "model"));
        assertThrows(IllegalArgumentException.class, () -> service.storeCompleted("not-an-id", secret, "question", "answer", "model"));
        verifyNoInteractions(repository);
        assertEquals(AiHistoryService.StoreResult.STORED, service.storeCompleted(conversation, secret,
                "q".repeat(1000), "a".repeat(16000), "m".repeat(128)));
    }

    @Test
    void readIsOwnerScopedBoundedAndNeverRenews() {
        var first = document(); var second = document();
        when(repository.findActive(anyString(), eq(conversation), eq(NOW), eq(0), eq(1))).thenReturn(List.of(first, second));
        var page = service.findActive(conversation, secret, 0, 1);
        assertTrue(page.available()); assertTrue(page.hasNext()); assertEquals(List.of(first), page.content());
        verify(repository).findActive(AnonymousOwnerReference.derive(secret), conversation, NOW, 0, 1);
        verify(repository, never()).insertAndRenew(any());
        assertThrows(UnsupportedOperationException.class, () -> page.content().clear());
    }

    @Test
    void invalidPaginationNeverCallsMongo() {
        for (int[] bounds : new int[][] {{-1, 1}, {1001, 1}, {0, 0}, {0, 51}}) {
            assertThrows(IllegalArgumentException.class, () -> service.findActive(conversation, secret, bounds[0], bounds[1]));
        }
        verifyNoInteractions(repository);
    }

    @Test
    void infrastructureFailureIsExplicitAndNotRetried() {
        doThrow(new DataAccessResourceFailureException("internal detail")).when(repository).insertAndRenew(any());
        when(repository.findActive(anyString(), anyString(), any(), anyInt(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("internal detail"));
        assertEquals(AiHistoryService.StoreResult.UNAVAILABLE, service.storeCompleted(conversation, secret, "Q", "A", "model"));
        var page = service.findActive(conversation, secret, 0, 20);
        assertFalse(page.available()); assertTrue(page.content().isEmpty());
        verify(repository, times(1)).insertAndRenew(any());
        verify(repository, times(1)).findActive(anyString(), anyString(), any(), anyInt(), anyInt());
    }

    private AiConversationDocument document() {
        return new AiConversationDocument(UUID.randomUUID().toString(), 1, conversation,
                AnonymousOwnerReference.derive(secret), "Q", "A", "model", NOW, NOW.plus(Duration.ofDays(30)));
    }
}
