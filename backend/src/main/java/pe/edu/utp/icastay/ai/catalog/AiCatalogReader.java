package pe.edu.utp.icastay.ai.catalog;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.utp.icastay.hotel.HotelService;
import pe.edu.utp.icastay.room.RoomService;

/** Public allowlist; never exposes entities, owners, reservation rows or descriptions. */
@Service
public class AiCatalogReader {
    private final HotelService hotels;
    private final RoomService rooms;
    private final EntityManager entityManager;

    public AiCatalogReader(HotelService hotels, RoomService rooms, EntityManager entityManager) {
        this.hotels = hotels; this.rooms = rooms; this.entityManager = entityManager;
    }

    @Transactional(readOnly = true, timeout = 3)
    public String read(AiCatalogQuery query) {
        // Transaction-local limits: do not alter the pool or other modules' connections.
        entityManager.createNativeQuery("select set_config('statement_timeout', '2000', true)").getSingleResult();
        entityManager.createNativeQuery("select set_config('lock_timeout', '1000', true)").getSingleResult();
        var page = query.name() == null ? hotels.listActive(0, 3, null, query.city())
                : hotels.matchActiveName(query.name(), query.city());
        if (page.content().isEmpty()) return query.name() == null
                ? "No encontré hoteles activos en esa ciudad en el catálogo. No he comprobado disponibilidad de habitaciones."
                : "No encontré una coincidencia ACTIVE para ese nombre de hotel y ciudad en el catálogo público. "
                    + "Comprueba el nombre completo; esto no significa que el hotel exista y esté sin habitaciones disponibles.";
        if (query.name() != null && page.totalElements() > 1)
            return "Hay varias coincidencias ACTIVE para ese nombre. Indica la ciudad o el nombre completo entre comillas "
                    + "para distinguirlas; no he comprobado habitaciones. Coincidencias (hasta 3): "
                    + page.content().stream().map(h -> safe(h.name(), 180) + " — " + safe(h.city(), 120))
                        .collect(java.util.stream.Collectors.joining("; "));
        var answer = new StringBuilder("Datos públicos consultados ahora (hasta 3 hoteles y 3 habitaciones por hotel):\n");
        for (var hotel : page.content()) {
            // Recheck ACTIVE through the public room service, including hotel-only queries.
            var visible = rooms.availableLimited(hotel.id(), query.checkIn(), query.checkOut(), query.guests(), 3);
            if (visible.isEmpty()) continue;
            answer.append("Hotel: ").append(safe(hotel.name(), 180)).append("; ciudad: ").append(safe(hotel.city(), 120)).append(".\n");
            if (!query.rooms()) continue;
            if (visible.get().isEmpty()) answer.append(query.checkIn() == null
                    ? "Este hotel existe en el catálogo ACTIVE, pero no tiene habitaciones activas publicadas.\n"
                    : "Este hotel existe en el catálogo ACTIVE, pero no hay habitaciones disponibles para esas fechas y huéspedes.\n");
            for (var room : visible.get()) {
                answer.append("Habitación: ").append(safe(room.number(), 40)).append("; capacidad: ").append(room.capacity())
                        .append("; precio por noche: ").append(room.pricePerNight().toPlainString()).append(' ').append(room.currency()).append(".\n");
            }
        }
        if (query.checkIn() != null) answer.append("Disponibilidad consultada para entrada ").append(query.checkIn())
                .append(", salida ").append(query.checkOut()).append(", ").append(query.guests())
                .append(" huéspedes por habitación. La salida es exclusiva. Puede cambiar antes de confirmar el alojamiento.\n");
        else answer.append("Disponibilidad no comprobada: indica fechas de entrada y salida y huéspedes.\n");
        answer.append("Resultados limitados, no representan todo el catálogo. No dispongo de políticas verificadas; los importes son por noche, no un total final.");
        return answer.toString();
    }

    private String safe(String value, int max) {
        if (value == null) return "No informado";
        String cleaned = value.replaceAll("[\\p{Cc}\\p{Cf}]", " ");
        return cleaned.substring(0, Math.min(cleaned.length(), max));
    }
}
