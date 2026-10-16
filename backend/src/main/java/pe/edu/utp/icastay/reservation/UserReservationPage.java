package pe.edu.utp.icastay.reservation;

import java.util.List;

public record UserReservationPage(List<UserReservationView> content, int page, int size,
        long totalElements, int totalPages) { }
