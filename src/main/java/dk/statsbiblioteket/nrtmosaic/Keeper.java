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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Keeps track of the Pyramids.
 */
public class Keeper {
    private static Log log = LogFactory.getLog(Keeper.class);

    private final int bucketSize; // Should be a power of two
    private final int bucketCount; // 256/bucketSize
    private final List<List<PyramidGrey23>> pyramidsTop;
    private final List<List<PyramidGrey23>> pyramidsBottom;
    private final Map<UUID, PyramidGrey23> pyramids;


    public Keeper() {
        this(Config.getCacheRoot());
    }

    // /tmp/pyramid_test1631652512768907712/ 02/ 82/ 02823b5f223a41249913985cb5ad815f.dat
    public Keeper(Path root) {
        long startTime = System.nanoTime();
        bucketSize = Config.getInt("pyramid.bucketsize");
        bucketCount = 256%bucketSize != 0 ? 256/bucketSize+1 : 256/bucketSize;
        pyramidsTop = new ArrayList<>(bucketCount);
        pyramidsBottom = new ArrayList<>(bucketCount);
        pyramids = new HashMap<>();
        {
            for (int i = 0 ; i < bucketCount ; i++) {
                pyramidsTop.add(new ArrayList<>());
                pyramidsBottom.add(new ArrayList<>());
            }
        }

        loadFromConcatenations(root);
        //loadFromIndividualFiles(root);
        log.info("Finished loading " + size() + " pyramids into buckets " + listBuckets() + " in " +
                 (System.nanoTime() - startTime) / 1000000 + "ms");
    }

    private void loadFromConcatenations(Path root) {
        Path concatRoot = root.resolve("concatenated");
        if (!Files.exists(concatRoot)) {
            throw new RuntimeException("The expected concatenation cache did not exist at " + concatRoot);
        }
        int i = 0;
        AtomicLong pixels = new AtomicLong(0);
        ExecutorService executor = Executors.newFixedThreadPool(Config.getInt("keeper.mapping.threads"));
        log.info(String.format("Mapping concatenated pyramid data from '%s' into Pyramids, using %d threads",
                               concatRoot.toString(), Config.getInt("keeper.mapping.threads")));
        while (Files.exists(concatRoot.resolve(i + ".dat"))) {
            Path concatFile = concatRoot.resolve(i++ + ".dat");
            if (!Files.exists(concatFile)) {
                log.warn("Expected concatenation file at " + concatFile);
                continue;
            }
            long concatSize;
            try {
                concatSize = Files.size(concatFile);
            } catch (IOException e) {
                log.warn("Unable to determine size of concatenation file " + concatFile, e);
                continue;
            }
            if (concatSize == 0) {
                log.trace("Concatenation file of size 0 was skipped: " + concatFile);
                continue;
            }
            if (concatSize > Integer.MAX_VALUE) {
                throw new UnsupportedOperationException(
                        "Sorry, but " + concatFile + " is > 2GB, which is not currently supported");
            }

            MappedByteBuffer mapped;
            try {
                mapped = new RandomAccessFile(concatFile.toFile().getCanonicalFile(), "r").getChannel().
                        map(FileChannel.MapMode.READ_ONLY, 0, concatSize);
            } catch (IOException e) {
                throw new RuntimeException("Unable to map concatenated pyramids file " + concatFile, e);
            }
            executor.submit(new PyramidMapper(mapped, concatFile, concatSize, pixels));
        }
        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            log.error("Waited more than 1 day for the mapping threads to finish. Giving up.");
            throw new RuntimeException("Unable to finish mapping as it took more than 1 day", e);
        }
        sortPyramids();
        long scale = (long) Math.pow(2, Config.getInt("prime.lastbasiclevel") - Config.getInt("prime.firstbasiclevel"));
        long backingPixels = pixels.get() * scale*scale;
        log.info(String.format(
                Locale.ENGLISH,
                "Mapped %d pyramids in total. Source size = %,d pixels. Backing size (approximate) = %,d pixels",
                pyramids.size(), pixels.get(), backingPixels));
    }

    private class PyramidMapper implements Callable<Long> {
        private final MappedByteBuffer mapped;
        private final Path concatFile;
        private final long concatSize;
        private final AtomicLong pixels;

        public PyramidMapper(MappedByteBuffer mapped, Path concatFile, long concatSize, AtomicLong pixels) {
            this.mapped = mapped;
            this.concatFile = concatFile;
            this.concatSize = concatSize;
            this.pixels = pixels;
        }

        @Override
        public Long call() throws Exception {
            int offset = 0;
            long mapCount = 0;
            byte[] bulkGetBuffer = Config.imhotep.createBulkGetBuffer();
            while (offset < concatSize) {
                pixels.addAndGet(addPyramid(Config.imhotep.createNew(mapped, offset, bulkGetBuffer)));
                offset += Config.imhotep.getBytecount();
                mapCount++;
            }
            log.info("Mapped " + mapCount + " pyramids from " + concatFile);
            return mapCount;
        }
    }

    private void loadFromIndividualFiles(Path root) {
        log.debug("Loading pyramids from " + root);
        Util.wrappedList(root).
                filter(sub1 -> Files.isDirectory(sub1) && sub1.getFileName().toString().length() == 2).
                forEach(sub1 -> Util.wrappedList(sub1).
                        filter(sub2 -> Files.isDirectory(sub2) && sub2.getFileName().toString().length() == 2).
                                            forEach(sub2 -> Util.wrappedList(sub2).
                                                    filter(dat -> Files.isRegularFile(dat) && dat.toString().endsWith(".dat")).
                                                                        forEach(this::addPyramid)));
        sortPyramids();
    }

    private void sortPyramids() {
        // TODO: The threaded builder seems to cause collisions. Maybe sync on adds to the two pyramid maps?
//        sanityCheck("pyramidsTop", pyramidsTop);
//        sanityCheck("pyramidsBottom", pyramidsBottom);
        for (int i = 0 ; i < bucketCount ; i++) {
            // TODO: Extend the sort to use primaries first
            Collections.sort(pyramidsTop.get(i), (o1, o2) -> o2.getTopSecondary()-o1.getTopSecondary());
            Collections.sort(pyramidsBottom.get(i), (o1, o2) -> o2.getBottomSecondary()-o1.getBottomSecondary());
        }
        log.info("Pyramids sorted into buckets " + listBuckets());
        collapsePyramids();
    }

    private void sanityCheck(String designation, List<List<PyramidGrey23>> pyramids) {
        for (int i = 0 ; i < bucketCount ; i++) {
            List<PyramidGrey23> pl = pyramids.get(i);
            if (pl == null) {
                log.error("Sanity checking: The " + designation + " at index " + i + " was null");
            } else {
                for (int j = 0; j < pl.size(); j++) {
                    if (pl.get(j) == null) {
                        log.error("The " + designation + " entry at " + i + ", " + j + " was null");
                    }
                }
            }
        }
    }

    private void collapsePyramids() {
        int up = getAbs("pyramid.buckets.collapse.up");
        int down = getAbs("pyramid.buckets.collapse.down");
        if (up == 0 && down == 0) {
            log.info("No bucket collapsing");
            return;
        }
        log.debug("Collapsing " + pyramids.size() + " pyramids with bottom-up=" + up + ", top-down=" + down);
        collapse(pyramidsTop, up, 0, 1);
        collapse(pyramidsBottom, up, 0, 1);
        collapse(pyramidsTop, down, pyramidsTop.size()-1, -1);
        collapse(pyramidsBottom, down, pyramidsBottom.size()-1, -1);
        log.debug("Collapsed " + pyramids.size() + " pyramids with bottom-up=" + up + ", top-down=" + down +
                  " into buckets " + listBuckets());
    }

    private void collapse(List<List<PyramidGrey23>> pyramids, int needed, int start, int delta) {
        List<PyramidGrey23> collected = new ArrayList<>();
        for (int bucket = start ; bucket < pyramids.size() && bucket >= 0 ; bucket+=delta) {
            collected.addAll(pyramids.get(bucket));
            if (collected.size() >= needed) {
                pyramids.set(bucket, collected);
                return;
            }
            pyramids.set(bucket, Collections.emptyList());
        }
    }

    private int getAbs(String key) {
        String s = Config.getString(key);
        return Math.min(pyramids.size(),
                        s.endsWith("%") ?
                                (int) (Double.valueOf(s.substring(0, s.length() - 1)) * 0.01 * pyramids.size()) :
                                Integer.valueOf(s));
    }

    public int size() {
        int size = 0;
        for (List<PyramidGrey23> pList: pyramidsTop) {
            size += pList.size();
        }
        return size;
    }

    public PyramidGrey23 getClosestTop(int primary, int secondary, Random random) {
        List<PyramidGrey23> candidates = getClosest(pyramidsTop, primary, random);
        return candidates.get(random.nextInt(candidates.size())); // TODO: Should prioritize secondary
    }
    public PyramidGrey23 getClosestBottom(int primary, int secondary, Random random) {
        List<PyramidGrey23> candidates = getClosest(pyramidsBottom, primary, random);
        return candidates.get(random.nextInt(candidates.size())); // TODO: Should prioritize secondary
    }

    // Attempts to extract UUID from origin and use cached fill color
    public int getFillGrey(String origin, Integer dynamicGrey) {
        switch (Util.DEFAULT_FILL_STYLE) {
            case fixed:
                return Util.FILL_COLOR_INT;
            case average:
                try {
                    return getFillGrey(new UUID(origin));
                } catch (IllegalArgumentException e) {
                    log.warn("Unable to extract UUID from '" + origin + "'. Using default grey " + Util.FILL_COLOR_INT);
                    return Util.FILL_COLOR_INT;
                }
            case dynamic:
                return dynamicGrey == null ? Util.FILL_COLOR_INT : dynamicGrey;
            default: throw new UnsupportedOperationException(
                    "The fill style '" + Util.DEFAULT_FILL_STYLE + "' is not supported yet");
        }
    }
    /**
     * Depending on property "tile.fill.style", this either returns the average grey for th pyramid or the default fill.
     * @return average grey for the pyramid or default fill.
     */
    public int getFillGrey(UUID pyramidID) {
        if (Util.DEFAULT_FILL_STYLE == Util.FILL_STYLE.fixed) {
            return Util.FILL_COLOR_INT;
        }
        PyramidGrey23 pyramid = pyramids.get(pyramidID);
        if (pyramid == null) {
            log.debug("Could not resolve Pyramid for " + pyramidID + ", returning default grey " + Util.FILL_COLOR_INT);
            return Util.FILL_COLOR_INT;
        }
        return pyramid.getAverageGrey();
    }
    public PyramidGrey23 getPyramid(String origin) {
        try {
            UUID id = new UUID(origin);
            PyramidGrey23 pyramid = getPyramid(id);
            if (pyramid == null) {
                log.warn("Unable to locate pyramid for resolved id '" + id.toHex() +"' from source '" + origin + "'");
                return null;
            }
            return pyramid;
        } catch (IllegalArgumentException e) {
            log.warn("getPyramid(" + origin + "): Unable to extract UUID");
        }
        return null;
    }
    public PyramidGrey23 getPyramid(UUID id) {
        return pyramids.get(id);
    }

    private List<PyramidGrey23> getClosest(List<List<PyramidGrey23>> pyramids, final int ideal, Random random) {
        int delta = -1;
        while (delta++ < bucketCount*2) { // Linear search out from origo
            int index = ideal/bucketSize + (((delta & 1) == 1 ? -1 : 1) * delta/2);
            if (index < 0 || index >= bucketCount) {
                continue;
            }
            if (!pyramids.get(index).isEmpty()) {
                return pyramids.get(index);
            }
        }
        throw new IllegalStateException("Getting closest should always return a candidate list");
    }


    private long addPyramid(Path dat) {
        PyramidGrey23 pyramid;
        try {
            if ((pyramid = Config.imhotep.createNew(dat)) == null) {
                return 0;
            }
        } catch (Exception e) {
            log.warn("Unable to load Pyramid from '" + dat + "'", e);
            return 0;
        }
        long pixels = addPyramid(pyramid);
        log.trace("Loaded #" + size() + " " + pyramid);
        return pixels;
    }

    /**
     * @return pixel count for the source image for the pyramid.
     */
    private long addPyramid(PyramidGrey23 pyramid) {
        synchronized (pyramidsTop) {
            pyramidsTop.get(pyramid.getTopPrimary() / bucketSize).add(pyramid);
        }
        synchronized (pyramidsBottom) {
            pyramidsBottom.get(pyramid.getBottomPrimary() / bucketSize).add(pyramid);
        }
        synchronized (pyramids) {
            pyramids.put(pyramid.getID(), pyramid);
        }
        return pyramid.getSourceWidth()*pyramid.getSourceHeight();
    }

    private String listBuckets() {
        StringBuilder sb = new StringBuilder();
        int bucketStart = 0;
        for (int bucket = 0 ; bucket < bucketCount ; bucket++) {
            if (bucket != 0) {
                sb.append(", ");
            }
            sb.append(bucketStart).append("(").append(pyramidsTop.get(bucket).size()).append(")");
            bucketStart += bucketSize;
        }
        return sb.toString();
    }

    /**
     * @return a random Pyramid from the full collection of Pyramids.
     */
    public PyramidGrey23 getRandom() {
        Random random = new Random();
        List<List<PyramidGrey23>> defined = new ArrayList<>(pyramidsTop.size());
        for (List<PyramidGrey23> pList: pyramidsTop) {
            if (!pList.isEmpty()) {
                defined.add(pList);
            }
        }
        if (defined.isEmpty()) {
            throw new IllegalStateException("No pyramids defined");
        }
        List<PyramidGrey23> pList = defined.get(random.nextInt(defined.size()));
        return pList.get(random.nextInt(pList.size()));
    }
}
