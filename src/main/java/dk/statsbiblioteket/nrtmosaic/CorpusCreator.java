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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class CorpusCreator {
    private static Log log = LogFactory.getLog(CorpusCreator.class);
    private static boolean cacheGenerated = false;

    // Used as background when the input image is not large enough
    public static final Color FILL_COLOR;
    static {
        int grey = Config.getInt("tile.fillgrey");
        FILL_COLOR = new Color(grey, grey, grey);
    }
    public static final int MAX_LEVEL = Config.getInt("pyramid.maxlevel"); // 128x128

    public static void main(String[] argsA) throws IOException {
        List<String> args = Arrays.asList(argsA);
        if (args.isEmpty()) {
            System.out.println("Usage: CorpusCreator imagefile*");
        }

        CorpusCreator cc = new CorpusCreator();
        for (String arg: args) {
            URL in = new File(arg).exists() ? new File(arg).toURI().toURL() : new URL(arg);
            cc.breakDownImage(in);
        }
    }

    public static synchronized void generateCache() {
        if (cacheGenerated) {
            log.debug("Corpus cache already generated");
            return;
        }
        cacheGenerated = true;
        final String sString = Config.getString("pyramid.source");
        InputStream source = Thread.currentThread().getContextClassLoader().getResourceAsStream(sString);
        if (source == null) {
            log.info("No source available at " + sString);
            return;
        }
        int processed = 0;
        int created = 0;
        Path dest = Paths.get(Config.getString("pyramid.cache"));
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(source, "utf-8"));
            CorpusCreator cc = new CorpusCreator();
            String line;

            while ((line = in.readLine()) != null && !line.isEmpty() && !line.startsWith("#")) {
                processed++;
                UUID id = new UUID(line);
                if (Files.exists(Config.imhotep.getFullPath(dest, id))) {
                    log.debug("Skipping " + line + " as a pyramid already exists for it");
                    continue;
                }
                PyramidGrey23 pyramid = cc.breakDownImage(Util.resolveURL(line));
                pyramid.store(dest);
                created++;
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("utf-8 not supported");
        } catch (IOException e) {
            throw new RuntimeException("IOException while reading " + sString, e);
        }
        log.info("Created " + created + "/" + processed + " pyramids from URLs in " + sString +
                 ", storing pyramids at " + dest);
    }

    public PyramidGrey23 breakDownImage(URL in) throws IOException {
        UUID uuid = new UUID(in.toString());
        // Write to 256x234 pixel grey image
        BufferedImage full = renderToFull(ImageIO.read(in), Config.imhotep.getMaxTileLevel()); // Also does greyscale
        return renderPyramid(uuid, full);
    }

    private PyramidGrey23 renderPyramid(UUID id, BufferedImage inImage) throws IOException {
        PyramidGrey23 pyramid = Config.imhotep.createNew();
        pyramid.setID(id);
        final long baseSum[] = new long[2*3]; // We calculate the 2x3-level based on the full image
        for (int level = Config.imhotep.getMaxTileLevel(); level > 0 ; level--) {
            int edge = Config.imhotep.getTileEdge(level);
            BufferedImage scaled = getScaledImage(inImage, edge*2, edge*3);
//            System.out.println("level=" + level + ", avg=" + avg(scaled));
            int[] pixels = new int[edge*edge];
            byte[] greys = new byte[edge*edge];

            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 2; x++) {
                    if (level == 1) {
                        // Level 1 is special as we use the average of the color from the upmost level
                        // If we just scale down, the resulting pixels gets very light
                        greys[0] = (byte) (baseSum[y*2+x] / Config.imhotep.getTileEdge(Config.imhotep.getMaxTileLevel()) /
                                           Config.imhotep.getTileEdge(Config.imhotep.getMaxTileLevel()));
                        pyramid.setData(greys, level, x, y);
                        continue;
                    }
                    scaled.getRaster().getPixels(x * edge, y * edge, edge, edge, pixels);
                    if (scaled == inImage) { // Top-level
                        long sum = 0;
                        for (int i = 0; i < pixels.length; i++) {
                            greys[i] |= (byte) pixels[i];
                            sum += pixels[i];
                        }
                        baseSum[y*2+x] = sum;
                    } else {
                        for (int i = 0; i < pixels.length; i++) {
                            greys[i] |= (byte) pixels[i];
                        }
                    }
                    pyramid.setData(greys, level, x, y);
                }
            }
        }
        log.debug("Created " + pyramid);
        return pyramid;
    }

    private int avg(BufferedImage scaled) {
        long sum = 0 ;
        int w = scaled.getWidth();
        int h = scaled.getHeight();
        int[] pixels = new int[w*h];
        scaled.getRaster().getPixels(0, 0, w, h, pixels);
        for (int i = 0 ; i < w*h ; i++) {
            sum += pixels[i];
        }
        return (int) (sum / (w * h));
    }

    private BufferedImage renderToFull(BufferedImage in, int level) {
        BufferedImage full = new BufferedImage(
                Config.imhotep.getTileEdge(level) * 2, Config.imhotep.getTileEdge(level) * 3, BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = full.getGraphics();
        g.setColor(FILL_COLOR);
        g.fillRect(0, 0, full.getWidth(), full.getHeight());
        g.drawImage(in, 0, 0, null);
        g.dispose();
        return full;
    }


    // http://stackoverflow.com/questions/3967731/how-to-improve-the-performance-of-g-drawimage-method-for-resizing-images
    public static BufferedImage getScaledImage(BufferedImage image, int width, int height) throws IOException {
        int imageWidth  = image.getWidth();
        int imageHeight = image.getHeight();
        if (width == imageWidth && height == imageHeight) {
            return image;
        }

        double scaleX = (double)width/imageWidth;
        double scaleY = (double)height/imageHeight;
        AffineTransform scaleTransform = AffineTransform.getScaleInstance(scaleX, scaleY);
        AffineTransformOp bilinearScaleOp = new AffineTransformOp(scaleTransform, AffineTransformOp.TYPE_BILINEAR);

        return bilinearScaleOp.filter(image, new BufferedImage(width, height, image.getType()));
    }
}
