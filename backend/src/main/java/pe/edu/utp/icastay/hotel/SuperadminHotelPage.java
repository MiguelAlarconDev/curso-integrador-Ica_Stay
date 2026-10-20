package pe.edu.utp.icastay.hotel;

import java.util.List;

public record SuperadminHotelPage(List<SuperadminHotelView> content, int page, int size,
        long totalElements, int totalPages) {}
