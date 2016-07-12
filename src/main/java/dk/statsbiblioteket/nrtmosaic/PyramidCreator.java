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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class PyramidCreator {
    private static Log log = LogFactory.getLog(PyramidCreator.class);
    private int pyramidCount = 0;
    public static final Util.FILL_STYLE fillStyle = Util.FILL_STYLE.valueOf(Config.getString("tile.fill.style"));

    public static void create() {
        new PyramidCreator().createPyramidsInternal();
    }

    private void createPyramidsInternal() {
        long startTime = System.nanoTime();
        final boolean overwrite = Config.getBool("corpuscreator.overwrite");
        final String sString = Config.getString("pyramid.source");
        log.info("Creating pyramids from sources in " + sString);
        InputStream source;
        try {
            source = Util.resolveURL(sString).openStream();
        } catch (IOException e) {
            throw new RuntimeException("Unable to open stream '" + sString + "'");
        }
        if (source == null) {
            log.info("No source available at " + sString);
            return;
        }
        int processed = 0;
        int created = 0;
        Path cacheRoot = Config.getCacheRoot();
        try {
            log.debug("Retrieving raw corpus images from " + sString);
            BufferedReader in = new BufferedReader(new InputStreamReader(source, "utf-8"));
            String line;

            while ((line = in.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                processed++;
                UUID id = new UUID(line);
                if (!overwrite && Files.exists(Config.imhotep.getFullPath(cacheRoot, id))) {
                    log.trace("Skipping " + line + " as a pyramid already exists for it");
                    continue;
                }
                PyramidGrey23 pyramid;
                try {
                    URL sourceURL = Util.resolveURL(line);
                    if (sourceURL == null) {
                        log.warn("Unable to resolve '" + line + "' to URL");
                        continue;
                    }
                    pyramid = breakDownImage(sourceURL);
                } catch (Exception e) {
                    log.warn("Unable to create pyramid for '" + line + "'", e);
                    continue;
                }
                pyramid.store(cacheRoot, overwrite);
                created++;
            }
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("utf-8 not supported");
        } catch (IOException e) {
            throw new RuntimeException("IOException while reading " + sString, e);
        }
        log.info("Pyramid creation finished in " + (System.nanoTime()-startTime)/1000000 + "ms." +
                 " Newly created: " + created + ", already existing: " + (processed-created) +
                 ", total images available: " + processed + ", source: " + sString +
                 ", storing pyramids at " + cacheRoot);

    }

    public PyramidGrey23 breakDownImage(URL in) throws IOException {
        UUID uuid = new UUID(in.toString());

        final BufferedImage greyImage = Util.toGrey(ImageIO.read(in));
        final int averageGrey = Util.getAverageGrey(greyImage);
        final int maxEdge = Config.imhotep.getTileEdge(Config.imhotep.getMaxTileLevel());
        BufferedImage inImage;
        switch (fillStyle) {
            case fixed:
                inImage = Util.pad(greyImage,
                                   maxEdge * Config.imhotep.getFractionWidth(),
                                   maxEdge * Config.imhotep.getFractionHeight());
                break;
            case average:
                inImage = Util.pad(greyImage,
                                   maxEdge * Config.imhotep.getFractionWidth(),
                                   maxEdge * Config.imhotep.getFractionHeight(),
                                   averageGrey);
                break;
            default:
                throw new UnsupportedOperationException("Don't know how to handle fill style " + fillStyle);
        }

        final PyramidGrey23 pyramid = Config.imhotep.createNew(uuid);
        pyramid.setAverageGrey(averageGrey);
        pyramid.setSourceSize(greyImage.getWidth(), greyImage.getHeight());
        final int fw = pyramid.getFractionWidth();
        final int fh = pyramid.getFractionHeight();
        final int maxLevel = Config.imhotep.getMaxTileLevel();
        final long baseSum[] = new long[fw*fh]; // We calculate the 2x3-level based on the full image

        for (int level = maxLevel; level > 0 ; level--) {
            int edge = Config.imhotep.getTileEdge(level);
            BufferedImage scaled = Util.scale(inImage, edge * fw, edge * fh);
//            System.out.println("level=" + level + ", avg=" + avg(scaled));
            int[] sourcePixels = new int[edge*edge];
            byte[] pData = new byte[edge*edge];
            //show(scaled);

            for (int fy = 0; fy < fh; fy++) {
                for (int fx = 0; fx < fw; fx++) {
                    if (level == 1) {
                        // Level 1 is special as we use the average of the color from the upmost level
                        // If we just scale down, the resulting pixels gets very light
                        pData[0] = (byte) (baseSum[fy*fw+fx] /
                                           Config.imhotep.getTileEdge(maxLevel) /
                                           Config.imhotep.getTileEdge(maxLevel));
                        pyramid.setData(pData, level, fx, fy);
                        continue;
                    }
                    scaled.getRaster().getPixels(fx * edge, fy * edge, edge, edge, sourcePixels);
                    if (level == maxLevel) {
//                        System.out.println("getPixels(fx*edge=" + fx * edge + ", fy*edge=" + fy * edge + ", edge=" + edge);
                        long sum = 0;
                        for (int i = 0; i < sourcePixels.length; i++) {
                            pData[i] = (byte) sourcePixels[i];
                            sum += sourcePixels[i];
                        }
                        baseSum[fy*fw+fx] = sum;
                    } else {
                        for (int i = 0; i < sourcePixels.length; i++) {
                            pData[i] = (byte) sourcePixels[i];
                        }
                    }
//                    show(pData, edge);
                    pyramid.setData(pData, level, fx, fy);
                }
            }
        }
        log.debug("Created #" + ++pyramidCount + ": " + pyramid);
        return pyramid;
    }

}
