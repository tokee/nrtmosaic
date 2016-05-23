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
import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class CorpusCreator {
    private static Log log = LogFactory.getLog(CorpusCreator.class);

    public static void main(String[] argsA) {
        List<String> args = Arrays.asList(argsA);
        if (args.isEmpty()) {
            System.out.println("Usage: CorpusCreator imagefile*");
        }

        CorpusCreator cc = new CorpusCreator()
        for (String arg: args) {
            File in = new File(arg);
            if (!in.exists()) {
                System.err.println("FileNotFound: '" + in + "'");
            } else {
                cc.breakDownImage(in);
            }
        }
    }

    public void breakDownImage(File in) throws IOException {
        BufferedImage inImage = ImageIO.read(in);

        // Greyscale the image
        BufferedImage grey = new BufferedImage(inImage.getWidth(), inImage.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = grey.getGraphics();
        g.drawImage(inImage, 0, 0, null);
        g.dispose();
        inImage = null; // Don't need original anymore

        // Initially scale the image
        double scaleFactor = 256D/grey.getWidth();
        BufferedImage scaledImage = getScaledImage(
                grey, (int)(grey.getWidth()*scaleFactor), (int)(grey.getHeight()*scaleFactor));
        grey = null; // Don't need the grey anymore

        int level = 8;
        PyramidGrey23 pyramid = new PyramidGrey23(level); // 128x128
        while (true) {
            int tileEdge = (int) Math.pow(2, level-1);
            int[] pixels = new int[tileEdge*tileEdge];
            scaledImage.getRGB(0, 0, tileEdge*2, )
        }


        // No colors
        ImageFilter filter = new GrayFilter(true, 50);
        ImageProducer producer = new FilteredImageSource(inImage.getSource(), filter);
        Image grey = Toolkit.getDefaultToolkit().createImage(producer);

        // Image expected to be max 256 pixels in width
        double scaleFactor = 256D/inImage.getWidth();
        Image scaled256 = grey.getScaledInstance(
                (int)(inImage.getWidth()*scaleFactor), (int)(inImage.getHeight()*scaleFactor), Image.SCALE_DEFAULT);
        final byte[] pixels = ((DataBufferByte) scaled256.getRaster()
            .getDataBuffer()).getData();

    }
    // http://stackoverflow.com/questions/3967731/how-to-improve-the-performance-of-g-drawimage-method-for-resizing-images
    public static BufferedImage getScaledImage(BufferedImage image, int width, int height) throws IOException {
    int imageWidth  = image.getWidth();
    int imageHeight = image.getHeight();

    double scaleX = (double)width/imageWidth;
    double scaleY = (double)height/imageHeight;
    AffineTransform scaleTransform = AffineTransform.getScaleInstance(scaleX, scaleY);
    AffineTransformOp bilinearScaleOp = new AffineTransformOp(scaleTransform, AffineTransformOp.TYPE_BILINEAR);

    return bilinearScaleOp.filter(
        image,
        new BufferedImage(width, height, image.getType()));
}
}
