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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * Keeps track of the Pyramids.
 */
public class Keeper {
    private static Log log = LogFactory.getLog(Keeper.class);

    // TODO: This could be much more efficiently packed as just a single byte[]
    private final List<List<PyramidGrey23>> pyramidsTop = new ArrayList<>(256);
    private final List<List<PyramidGrey23>> pyramidsBottom = new ArrayList<>(256);

    {
        for (int i = 0 ; i < 256 ; i++) {
            pyramidsTop.add(new ArrayList<>());
            pyramidsBottom.add(new ArrayList<>());
        }
    }

    public Keeper() {
        this(Paths.get(Config.getString("pyramid.cache")));
    }

    // /tmp/pyramid_test1631652512768907712/ 02/ 82/ 02823b5f223a41249913985cb5ad815f.dat
    public Keeper(Path root) {
        CorpusCreator.generateCache(); // Entangled coding. Consider an overall application control class instead
        wrappedList(root).
                filter(sub1 -> Files.isDirectory(sub1)).
                forEach(sub1 -> wrappedList(sub1).
                        filter(sub2 -> Files.isDirectory(sub2)).
                        forEach(sub2 -> wrappedList(sub2).
                                filter(dat -> Files.isRegularFile(dat) && dat.toString().endsWith(".dat")).
                                forEach(this::addPyramid)));
        sortPyramids();
    }

    private void sortPyramids() {
        for (int i = 0 ; i < 256 ; i++) {
            Collections.sort(pyramidsTop.get(i), new Comparator<PyramidGrey23>() {
                @Override
                public int compare(PyramidGrey23 o1, PyramidGrey23 o2) {
                    return o2.getTopSecondary()-o1.getTopSecondary();
                }
            });
            Collections.sort(pyramidsBottom.get(i), new Comparator<PyramidGrey23>() {
                @Override
                public int compare(PyramidGrey23 o1, PyramidGrey23 o2) {
                    return o2.getBottomSecondary()-o1.getBottomSecondary();
                }
            });
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
            pyramid = Config.imhotep.createNew(dat);
            log.debug("Loaded " + pyramid);
        } catch (IOException e) {
            // TODO: Consider logging and continuing here, but catch the case where there are only errors
            throw new RuntimeException("IOException reading pyramid data from " + dat, e);
        }
        pyramidsTop.get(pyramid.getTopPrimary()).add(pyramid);
        pyramidsBottom.get(pyramid.getBottomPrimary()).add(pyramid);
    }

    private Stream<Path> wrappedList(Path folder) {
        try {
            return Files.list(folder);
        } catch (IOException e) {
            throw new RuntimeException("IOException iterating sub-folders to for " + folder, e);
        }
    }
}
