package pe.edu.utp.icastay.hotel;

import java.util.List;

public record HotelPage(List<HotelView> content, int page, int size, long totalElements, int totalPages) {
}
