package pe.edu.utp.icastay.gallery;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PhotoValidator {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private final String ffmpeg;
    private final Semaphore decoders = new Semaphore(2);
    public record Validated(byte[] bytes, String mimeType, int width, int height) {}

    public PhotoValidator(@Value("${icastay.photos.webp-decoder:ffmpeg}") String ffmpeg) { this.ffmpeg = ffmpeg; }

    public Validated validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("Fotografia vacia");
        if (file.getSize() > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Maximo 5 MiB por foto");
        if (!decoders.tryAcquire()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Procesador de imagenes ocupado");
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
            if (tag(bytes, 0, "RIFF") && tag(bytes, 8, "WEBP")) return webp(bytes);
            try (var imageInput = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) throw invalid("Formato no admitido");
                var reader = readers.next();
                try {
                    String format = reader.getFormatName();
                    String mime = switch (format.toLowerCase(java.util.Locale.ROOT)) {
                        case "jpeg", "jpg" -> "image/jpeg";
                        case "png" -> "image/png";
                        default -> throw invalid("Solo JPG, PNG y WebP");
                    };
                    reader.setInput(imageInput);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    dimensions(width, height);
                    boolean[] warning = {false};
                    reader.addIIOReadWarningListener((source, message) -> warning[0] = true);
                    var decoded = reader.read(0);
                    if (decoded == null || warning[0]) throw invalid("Imagen corrupta");
                    decoded.flush();
                    return new Validated(bytes, mime, width, height);
                } finally { reader.dispose(); }
            }
        } catch (IOException error) { throw invalid("No se pudo decodificar la imagen"); }
        finally { decoders.release(); }
    }

    private Validated webp(byte[] bytes) throws IOException {
        if (bytes.length < 20 || unsigned(bytes, 4, 4) + 8 != bytes.length) throw invalid("WebP truncado");
        int width = 0, height = 0, payloads = 0;
        int canvasWidth = 0, canvasHeight = 0;
        for (int offset = 12; offset < bytes.length;) {
            if (offset + 8 > bytes.length) throw invalid("WebP truncado");
            long size = unsigned(bytes, offset + 4, 4);
            long end = offset + 8L + size + (size & 1);
            if (end > bytes.length) throw invalid("WebP truncado");
            int data = offset + 8;
            if (tag(bytes, offset, "ANIM") || tag(bytes, offset, "ANMF")) throw invalid("WebP animado no admitido");
            if (tag(bytes, offset, "VP8X")) {
                if (size != 10 || (bytes[data] & 2) != 0) throw invalid("WebP animado o invalido");
                canvasWidth = 1 + (int) unsigned(bytes, data + 4, 3);
                canvasHeight = 1 + (int) unsigned(bytes, data + 7, 3);
                dimensions(canvasWidth, canvasHeight);
            } else if (tag(bytes, offset, "VP8 ")) {
                if (size < 10 || (bytes[data + 3] & 255) != 0x9d || (bytes[data + 4] & 255) != 1 || (bytes[data + 5] & 255) != 0x2a) throw invalid("WebP invalido");
                width = (int) unsigned(bytes, data + 6, 2) & 0x3fff;
                height = (int) unsigned(bytes, data + 8, 2) & 0x3fff;
                payloads++;
            } else if (tag(bytes, offset, "VP8L")) {
                if (size < 5 || (bytes[data] & 255) != 0x2f) throw invalid("WebP invalido");
                long bits = unsigned(bytes, data + 1, 4);
                width = 1 + (int) (bits & 0x3fff);
                height = 1 + (int) ((bits >> 14) & 0x3fff);
                payloads++;
            }
            offset = (int) end;
        }
        dimensions(width, height);
        if (payloads != 1 || (canvasWidth != 0 && (canvasWidth != width || canvasHeight != height))) throw invalid("WebP invalido");
        Path temporary = Files.createTempFile("icastay-webp-", ".webp");
        Process process = null;
        try {
            Files.write(temporary, bytes);
            try {
                process = new ProcessBuilder(ffmpeg, "-nostdin", "-v", "error", "-xerror", "-err_detect", "explode", "-threads", "1",
                        "-protocol_whitelist", "file,pipe", "-f", "webp_pipe", "-i", temporary.toString(),
                        "-frames:v", "1", "-f", "null", "-")
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            } catch (IOException unavailable) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Decodificador WebP no disponible"); }
            if (!process.waitFor(10, TimeUnit.SECONDS)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Tiempo de decodificacion agotado");
            if (process.exitValue() != 0) throw invalid("WebP corrupto");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                try { process.waitFor(2, TimeUnit.SECONDS); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
            Files.deleteIfExists(temporary);
        }
        return new Validated(bytes, "image/webp", width, height);
    }

    private static boolean tag(byte[] bytes, int offset, String value) {
        return offset + value.length() <= bytes.length && new String(bytes, offset, value.length(), StandardCharsets.US_ASCII).equals(value);
    }
    private static long unsigned(byte[] bytes, int offset, int length) {
        long value = 0;
        for (int i = 0; i < length; i++) value |= (long) (bytes[offset + i] & 255) << (8 * i);
        return value;
    }
    private static void dimensions(int width, int height) {
        if (width <= 0 || height <= 0 || width > 8192 || height > 8192 || (long) width * height > 16_000_000) throw invalid("Maximo 8192 px por lado y 16 megapixeles");
    }
    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
