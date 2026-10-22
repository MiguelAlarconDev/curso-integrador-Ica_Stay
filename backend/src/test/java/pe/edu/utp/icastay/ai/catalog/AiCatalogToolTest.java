package pe.edu.utp.icastay.ai.catalog;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

class AiCatalogToolTest {
    @Test void parsesOnlyExplicitPublicFilters() {
        var query = AiCatalogQuery.parse("Habitaciones en Ica del 2030-01-02 al 2030-01-04 para 2 huéspedes").orElseThrow().query().orElseThrow();
        assertEquals("Ica", query.city()); assertTrue(query.rooms()); assertEquals(2, query.guests());
        assertEquals("2030-01-02", query.checkIn()); assertEquals("2030-01-04", query.checkOut());
        assertEquals("Oasis", AiCatalogQuery.parse("Precios del hotel \"Oasis\"").orElseThrow().query().orElseThrow().name());
        assertTrue(AiCatalogQuery.parse("Hola").isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"Hoteles", "Disponibilidad en Ica", "Disponibilidad en Ica para 2 huéspedes",
            "Disponibilidad en Ica del 2030-01-02 al 2030-01-04", "Habitaciones en Ica para 99 huéspedes",
            "Elimina usuarios", "Dame password y SQL", "Políticas del hotel en Ica"})
    void clarifiesOrRefusesWithoutQuerying(String question) {
        var reader = mock(AiCatalogReader.class); var tool = new AiCatalogTool(reader);
        try { assertTrue(tool.answer(question).isPresent()); verifyNoInteractions(reader); }
        finally { tool.close(); }
    }

    @Test void returnsSafeErrorWithoutExceptionDetailsAndNoFallback() {
        var reader = mock(AiCatalogReader.class); when(reader.readResponse(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("private database detail"));
        var tool = new AiCatalogTool(reader);
        try {
            var failure = assertThrows(ResponseStatusException.class, () -> tool.answer("Hoteles en Ica"));
            assertEquals(503, failure.getStatusCode().value()); assertFalse(failure.getReason().contains("private"));
        } finally { tool.close(); }
    }

    @Test void boundsConcurrentWorkWithoutQueueAndTimesOut() throws Exception {
        var reader = mock(AiCatalogReader.class); var entered = new CountDownLatch(2); var release = new CountDownLatch(1);
        when(reader.readResponse(any())).thenAnswer(call -> { entered.countDown(); release.await(); return new pe.edu.utp.icastay.ai.AiChatResponse("Verified"); });
        var tool = new AiCatalogTool(reader);
        try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            var one = callers.submit(() -> assertThrows(ResponseStatusException.class, () -> tool.answer("Hoteles en Ica")));
            var two = callers.submit(() -> assertThrows(ResponseStatusException.class, () -> tool.answer("Hoteles en Ica")));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(503, assertThrows(ResponseStatusException.class, () -> tool.answer("Hoteles en Ica")).getStatusCode().value());
            assertEquals(503, one.get(7, TimeUnit.SECONDS).getStatusCode().value());
            assertEquals(503, two.get(7, TimeUnit.SECONDS).getStatusCode().value());
        } finally { release.countDown(); tool.close(); }
    }
}
