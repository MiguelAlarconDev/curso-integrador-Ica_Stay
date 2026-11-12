package pe.edu.utp.icastay.gallery;

import java.time.Instant;
import java.util.UUID;

public record PhotoView(UUID id, String url, String mimeType, long sizeBytes, int width,
        int height, String altText, int position, boolean isCover, Instant createdAt) {}
