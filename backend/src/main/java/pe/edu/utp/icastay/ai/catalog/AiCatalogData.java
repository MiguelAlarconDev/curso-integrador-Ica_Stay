package pe.edu.utp.icastay.ai.catalog;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public snapshot, built only from the controlled catalog query; never from model output. */
public record AiCatalogData(Instant checkedAt, String checkIn, String checkOut, Integer guests,
        boolean roomsRequested, List<Hotel> hotels) {
    public AiCatalogData { hotels = List.copyOf(hotels); }
    public enum Availability { UNKNOWN, AVAILABLE, UNAVAILABLE }
    public record Hotel(UUID id, String name, String city, Availability availability, List<Room> rooms) {
        public Hotel { rooms = List.copyOf(rooms); }
    }
    public record Room(String number, int capacity, String pricePerNight, String currency) {}
}
