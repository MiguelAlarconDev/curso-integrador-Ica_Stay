package pe.edu.utp.icastay.gallery;

import java.io.IOException;

/** Keys are provider-independent; implementations must never overwrite objects. */
public interface PhotoStorage {
    void store(String key, byte[] content) throws IOException;
    byte[] read(String key) throws IOException;
    /** Internal compensation only; no public deletion API in this increment. */
    void delete(String key) throws IOException;
}
