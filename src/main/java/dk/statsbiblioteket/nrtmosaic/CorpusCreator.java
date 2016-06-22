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
            log.info("Corpus cache already generated");
            return;
        }
        long startTime = System.nanoTime();
        cacheGenerated = true;
        final boolean overwrite = Config.getBool("corpuscreator.overwrite");
        final String sString = Config.getString("pyramid.source");
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
        Path dest = Paths.get(Config.getString("pyramid.cache"), Config.getString("tile.fill.style"));
        try {
            log.debug("Retrieving raw corpus images from " + sString);
            BufferedReader in = new BufferedReader(new InputStreamReader(source, "utf-8"));
            CorpusCreator cc = new CorpusCreator();
            String line;

            while ((line = in.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                processed++;
                UUID id = new UUID(line);
                if (!overwrite && Files.exists(Config.imhotep.getFullPath(dest, id))) {
                    log.trace("Skipping " + line + " as a pyramid already exists for it");
                    continue;
                }
                PyramidGrey23 pyramid;
                try {
                    pyramid = cc.breakDownImage(Util.resolveURL(line));
                } catch (Exception e) {
                    log.warn("Unable to create pyramid for " + line + ": " + e.getMessage());
                    continue;
                }
                pyramid.store(dest);
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
                 ", storing pyramids at " + dest);
    }

    public PyramidGrey23 breakDownImage(URL in) throws IOException {
        UUID uuid = new UUID(in.toString());

        final BufferedImage greyImage = Util.toGrey(ImageIO.read(in));
        final int averageGrey = Util.getAverageGrey(greyImage);
        final int edge = Config.imhotep.getTileEdge(Config.imhotep.getMaxTileLevel());
        BufferedImage inImage = Util.pad(greyImage,
                                         edge * Config.imhotep.getFractionWidth(),
                                         edge * Config.imhotep.getFractionHeight(),
                                         averageGrey);

        final PyramidGrey23 pyramid = Config.imhotep.createNew(uuid);
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
        log.debug("Created " + pyramid);
        return pyramid;
    }


    private BufferedImage renderToFull(BufferedImage in, int level) {
        return Util.pad(in,
                   Config.imhotep.getTileEdge(level) * Config.imhotep.getFractionWidth(),
                   Config.imhotep.getTileEdge(level) * Config.imhotep.getFractionHeight());
    }

}
