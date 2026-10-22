package pe.edu.utp.icastay.ai.catalog;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AiCatalogNaturalQueryTest {
    @ParameterizedTest @ValueSource(strings = {"¿Qué hoteles hay disponibles en Ica?", "Hoteles en Ica",
            "¿Qué hotel hay disponible en Ica?", "Hotel en Ica", "Hoteles en la ciudad de Ica"})
    void cityListingDoesNotClaimDatedAvailability(String message) {
        var query = AiCatalogQuery.parse(message).orElseThrow().query().orElseThrow();
        assertEquals("Ica", query.city()); assertNull(query.name()); assertFalse(query.rooms());
        assertNull(query.checkIn()); assertNull(query.guests());
    }
    @ParameterizedTest @ValueSource(strings = {"Muéstrame las habitaciones y precios del hotel Oasis Ica",
            "Precios del Hotel Oasis Ica", "Habitaciones en hotel Oasis Ica", "Habitaciones del hotel \"Oasis Ica\""})
    void recognizesHotelMentionWithoutRequiringQuotes(String message) {
        var query = AiCatalogQuery.parse(message).orElseThrow().query().orElseThrow();
        assertEquals("Oasis Ica", query.name()); assertNull(query.city()); assertTrue(query.rooms());
        assertNull(query.checkIn());
    }
    @Test void datesGuestsAndHotelAreIndependentFromCity() {
        var query = AiCatalogQuery.parse("¿Hay habitaciones disponibles en Hotel Oasis Ica del 2026-12-15 al 2026-12-17 para 2 huéspedes?")
                .orElseThrow().query().orElseThrow();
        assertEquals("Oasis Ica", query.name()); assertNull(query.city());
        assertEquals("2026-12-15", query.checkIn()); assertEquals("2026-12-17", query.checkOut()); assertEquals(2, query.guests());
    }
    @ParameterizedTest @ValueSource(strings = {"0", "-1", "21", "1000", "1.5", "2,5"})
    void rejectsInvalidGuestCounts(String guests) {
        var plan = AiCatalogQuery.parse("Habitaciones en Ica del 2030-01-02 al 2030-01-04 para " + guests + " huéspedes").orElseThrow();
        assertTrue(plan.query().isEmpty()); assertTrue(plan.clarification().contains("1 y 20"));
        assertFalse(plan.clarification().contains("Indica la ciudad"));
    }
    @Test void partialAvailabilityAsksOnlyForMissingCriteriaAndRoomListingStaysUndated() {
        var partial = AiCatalogQuery.parse("Habitaciones disponibles en Ica para 2 huéspedes").orElseThrow();
        assertTrue(partial.query().isEmpty()); assertFalse(partial.clarification().contains("ciudad"));
        var listing = AiCatalogQuery.parse("Habitaciones disponibles en Ica").orElseThrow().query().orElseThrow();
        assertTrue(listing.rooms()); assertNull(listing.checkIn());
    }
}
