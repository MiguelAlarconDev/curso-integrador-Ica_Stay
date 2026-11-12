package pe.edu.utp.icastay.gallery;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.utp.icastay.auth.AuthenticatedUser;

@RestController
public class PhotoController {
    private final PhotoService photos;
    PhotoController(PhotoService photos) { this.photos = photos; }

    public record OrderRequest(List<UUID> photoIds) {}

    @DeleteMapping("/api/v1/admin/hotel/photos/{photoId}")
    public ResponseEntity<Void> deleteHotel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID photoId) {
        photos.delete(user.id(), null, photoId); return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/v1/admin/hotel/rooms/{roomId}/photos/{photoId}")
    public ResponseEntity<Void> deleteRoom(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roomId, @PathVariable UUID photoId) {
        photos.delete(user.id(), roomId, photoId); return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/v1/admin/hotel/photos/order")
    public ResponseEntity<List<PhotoView>> orderHotel(@AuthenticationPrincipal AuthenticatedUser user, @RequestBody OrderRequest request) {
        return metadata(photos.reorder(user.id(), null, request == null ? null : request.photoIds()));
    }

    @PutMapping("/api/v1/admin/hotel/rooms/{roomId}/photos/order")
    public ResponseEntity<List<PhotoView>> orderRoom(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roomId, @RequestBody OrderRequest request) {
        return metadata(photos.reorder(user.id(), roomId, request == null ? null : request.photoIds()));
    }

    @PutMapping("/api/v1/admin/hotel/photos/{photoId}/cover")
    public ResponseEntity<PhotoView> coverHotel(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID photoId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(photos.selectCover(user.id(), null, photoId));
    }

    @PutMapping("/api/v1/admin/hotel/rooms/{roomId}/photos/{photoId}/cover")
    public ResponseEntity<PhotoView> coverRoom(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roomId, @PathVariable UUID photoId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(photos.selectCover(user.id(), roomId, photoId));
    }

    @PostMapping(value = "/api/v1/admin/hotel/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoView> uploadHotel(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "") String altText) {
        return created(photos.upload(user.id(), null, file, altText));
    }

    @PostMapping(value = "/api/v1/admin/hotel/rooms/{roomId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PhotoView> uploadRoom(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roomId,
            @RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "") String altText) {
        return created(photos.upload(user.id(), roomId, file, altText));
    }

    @GetMapping("/api/v1/admin/hotel/photos")
    public ResponseEntity<List<PhotoView>> adminHotelList(@AuthenticationPrincipal AuthenticatedUser user) { return metadata(photos.adminList(user.id(), null)); }

    @GetMapping("/api/v1/admin/hotel/rooms/{roomId}/photos")
    public ResponseEntity<List<PhotoView>> adminRoomList(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roomId) { return metadata(photos.adminList(user.id(), roomId)); }

    @GetMapping("/api/v1/admin/hotel/photos/{photoId}/content")
    public ResponseEntity<byte[]> adminHotelContent(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID photoId) { return content(photos.adminContent(user.id(), null, photoId)); }

    @GetMapping("/api/v1/admin/hotel/rooms/{roomId}/photos/{photoId}/content")
    public ResponseEntity<byte[]> adminRoomContent(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID roomId, @PathVariable UUID photoId) { return content(photos.adminContent(user.id(), roomId, photoId)); }

    @GetMapping("/api/v1/hotels/{hotelId}/photos")
    public ResponseEntity<List<PhotoView>> hotelList(@PathVariable UUID hotelId) { return metadata(photos.list(hotelId, null)); }

    @GetMapping("/api/v1/hotels/{hotelId}/rooms/{roomId}/photos")
    public ResponseEntity<List<PhotoView>> roomList(@PathVariable UUID hotelId, @PathVariable UUID roomId) { return metadata(photos.list(hotelId, roomId)); }

    @GetMapping("/api/v1/hotels/{hotelId}/photos/{photoId}/content")
    public ResponseEntity<byte[]> hotelContent(@PathVariable UUID hotelId, @PathVariable UUID photoId) { return content(photos.content(hotelId, null, photoId)); }

    @GetMapping("/api/v1/hotels/{hotelId}/rooms/{roomId}/photos/{photoId}/content")
    public ResponseEntity<byte[]> roomContent(@PathVariable UUID hotelId, @PathVariable UUID roomId, @PathVariable UUID photoId) { return content(photos.content(hotelId, roomId, photoId)); }

    private ResponseEntity<PhotoView> created(PhotoView photo) { return ResponseEntity.created(URI.create(photo.url())).cacheControl(CacheControl.noStore()).body(photo); }
    private ResponseEntity<List<PhotoView>> metadata(List<PhotoView> list) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(list); }
    private ResponseEntity<byte[]> content(PhotoService.Content content) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.mimeType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline").contentLength(content.bytes().length).body(content.bytes());
    }
}
