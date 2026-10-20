package pe.edu.utp.icastay.hotel;

import java.util.UUID;

public record SuperadminHotelDetail(UUID id, String name, String address, String city,
        String status, UUID adminUserId, String adminName, String adminEmail, String description) {}
