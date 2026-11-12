package pe.edu.utp.icastay.gallery;

import java.io.IOException;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalPhotoStorage implements PhotoStorage {
    private final Path root;
    private static final String UUID_PATTERN = "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}";

    public LocalPhotoStorage(@Value("${icastay.photos.root}") String configuredRoot) {
        root = Path.of(configuredRoot).toAbsolutePath().normalize();
        for (Path path = root; path != null; path = path.getParent()) {
            if (Files.exists(path.resolve(".git"))) throw new IllegalArgumentException("Photo storage must be outside the repository");
        }
    }

    private Path resolve(String key, boolean create) throws IOException {
        if (key == null || !key.matches("(hotels|rooms)/" + UUID_PATTERN + "/" + UUID_PATTERN)) {
            throw new IOException("Invalid storage key");
        }
        Path target = root.resolve(key).normalize();
        // Reject symlink ancestors as well as symlink files. The volume must be owned by the backend account.
        for (Path path = target; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) throw new IOException("Symbolic links are not supported");
        }
        if (create) Files.createDirectories(target.getParent());
        if (Files.exists(target.getParent()) && !target.getParent().toRealPath().startsWith(root.toRealPath())) {
            throw new IOException("Storage path escaped root");
        }
        return target;
    }

    @Override public void store(String key, byte[] content) throws IOException {
        Path target = resolve(key, true);
        // CREATE_NEW prevents replacement even if a key is accidentally reused.
        boolean created = false;
        try {
            var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            created = true;
            try (output) { output.write(content); }
        } catch (IOException error) {
            if (created) {
                try { Files.deleteIfExists(target); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
            }
            throw error;
        }
    }

    @Override public byte[] read(String key) throws IOException {
        Path target = resolve(key, false);
        try (var input = Files.newInputStream(target, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(PhotoValidator.MAX_BYTES + 1);
            if (bytes.length > PhotoValidator.MAX_BYTES) throw new IOException("Stored photo exceeds size limit");
            return bytes;
        }
    }

    @Override public void delete(String key) throws IOException { Files.deleteIfExists(resolve(key, false)); }
}
