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
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Util {
    private static Log log = LogFactory.getLog(Util.class);

    // Used as background when the input image is not large enough
    public static final Color FILL_COLOR;
    public static final Color DARK_GREY;
    private static final BufferedImage BLANK;

    public static int getAverageGrey(BufferedImage scaled) {
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

    static {
        int grey = Config.getInt("tile.fillgrey");
        FILL_COLOR = new Color(grey, grey, grey);
        int dark_grey = Config.getInt("tile.debuggrey");
        DARK_GREY = new Color(dark_grey, dark_grey, dark_grey);

        final int edge = Config.getInt("tile.edge");
        BLANK = new BufferedImage(edge, edge, BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = BLANK.getGraphics();
        g.setColor(FILL_COLOR);
        g.fillRect(0, 0, edge, edge);
        g.dispose();
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


    public static void show(BufferedImage... images)  { // Debugging
        try {
            JDialog dialog = new JDialog();
            dialog.setTitle("Images");
            final int width = Math.min(10, images.length);
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
        BufferedImage full = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = full.getGraphics();
        g.setColor(FILL_COLOR);
        g.fillRect(0, 0, full.getWidth(), full.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return full;
    }

    // http://stackoverflow.com/questions/3967731/how-to-improve-the-performance-of-g-drawimage-method-for-resizing-images
    public static BufferedImage scale(BufferedImage image, int width, int height) throws IOException {
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

    public static void drawBorder(BufferedImage image) {
        Graphics g = image.getGraphics();
        g.setColor(DARK_GREY);
        g.drawRect(0, 0, image.getWidth()-1, image.getHeight()-1);
        g.drawRect(1, 1, image.getWidth()-3, image.getHeight()-3);
        g.dispose();
    }

    public static BufferedImage getBlankTile() {
        return BLANK; // Bit dangerous as debug might modify this
    }
}
