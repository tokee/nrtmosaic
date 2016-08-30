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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class PyramidCreator {
    private static Log log = LogFactory.getLog(PyramidCreator.class);
    public static final int MIN_BUCKET_SIZE = 10;

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
        int lines;
        try {
            lines = Util.countLines(sString);
        } catch (IOException e) {
            throw new RuntimeException("Unable to determine line count for '" + sString + "'", e);
        }
        if (lines == 0) {
            throw new IllegalArgumentException("No lines in '" + sString + "'. Corpus cannot be created");
        }

        int threadCount = Math.max(1, Math.min(Config.getInt("corpuscreator.threads"), lines/MIN_BUCKET_SIZE));
        int bucketSize = lines/threadCount + 1;

        log.info("Processing " + lines + " lines in " + sString + " in " + threadCount + " threads");
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Path cacheRoot = Config.getCacheRoot();
        int start = 0;
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger created = new AtomicInteger(0);
        for (int threadID = 0 ; threadID < threadCount && start < lines; threadID++) {
            executor.submit(new PyramidListHandler(
                    sString, start, Math.min(start+bucketSize, lines), cacheRoot, overwrite, processed, created));
            start += bucketSize;
        }
        try {
            executor.awaitTermination(10, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for Pyramid creator threads to finish", e);
        }
        log.info("Pyramid creation finished in " + (System.nanoTime()-startTime)/1000000 + "ms." +
                 " Newly created: " + created + ", already existing: " + (processed.get()-created.get()) +
                 ", total images available: " + processed.get() + ", source: " + sString +
                 ", stored pyramids at " + cacheRoot);
    }

    // Planned for threading, but the real solution is a bounded executor, which seems to be non-trivial to make
    private class PyramidListHandler implements Callable<Integer> {
        private final String resources;
        private final Path cacheRoot;
        private final boolean overwrite;
        private final int start;
        private final int end;
        private final AtomicInteger processed;
        private final AtomicInteger created;

        public PyramidListHandler(String source, int start, int end, Path cacheRoot, boolean overwrite,
                                  AtomicInteger processed, AtomicInteger created) {
            this.resources = source;
            this.cacheRoot = cacheRoot;
            this.overwrite = overwrite;
            this.start = start;
            this.end = end;
            this.processed = processed;
            this.created = created;
        }

        @Override
        public Integer call() throws Exception {
            Util.processLines(resources, start, end, line -> {
                if (line == null || line.isEmpty() || line.startsWith("#")) {
                    return;
                }
                UUID id = new UUID(line);
                if (!overwrite && Files.exists(Config.imhotep.getFullPath(cacheRoot, id))) {
                    log.trace("Skipping " + line + " as a pyramid already exists for it");
                    processed.incrementAndGet();
                    return;
                }
                PyramidGrey23 pyramid;
                try {
                    URL sourceURL = Util.resolveURL(line);
                    if (sourceURL == null) {
                        log.warn("Unable to resolve '" + line + "' to URL");
                        return;
                    }
                    pyramid = breakDownImage(sourceURL);
                    pyramid.store(cacheRoot, overwrite);
                    processed.incrementAndGet();
                    created.incrementAndGet();
                } catch (Exception e) {
                    log.warn("Unable to create pyramid for '" + line + "'", e);
                }
            });
            return processed.get();
        }
    }

    public PyramidGrey23 breakDownImage(URL in) throws IOException {
        UUID uuid = new UUID(in.toString());

        final BufferedImage greyImage = Util.ensureNoMissingGrey(Util.toGrey(ImageIO.read(in)));
        final int sWidth = greyImage.getWidth();
        final int sHeight = greyImage.getHeight();
        final int averageGrey = Util.getAverageGrey(greyImage);
        final int maxLevel = Config.imhotep.getMaxTileLevel();
        final int maxEdge = Config.imhotep.getTileEdge(maxLevel);
        final int iWidth = maxEdge * Config.imhotep.getFractionWidth();
        final int iHeight = maxEdge * Config.imhotep.getFractionHeight();
        if (sWidth > iWidth || sHeight > iHeight) {
            log.warn("The source " + in + " has size " + sWidth + "x" + sHeight +
                     ", which exceeds the ideal size " + iWidth + "x" + iHeight);
        }
        final double missingPixelsFraction = 1D*sWidth*sHeight/(iWidth*iHeight);
        BufferedImage inImage = Util.pad(greyImage,
                                         maxEdge * Config.imhotep.getFractionWidth(),
                                         maxEdge * Config.imhotep.getFractionHeight(),
                                         Util.MISSING_GREY);

        final PyramidGrey23 pyramid = Config.imhotep.createNew(uuid);
        pyramid.setAverageGrey(averageGrey);
        pyramid.setMissingPixelsFraction(missingPixelsFraction);
        pyramid.setSourceSize(sWidth, sHeight);
        final int fw = pyramid.getFractionWidth();
        final int fh = pyramid.getFractionHeight();
        final long baseSum[] = new long[fw*fh]; // We calculate the 2x3-level based on the full image

        for (int level = maxLevel; level > 0 ; level--) {
            int edge = Config.imhotep.getTileEdge(level);
            // FIXME: When scaling, it is possible to arrive at MISSING_GREY: Scaling should be done without padding
            BufferedImage scaled = Util.missingAwareScale(
                    inImage, edge * fw, edge * fh, sWidth, sHeight, iWidth, iHeight);
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
