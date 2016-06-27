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
import java.nio.file.Paths;
import java.util.*;

/**
 * Keeps track of the Pyramids.
 */
public class Keeper {
    private static Log log = LogFactory.getLog(Keeper.class);

    // TODO: This could be much more efficiently packed as just a single byte[]
    private final List<List<PyramidGrey23>> pyramidsTop = new ArrayList<>(256);
    private final List<List<PyramidGrey23>> pyramidsBottom = new ArrayList<>(256);
    private final Map<UUID, PyramidGrey23> pyramids = new HashMap<>();

    {
        for (int i = 0 ; i < 256 ; i++) {
            pyramidsTop.add(new ArrayList<>());
            pyramidsBottom.add(new ArrayList<>());
        }
    }

    public Keeper() {
        this(Paths.get(Config.getString("pyramid.cache"), Config.getString("tile.fill.style")));
    }

    // /tmp/pyramid_test1631652512768907712/ 02/ 82/ 02823b5f223a41249913985cb5ad815f.dat
    public Keeper(Path root) {
        long startTime = System.nanoTime();
        loadFromConcatenations(root);
        //loadFromIndividualFiles(root);
        log.info("Finished loading " + size() + " pyramids in " + (System.nanoTime() - startTime) / 1000000 + "ms");
    }

    private void loadFromConcatenations(Path root) {
        Path concatRoot = root.resolve("concatenated");
        if (!Files.exists(concatRoot)) {
            throw new RuntimeException("The expected concatenation cache did not exist at " + concatRoot);
        }
        for (int i = 0 ; i < 256 ; i++) {
            Path concatFile = concatRoot.resolve(i + ".dat");
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
            int offset = 0;
            int mapCount = 0;
            while (offset < concatSize) {
                addPyramid(Config.imhotep.createNew(mapped, offset));
                offset += Config.imhotep.getBytecount();
                mapCount++;
            }
            log.debug("Mapped " + mapCount + " pyramids from " + concatFile);
        }
        sortPyramids();
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
        for (int i = 0 ; i < 256 ; i++) {
            Collections.sort(pyramidsTop.get(i), (o1, o2) -> o2.getTopSecondary()-o1.getTopSecondary());
            Collections.sort(pyramidsBottom.get(i), (o1, o2) -> o2.getBottomSecondary()-o1.getBottomSecondary());
        }
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
    public int getFillGrey(String origin) {
        if (Util.DEFAULT_FILL_STYLE == Util.FILL_STYLE.fixed) {
            return Util.FILL_COLOR_INT;
        }
        try {
            return getFillGrey(new UUID(origin));
        } catch (IllegalArgumentException e) {
            log.warn("Unable to extract UUID from '" + origin + "'. Using default fill grey " + Util.FILL_COLOR_INT);
        }
        return Util.FILL_COLOR_INT;
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
            return getPyramid(new UUID(origin));
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
        while (delta++ < 512) { // Linear search out from origo
            int index = ideal + (((delta & 1) == 1 ? -1 : 1) * delta/2);
            if (index < 0 || index > 255) {
                continue;
            }
            if (!pyramids.get(index).isEmpty()) {
                return pyramids.get(index);
            }
        }
        throw new IllegalStateException("Getting closest should always return a candidate list");
    }


    private void addPyramid(Path dat) {
        PyramidGrey23 pyramid;
        try {
            if ((pyramid = Config.imhotep.createNew(dat)) == null) {
                return;
            }
        } catch (Exception e) {
            log.warn("Unable to load Pyramid from '" + dat + "'", e);
            return;
        }
        addPyramid(pyramid);
        log.trace("Loaded #" + size() + " " + pyramid);
    }

    private void addPyramid(PyramidGrey23 pyramid) {
        pyramidsTop.get(pyramid.getTopPrimary()).add(pyramid);
        pyramidsBottom.get(pyramid.getBottomPrimary()).add(pyramid);
        pyramids.put(pyramid.getID(), pyramid);
    }

}
