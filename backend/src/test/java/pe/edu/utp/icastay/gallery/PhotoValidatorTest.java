package pe.edu.utp.icastay.gallery;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class PhotoValidatorTest {
    static final byte[] WEBP = Base64.getDecoder().decode("UklGRjgAAABXRUJQVlA4ICwAAACQAQCdASoCAAIAAgA0JaACdLoAA5gA/vmTb/+QH/+QH/+QH/8gP+IXeyAwAA==");
    private final PhotoValidator validator = new PhotoValidator(System.getenv().getOrDefault("PHOTO_WEBP_DECODER", "ffmpeg"));

    static byte[] image(String type, int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), type, output));
        return output.toByteArray();
    }

    @ParameterizedTest @ValueSource(strings = {"jpeg", "png"})
    void detectsAndDecodesContentIgnoringNameAndDeclaredMime(String type) throws Exception {
        var result = validator.validate(new MockMultipartFile("file", "../../evil.html", "text/html", image(type, 3, 2)));
        assertEquals("image/" + type, result.mimeType()); assertEquals(3, result.width()); assertEquals(2, result.height());
    }

    @Test void decodesRealWebp() {
        var result = validator.validate(new MockMultipartFile("file", "wrong.jpg", "image/jpeg", WEBP));
        assertEquals("image/webp", result.mimeType()); assertEquals(2, result.width()); assertEquals(2, result.height());
    }

    @Test void rejectsOversizeAndEmpty() {
        assertEquals(413, rejected(new byte[PhotoValidator.MAX_BYTES + 1]));
        assertEquals(400, rejected(new byte[0]));
    }

    @Test void rejectsFakeAndTruncatedImages() throws Exception {
        assertEquals(400, rejected("<script>alert(1)</script>".getBytes()));
        assertEquals(400, rejected(java.util.Arrays.copyOf(image("png", 2, 2), 30)));
        assertEquals(400, rejected(java.util.Arrays.copyOf(image("jpeg", 2, 2), 30)));
        assertEquals(400, rejected(java.util.Arrays.copyOf(WEBP, 30)));
        byte[] corrupt = java.util.Arrays.copyOf(WEBP, 30);
        corrupt[4] = 22; // Consistent RIFF/chunk lengths, but no compressed data after the frame header.
        corrupt[16] = 10;
        assertEquals(400, rejected(corrupt)); // Valid RIFF/dimensions, invalid compressed payload.
    }

    @Test void rejectsOversizedDimensionsBeforeDecoding() throws Exception {
        assertEquals(400, rejected(image("png", 8193, 1)));
        byte[] huge = WEBP.clone(); huge[26] = (byte) 0xff; huge[27] = 0x3f;
        assertEquals(400, rejected(huge));
    }

    @Test void failsClosedIfWebpDecoderIsUnavailable() {
        var error = assertThrows(ResponseStatusException.class, () -> new PhotoValidator("nonexistent-icastay-decoder")
                .validate(new MockMultipartFile("file", WEBP)));
        assertEquals(503, error.getStatusCode().value());
    }

    private int rejected(byte[] bytes) {
        return assertThrows(ResponseStatusException.class, () -> validator.validate(new MockMultipartFile("file", "photo.webp", "image/webp", bytes)))
                .getStatusCode().value();
    }
}
