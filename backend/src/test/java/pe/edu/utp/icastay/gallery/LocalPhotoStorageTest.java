package pe.edu.utp.icastay.gallery;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalPhotoStorageTest {
    @TempDir Path directory;
    private String key() { return "hotels/" + UUID.randomUUID() + "/" + UUID.randomUUID(); }

    @Test void survivesNewStorageInstanceAndDoesNotOverwrite() throws Exception {
        String key = key(); byte[] bytes = {1, 2, 3};
        var storage = new LocalPhotoStorage(directory.toString());
        storage.store(key, bytes);
        var restarted = new LocalPhotoStorage(directory.toString());
        assertArrayEquals(bytes, restarted.read(key));
        assertThrows(FileAlreadyExistsException.class, () -> restarted.store(key, new byte[]{9}));
        assertArrayEquals(bytes, restarted.read(key));
        restarted.delete(key); assertThrows(NoSuchFileException.class, () -> storage.read(key));
    }

    @Test void rejectsUnsafeKeysAndRepositoryRoots() throws Exception {
        var storage = new LocalPhotoStorage(directory.toString());
        for (String key : new String[]{"../secret", "C:/secret", "/etc/passwd", "hotels/../x", "hotels/a/b", "rooms\\a\\b"}) {
            assertThrows(IOException.class, () -> storage.store(key, new byte[]{1}));
            assertThrows(IOException.class, () -> storage.read(key));
            assertThrows(IOException.class, () -> storage.delete(key));
        }
        Files.createDirectory(directory.resolve(".git"));
        assertThrows(IllegalArgumentException.class, () -> new LocalPhotoStorage(directory.resolve("photos").toString()));
    }

    @Test void reportsUnwritableRoot() throws Exception {
        Path file = directory.resolve("not-a-directory"); Files.write(file, new byte[]{1});
        var storage = new LocalPhotoStorage(file.toString());
        assertThrows(IOException.class, () -> storage.store(key(), new byte[]{2}));
    }
}
