package pe.edu.utp.icastay.hotel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Explicit allowlist: ownership, ID and status are never bound to the entity.
@JsonIgnoreProperties(ignoreUnknown = true)
public record UpdateHotelRequest(String name, String description, String address, String city) {
}
