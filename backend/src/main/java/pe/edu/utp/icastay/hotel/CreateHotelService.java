package pe.edu.utp.icastay.hotel;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.icastay.auth.AuthService;

@Service
public class CreateHotelService {
    private final HotelRepository hotels;
    private final AuthService auth;

    public CreateHotelService(HotelRepository hotels, AuthService auth) {
        this.hotels = hotels;
        this.auth = auth;
    }

    @Transactional
    public SuperadminHotelView create(CreateHotelRequest request) {
        if (request == null || request.admin() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "datos de hotel y administrador obligatorios");
        }
        var details = HotelService.validateDetails(new UpdateHotelRequest(
                request.name(), request.description(), request.address(), request.city()));
        var admin = auth.createHotelAdmin(request.admin().name(), request.admin().email(), request.admin().password());
        var hotel = hotels.saveAndFlush(HotelEntity.create(admin.getId(), details));
        return new SuperadminHotelView(hotel.getId(), hotel.getName(), hotel.getAddress(), hotel.getCity(),
                "ACTIVE", admin.getId(), admin.getName(), admin.getEmail());
    }
}
