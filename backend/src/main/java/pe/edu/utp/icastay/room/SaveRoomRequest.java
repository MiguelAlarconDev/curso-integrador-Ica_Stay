package pe.edu.utp.icastay.room;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SaveRoomRequest(String number, String description, BigDecimal capacity,
        BigDecimal pricePerNight, String currency) {
}
