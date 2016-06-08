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
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class CorpusCreator {
    private static Log log = LogFactory.getLog(CorpusCreator.class);

    // Used as background when the input image is not large enough
    public static final Color FILL_COLOR;
    static {
        int grey = Config.getInt("tile.fillgrey");
        FILL_COLOR = new Color(grey, grey, grey);
    }
    public static final int MAX_LEVEL = Config.getInt("pyramid.maxlevel"); // 128x128
    public static final PyramidGrey23 imhotep = new PyramidGrey23(MAX_LEVEL);

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

    public PyramidGrey23 breakDownImage(URL in) throws IOException {
        // Write to 256x234 pixel grey image
        BufferedImage full = renderToFull(ImageIO.read(in), imhotep.getMaxTileLevel()); // Also does greyscale
        return renderPyramid(new UUID(in.toString()), full);
        // TODO: Store the pyramid
    }

    private PyramidGrey23 renderPyramid(UUID id, BufferedImage inImage) throws IOException {
        PyramidGrey23 pyramid = imhotep.createNew();
        pyramid.setID(id);
        final long baseSum[] = new long[2*3]; // We calculate the 2x3-level based on the full image
        for (int level = imhotep.getMaxTileLevel(); level > 0 ; level--) {
            int edge = imhotep.getTileEdge(level);
            BufferedImage scaled = getScaledImage(inImage, edge*2, edge*3);
//            System.out.println("level=" + level + ", avg=" + avg(scaled));
            int[] pixels = new int[edge*edge];
            byte[] greys = new byte[edge*edge];

            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 2; x++) {
                    if (level == 1) {
                        // Level 1 is special as we use the average of the color from the upmost level
                        // If we just scale down, the resulting pixels gets very light
                        greys[0] = (byte) (baseSum[y*2+x] / imhotep.getTileEdge(imhotep.getMaxTileLevel()) /
                                           imhotep.getTileEdge(imhotep.getMaxTileLevel()));
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
                imhotep.getTileEdge(level) * 2, imhotep.getTileEdge(level) * 3, BufferedImage.TYPE_BYTE_GRAY);
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
