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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Keeps track of the Pyramids.
 */
public class Keeper {
    private static Log log = LogFactory.getLog(Keeper.class);

    private final PyramidGrey23 imhotep;
    // TODO: This could be much more efficiently packed as just a single byte[]
    private final List<List<PyramidGrey23>> pyramidsTop = new ArrayList<>(256);
    private final List<List<PyramidGrey23>> pyramidsBottom = new ArrayList<>(256);

    public int size() {
        int size = 0;
        for (List<PyramidGrey23> pList: pyramidsTop) {
            size += pList.size();
        }
        return size;
    }

    {
        for (int i = 0 ; i < 256 ; i++) {
            pyramidsTop.add(new ArrayList<>());
            pyramidsBottom.add(new ArrayList<>());
        }
    }

    // /tmp/pyramid_test1631652512768907712/ 02/ 82/ 02823b5f223a41249913985cb5ad815f.dat
    public Keeper(Path root, PyramidGrey23 imhotep) {
        this.imhotep = imhotep;
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

    private void addPyramid(Path dat) {
        PyramidGrey23 pyramid;
        try {
            pyramid = imhotep.createNew(dat);
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
