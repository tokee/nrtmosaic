/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package dk.statsbiblioteket.nrtmosaic;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

import javax.swing.*;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.stream.Stream;

public class Util {
    private static Log log = LogFactory.getLog(Util.class);

    // Used as background when the input image is not large enough
    public static final Color FILL_COLOR;
    public static final int FILL_COLOR_INT;
    public static final Color DARK_GREY;
    private static final BufferedImage DEFAULT_BLANK;
    public static final int MISSING_GREY;
    public static final int MISSING_REPLACEMENT;

    public static void deleteFolder(Path lastRoot) {
        try {
            Files.walkFileTree(lastRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("IOException deleting " + lastRoot, e);
        }
    }

    public static Stream<Path> wrappedList(Path folder) {
        try {
            // Recursive traversal with open streams racks up the open file handle count, so we need to close ASAP
            return avoidLaziness(Files.list(folder));
        } catch (IOException e) {
            throw new RuntimeException("IOException iterating sub-folders of " + folder, e);
        }
    }

    private static Stream<Path> avoidLaziness(Stream<Path> lazy) {
        java.util.List<Path> resolved = new ArrayList<>();
        lazy.forEach(resolved::add);
        lazy.close(); // Why do we need this? Shouldn't the Stream auto-close upon completion?
        return resolved.stream();
    }

    /**
     * Any pixel with the value {@link #MISSING_GREY} will get the value {link #MISSING_REPLACEMENT}.
     */
    public static BufferedImage ensureNoMissingGrey(BufferedImage image) {
        final int w = image.getWidth();
        final int h = image.getHeight();
        int[] pixels = new int[w*h];
        image.getRaster().getPixels(0, 0, w, h, pixels);
        boolean someMissing = false;
        for (int i = 0 ; i < w*h ; i++) {
            if (pixels[i] == MISSING_GREY) {
                someMissing = true;
                pixels[i] = MISSING_REPLACEMENT;
            }
        }
        if (someMissing) {
            image.getRaster().setPixels(0, 0, w, h, pixels);
        }
        return image;
    }

    public enum FILL_STYLE {
        fixed,  // Same grey for all tiles
        average,// Average for existing pixels for the full source image
        dynamic // Custom for each Pyramid reference, optimizing average grey to match the wanted grey
    }
    public static final FILL_STYLE DEFAULT_FILL_STYLE;
    public static final int EDGE;

    public static int getAverageGrey(BufferedImage image) {
        return getAverageGrey(image, 0, 0, image.getWidth(), image.getHeight());
    }

    public static int getAverageGrey(BufferedImage image, int left, int top, int width, int height) {
        long sum = 0 ;
        int[] pixels = new int[width*height];
        image.getRaster().getPixels(0, 0, width, height, pixels);
        for (int i = 0 ; i < width*height ; i++) {
            sum += pixels[i];
        }
        return (int) (sum / (width * height));
    }

    public static BufferedImage toGrey(BufferedImage image) {
        return toGrey(image, false);
    }
    public static BufferedImage toGrey(BufferedImage image, boolean force) {
        if (!force && image.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY) {
            log.trace("toGrey: Image already grey, returning unmodified");
            return image;
        }
        BufferedImage grey = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = grey.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return grey;
    }

    static {
        DEFAULT_FILL_STYLE = FILL_STYLE.valueOf(Config.getString("tile.fill.style"));
        FILL_COLOR_INT = Config.getInt("tile.fill.grey");
        FILL_COLOR = new Color(FILL_COLOR_INT, FILL_COLOR_INT, FILL_COLOR_INT);
        int dark_grey = Config.getInt("tile.debuggrey");
        DARK_GREY = new Color(dark_grey, dark_grey, dark_grey);

        EDGE = Config.getInt("tile.edge");
        DEFAULT_BLANK = createBlank(EDGE, EDGE, FILL_COLOR_INT);
        MISSING_GREY = FILL_COLOR_INT;
        MISSING_REPLACEMENT = MISSING_GREY == 255 ? 254 : MISSING_GREY+1;
    }

    private static BufferedImage createBlank(int width, int height, int fillGrey) {
        Color grey = new Color(fillGrey, fillGrey, fillGrey);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = image.getGraphics();
        g.setColor(grey);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    // Tries local file, classloader and URL in that order
    public static URL resolveURL(String resource) {
        try {
            Path file = Paths.get(resource);
            if (Files.exists(file)) {
                return file.toUri().toURL();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Exception resolving '" + resource + "' as file", e);
        }

        URL url = Thread.currentThread().getContextClassLoader().getResource(resource);
        if (url != null) {
            return url;
        }

        try {
            return new URL(resource);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Exception resolving '" + resource + "' as URL", e);
        }
    }

    public static void show(byte[] pData, int edge) { // Debug
        BufferedImage image = new BufferedImage(edge, edge, BufferedImage.TYPE_BYTE_GRAY);
        int[] data = new int[pData.length];
        for (int i = 0 ; i < pData.length ; i++) {
            data[i] = 0xFF & pData[i];
        }
        image.getRaster().setPixels(0, 0, edge, edge, data);
        show(image);
    }


    public static void show(java.util.List<BufferedImage> images) { // Debugging
        show(images.toArray(new BufferedImage[images.size()]));
    }
    public static void show(BufferedImage... images)  { // Debugging
        try {
            JDialog dialog = new JDialog();
            dialog.setTitle("Images");
            final int width = Math.min(6, images.length);
            final int height = (int) Math.ceil(1.0 * images.length / width);
            dialog.getContentPane().setLayout(new GridLayout(height, width));
            for (BufferedImage image: images) {
                dialog.getContentPane().add(new JLabel(new ImageIcon(image)));
            }
            dialog.pack();
            dialog.setVisible(true);
            dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            Thread.sleep(30000); // TODO: Add code to wait for window close
        } catch (Exception e) {
            throw new RuntimeException("Just debugging", e);
        }
    }

    public static BufferedImage pad(BufferedImage image, int width, int height) {
        return pad(image, width, height, FILL_COLOR.getRed());
    }
    public static BufferedImage pad(BufferedImage image, int width, int height, int fillGrey) {
        final Color fill = new Color(fillGrey, fillGrey, fillGrey);
        if (image.getWidth() == width && image.getHeight() == height &&
            image.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY) {
            log.trace("pad: Image already grey and at size " + width + "x" + height + ", returning unmodified");
            return image;
        }
        BufferedImage full = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = full.getGraphics();
        g.setColor(fill);
        g.fillRect(0, 0, full.getWidth(), full.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return full;
    }

    /**
     * Scales the image to the wanted width and height, ensuring that no pixels are {@link #MISSING_GREY} and that the
     * missing pixels (those inside the scaled sourcewidth and sourceHeight) are all set to MISSING_GREY.
     */
    public static BufferedImage missingAwareScale(
            BufferedImage inImage, int wantedWidth, int wantedHeight, int sourceWidth, int sourceHeight,
            int idealWidth, int idealHeight) {
        BufferedImage outImage = scale(inImage, wantedWidth, wantedHeight);
        int missingLeft = (int) (1D*sourceWidth/idealWidth*wantedWidth);
        if (missingLeft < 1) {
            missingLeft = 1;
        }
        int missingTop = (int) (1D*sourceHeight/idealHeight*wantedHeight);
        if (missingTop < 1) {
            missingTop = 1;
        }
        int[] pixels = new int[wantedWidth*wantedHeight];
        outImage.getRaster().getPixels(0, 0, wantedWidth, wantedHeight, pixels);

        for (int y = 0 ; y < missingTop ; y++) {
            for (int x = 0 ; x < missingLeft ; x++) {
                if (pixels[y*wantedWidth+x] == MISSING_GREY) {
                    pixels[y*wantedWidth+x] = MISSING_REPLACEMENT;
                }
            }
            for (int x = missingLeft ; x < wantedWidth ; x++) {
                pixels[y*wantedWidth+x] = MISSING_GREY;
            }
        }
        for (int y = missingTop ; y < wantedHeight ; y++) {
            for (int x = 0 ; x < wantedWidth ; x++) {
                pixels[y*wantedWidth+x] = MISSING_GREY;
            }
        }

        outImage.getRaster().setPixels(0, 0, wantedWidth, wantedHeight, pixels);
        return outImage;
    }


    // http://stackoverflow.com/questions/3967731/how-to-improve-the-performance-of-g-drawimage-method-for-resizing-images
    public static BufferedImage scale(BufferedImage image, int width, int height) {
        int imageWidth  = image.getWidth();
        int imageHeight = image.getHeight();
        if (width == imageWidth && height == imageHeight) {
            return image;
        }

        double scaleX = (double)width/imageWidth;
        double scaleY = (double)height/imageHeight;
        AffineTransform scaleTransform = AffineTransform.getScaleInstance(scaleX, scaleY);
        AffineTransformOp bilinearScaleOp = new AffineTransformOp(scaleTransform, AffineTransformOp.TYPE_BICUBIC);

        return bilinearScaleOp.filter(image, new BufferedImage(width, height, image.getType()));
    }

    public static void drawBorder(BufferedImage image) {
        Graphics g = image.getGraphics();
        g.setColor(DARK_GREY);
        g.drawRect(0, 0, image.getWidth()-1, image.getHeight()-1);
        g.drawRect(1, 1, image.getWidth()-3, image.getHeight()-3);
        g.dispose();
    }

    public static BufferedImage getBlankTile() {
        return DEFAULT_BLANK; // Bit dangerous as debug might modify this BufferedImage
    }
    public static BufferedImage getBlankTile(int fillGrey) {
        return FILL_COLOR_INT == fillGrey ? DEFAULT_BLANK : createBlank(EDGE, EDGE, fillGrey);
    }

    public static String fetchString(URL url) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
        try {
            String inputLine;

            while ((inputLine = in.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(inputLine);
            }
        } finally {
            in.close();
        }
        return sb.toString();
    }
}
