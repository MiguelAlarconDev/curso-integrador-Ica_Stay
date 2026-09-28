package pe.edu.utp.icastay.hotel;

import java.util.UUID;

public record HotelView(UUID id, String name, String description, String address, String city) {
}
