package pe.edu.utp.icastay.room;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RoomStatusRequest(String status) {
}
