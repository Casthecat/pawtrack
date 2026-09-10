package com.pawtrack.backend.cat.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;

@Component
public class CatImageStorage {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private final Path configuredRoot;

    public CatImageStorage(@Value("${upload.path:uploads/}") String uploadPath) {
        configuredRoot = Path.of(uploadPath).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) throw invalid("A JPEG or PNG image is required.");
        if (file.getSize() > MAX_BYTES) throw tooLarge();
        String contentType = file.getContentType();
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType))
            throw invalid("Only JPEG and PNG portraits are accepted.");

        BufferedImage image;
        String format;
        try (var source = file.getInputStream()) {
            byte[] bytes = source.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw tooLarge();
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw invalid("The file is not a valid JPEG or PNG image.");
                var reader = readers.next();
                try {
                    format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    if (!(format.equals("jpeg") || format.equals("png")) || !contentType.equals("image/" + format))
                        throw invalid("Image content must match its JPEG or PNG content type.");
                    reader.setInput(input, true, true);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 16_000_000)
                        throw invalid("Portraits must be at most 4096 pixels per side and 16 megapixels.");
                    image = reader.read(0);
                    if (image == null) throw invalid("The image could not be decoded.");
                } finally { reader.dispose(); }
            }
        } catch (IOException ex) { throw invalid("The image could not be decoded. Use a valid JPEG or PNG."); }

        // Re-encode decoded pixels: do not publish attacker bytes, metadata or appended HTML.
        Path target = null;
        boolean created = false;
        try {
            Files.createDirectories(configuredRoot);
            Path root = configuredRoot.toRealPath();
            String filename = UUID.randomUUID() + (format.equals("jpeg") ? ".jpg" : ".png");
            target = root.resolve(filename).normalize();
            if (!target.getParent().equals(root)) throw invalid("Invalid image destination.");
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                created = true;
                if (!ImageIO.write(image, format, output)) throw new IOException("Image writer unavailable");
            }
            // Public URL is independent of the configured physical directory.
            return "uploads/" + filename;
        } catch (IOException ex) {
            if (created) try { Files.deleteIfExists(target); } catch (IOException ignored) { /* Keep original error. */ }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store the portrait.", ex);
        }
    }

    private static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException tooLarge() { return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Portraits must be 5 MiB or smaller."); }
}
