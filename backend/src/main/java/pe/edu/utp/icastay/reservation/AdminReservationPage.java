package pe.edu.utp.icastay.reservation;

import java.util.List;

public record AdminReservationPage(List<AdminReservationView> content, int page, int size,
        long totalElements, int totalPages) {
}
