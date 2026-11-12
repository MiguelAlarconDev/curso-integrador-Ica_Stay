package pe.edu.utp.icastay.gallery;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.utp.icastay.gallery.PhotoRepository.Owner;

@Service
public class PhotoService {
    private static final Logger log = LoggerFactory.getLogger(PhotoService.class);
    private final PhotoRepository photos;
    private final PhotoStorage storage;
    private final PhotoValidator validator;
    private final TransactionTemplate transaction;
    private final PhotoDeletionCleanup cleanup;

    PhotoService(PhotoRepository photos, PhotoStorage storage, PhotoValidator validator, PlatformTransactionManager manager, PhotoDeletionCleanup cleanup) {
        this.photos = photos; this.storage = storage; this.validator = validator;
        this.transaction = new TransactionTemplate(manager);
        this.cleanup = cleanup;
    }

    public PhotoView upload(UUID admin, UUID room, MultipartFile file, String altText) {
        Owner owner = photos.assigned(admin, room, false); // Check ownership before decoding or writing files.
        String alt = altText == null ? "" : altText.strip();
        if (alt.length() > 500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "altText admite hasta 500 caracteres");
        if (photos.list(owner).size() >= owner.kind().limit) throw full();
        var image = validator.validate(file);
        UUID id = UUID.randomUUID();
        String key = owner.kind().namespace + "/" + owner.id() + "/" + id;
        try { storage.store(key, image.bytes()); }
        catch (IOException error) { throw unavailable(error); }
        AtomicBoolean managed = new AtomicBoolean();
        try {
            return transaction.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int completion) {
                        if (completion == STATUS_ROLLED_BACK) compensate(key);
                        else if (completion == STATUS_UNKNOWN) log.error("Photo transaction outcome unknown; reconcile key {}", key);
                    }
                });
                managed.set(true);
                Owner locked = photos.assigned(admin, room, true);
                if (!owner.equals(locked)) throw PhotoRepository.notFound();
                var current = photos.list(owner);
                if (current.size() >= owner.kind().limit) throw full();
                normalize(owner, current);
                return photos.insert(owner, id, key, image, alt, current.size(), current.isEmpty()).view();
            });
        } catch (RuntimeException error) {
            if (!managed.get()) compensate(key);
            throw error;
        }
    }

    private void compensate(String key) {
        try { storage.delete(key); }
        catch (IOException error) { log.error("Photo rollback cleanup failed; reconcile key {}", key, error); }
    }

    public void delete(UUID admin, UUID room, UUID photoId) {
        transaction.executeWithoutResult(status -> {
            Owner owner = photos.assigned(admin, room, true);
            var current = photos.list(owner);
            var photo = current.stream().filter(item -> item.view().id().equals(photoId)).findFirst().orElseThrow(PhotoRepository::notFound);
            photos.delete(owner, photo);
            normalize(owner, photos.list(owner));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { cleanup.retryPending(); }
            });
        });
    }

    public List<PhotoView> reorder(UUID admin, UUID room, List<UUID> ids) {
        return transaction.execute(status -> {
            Owner owner = photos.assigned(admin, room, true);
            var current = photos.list(owner);
            if (ids == null || ids.size() > owner.kind().limit || ids.stream().anyMatch(java.util.Objects::isNull)
                    || new java.util.HashSet<>(ids).size() != ids.size()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "photoIds debe ser una lista sin duplicados ni nulos");
            }
            var ownIds = current.stream().map(item -> item.view().id()).collect(java.util.stream.Collectors.toSet());
            if (!ownIds.containsAll(ids)) throw PhotoRepository.notFound();
            if (ids.size() != current.size()) throw new ResponseStatusException(HttpStatus.CONFLICT, "La lista debe incluir toda la galeria actual");
            photos.order(owner, ids);
            normalize(owner, photos.list(owner));
            return photos.list(owner).stream().map(PhotoRepository.Stored::view).toList();
        });
    }

    public PhotoView selectCover(UUID admin, UUID room, UUID photoId) {
        return transaction.execute(status -> {
            Owner owner = photos.assigned(admin, room, true);
            var current = photos.list(owner);
            if (current.stream().noneMatch(item -> item.view().id().equals(photoId))) throw PhotoRepository.notFound();
            normalize(owner, current);
            photos.cover(owner, photoId);
            return photos.list(owner).stream().map(PhotoRepository.Stored::view).filter(photo -> photo.id().equals(photoId)).findFirst().orElseThrow();
        });
    }

    private void normalize(Owner owner, List<PhotoRepository.Stored> ordered) {
        photos.order(owner, ordered.stream().map(item -> item.view().id()).toList());
        if (!ordered.isEmpty() && ordered.stream().noneMatch(item -> item.view().isCover())) {
            photos.cover(owner, ordered.getFirst().view().id());
        }
    }

    public List<PhotoView> list(UUID hotel, UUID room) {
        Owner owner = new Owner(hotel, room);
        photos.requireVisible(owner);
        return photos.list(owner).stream().map(PhotoRepository.Stored::view).toList();
    }

    /** Owner-only read, regardless of hotel/room status; URLs point to the authenticated content route. */
    public List<PhotoView> adminList(UUID admin, UUID room) {
        Owner owner = photos.assigned(admin, room, false);
        return photos.list(owner, owner.adminUrl()).stream().map(PhotoRepository.Stored::view).toList();
    }

    public Content adminContent(UUID admin, UUID room, UUID id) { return read(photos.assigned(admin, room, false), id); }

    public record Content(byte[] bytes, String mimeType) {}
    public Content content(UUID hotel, UUID room, UUID id) {
        Owner owner = new Owner(hotel, room);
        photos.requireVisible(owner);
        return read(owner, id);
    }

    private Content read(Owner owner, UUID id) {
        var photo = photos.list(owner).stream().filter(item -> item.view().id().equals(id)).findFirst().orElseThrow(PhotoRepository::notFound);
        try { return new Content(storage.read(photo.key()), photo.view().mimeType()); }
        catch (NoSuchFileException missing) { throw PhotoRepository.notFound(); }
        catch (IOException error) { throw unavailable(error); }
    }

    private static ResponseStatusException full() { return new ResponseStatusException(HttpStatus.CONFLICT, "Limite de fotos alcanzado"); }
    private static ResponseStatusException unavailable(IOException error) { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Almacenamiento de fotos no disponible", error); }
}
