package com.pawtrack.backend.support;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

public final class TestImages {
    private TestImages() {}
    public static byte[] image(String format) { return image(format, 2, 2); }
    public static byte[] image(String format, int width, int height) {
        try {
            var output = new ByteArrayOutputStream();
            var pixels = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            pixels.setRGB(0, 0, 0xff669944);
            if (!ImageIO.write(pixels, format, output)) throw new IllegalArgumentException(format);
            return output.toByteArray();
        } catch (IOException ex) { throw new UncheckedIOException(ex); }
    }
}
