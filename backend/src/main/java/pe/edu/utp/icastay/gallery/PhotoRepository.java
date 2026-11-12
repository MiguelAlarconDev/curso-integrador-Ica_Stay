package pe.edu.utp.icastay.gallery;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;

@Repository
class PhotoRepository {
    enum Kind {
        HOTEL("hotel_photos", "hotel_id", "hotels", 15), ROOM("room_photos", "room_id", "rooms", 10);
        final String table, ownerColumn, namespace;
        final int limit;
        Kind(String table, String ownerColumn, String namespace, int limit) {
            this.table = table; this.ownerColumn = ownerColumn; this.namespace = namespace; this.limit = limit;
        }
    }
    record Owner(UUID hotelId, UUID roomId) {
        UUID id() { return roomId == null ? hotelId : roomId; }
        Kind kind() { return roomId == null ? Kind.HOTEL : Kind.ROOM; }
        String baseUrl() { return "/api/v1/hotels/" + hotelId + (roomId == null ? "" : "/rooms/" + roomId) + "/photos"; }
        String adminUrl() { return "/api/v1/admin/hotel" + (roomId == null ? "" : "/rooms/" + roomId) + "/photos"; }
    }
    record Stored(PhotoView view, String key) {}
    private final JdbcTemplate jdbc;
    PhotoRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Owner assigned(UUID admin, UUID room, boolean lock) {
        List<UUID> hotels = jdbc.query("SELECT id FROM hotels WHERE admin_user_id=?" + (lock ? " FOR UPDATE" : ""),
                (row, index) -> row.getObject(1, UUID.class), admin);
        if (hotels.isEmpty()) throw notFound();
        UUID hotel = hotels.getFirst();
        if (room != null && jdbc.query("SELECT id FROM rooms WHERE id=? AND hotel_id=?" + (lock ? " FOR UPDATE" : ""),
                (row, index) -> row.getObject(1, UUID.class), room, hotel).isEmpty()) throw notFound();
        return new Owner(hotel, room);
    }

    void requireVisible(Owner owner) {
        boolean visible = owner.roomId == null
                ? Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM hotels WHERE id=? AND status='ACTIVE')", Boolean.class, owner.hotelId))
                : Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM rooms r JOIN hotels h ON h.id=r.hotel_id WHERE r.id=? AND h.id=? AND r.status='ACTIVE' AND h.status='ACTIVE')", Boolean.class, owner.roomId, owner.hotelId));
        if (!visible) throw notFound();
    }

    List<Stored> list(Owner owner) { return list(owner, owner.baseUrl()); }

    List<Stored> list(Owner owner, String baseUrl) {
        Kind kind = owner.kind();
        return jdbc.query("SELECT * FROM " + kind.table + " WHERE " + kind.ownerColumn + "=? ORDER BY position ASC,id ASC",
                (row, index) -> new Stored(new PhotoView(row.getObject("id", UUID.class),
                        baseUrl + "/" + row.getObject("id") + "/content", row.getString("mime_type"),
                        row.getLong("size_bytes"), row.getInt("width"), row.getInt("height"), row.getString("alt_text"),
                        row.getInt("position"), row.getBoolean("is_cover"), row.getTimestamp("created_at").toInstant()), row.getString("storage_key")), owner.id());
    }

    Stored insert(Owner owner, UUID id, String key, PhotoValidator.Validated image, String alt, int position, boolean cover) {
        Kind kind = owner.kind();
        jdbc.update("INSERT INTO " + kind.table + " (id," + kind.ownerColumn + ",storage_key,mime_type,size_bytes,width,height,alt_text,position,is_cover) VALUES (?,?,?,?,?,?,?,?,?,?)",
                id, owner.id(), key, image.mimeType(), image.bytes().length, image.width(), image.height(), alt, position, cover);
        return list(owner).stream().filter(photo -> photo.view.id().equals(id)).findFirst().orElseThrow();
    }

    void order(Owner owner, List<UUID> ids) {
        Kind kind = owner.kind();
        String constraint = "uq_" + kind.table + "_position";
        jdbc.execute("SET CONSTRAINTS " + constraint + " DEFERRED");
        for (int position = 0; position < ids.size(); position++) {
            jdbc.update("UPDATE " + kind.table + " SET position=? WHERE " + kind.ownerColumn + "=? AND id=?",
                    position, owner.id(), ids.get(position));
        }
        jdbc.execute("SET CONSTRAINTS " + constraint + " IMMEDIATE");
    }

    void cover(Owner owner, UUID id) {
        Kind kind = owner.kind();
        jdbc.update("UPDATE " + kind.table + " SET is_cover=false WHERE " + kind.ownerColumn + "=? AND is_cover", owner.id());
        jdbc.update("UPDATE " + kind.table + " SET is_cover=true WHERE " + kind.ownerColumn + "=? AND id=?", owner.id(), id);
    }

    void delete(Owner owner, Stored photo) {
        Kind kind = owner.kind();
        jdbc.update("DELETE FROM " + kind.table + " WHERE " + kind.ownerColumn + "=? AND id=?", owner.id(), photo.view.id());
        // Durable intent committed with the metadata change; no filesystem work in this transaction.
        jdbc.update("INSERT INTO photo_file_deletions(storage_key) VALUES (?)", photo.key);
    }

    static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND); }
}
