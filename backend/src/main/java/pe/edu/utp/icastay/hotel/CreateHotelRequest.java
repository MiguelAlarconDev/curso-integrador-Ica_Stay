package pe.edu.utp.icastay.hotel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateHotelRequest(String name, String description, String address, String city, Admin admin) {
    @Override public String toString() { return "CreateHotelRequest[REDACTED]"; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Admin(String name, String email, String password) {
        @Override public String toString() { return "Admin[REDACTED]"; }
    }
}
