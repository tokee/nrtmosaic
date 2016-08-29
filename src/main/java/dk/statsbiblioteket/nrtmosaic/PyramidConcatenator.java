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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PyramidConcatenator {
    private static Log log = LogFactory.getLog(PyramidConcatenator.class);

    private static final long MAX_CONCAT_SIZE = (long)Math.pow(2, 30); // 1GB
    private final Path cRoot = Config.getCacheRoot().resolve("concatenated");
    private long currentSize = Long.MAX_VALUE;
    private int concatFileCount = -1;
    private int pyramidCount = 0;
    private FileOutputStream currentStream = null;

    public static void concatenate() throws IOException {
        new PyramidConcatenator().concatenateInternal();
    }

    public static boolean concatenationsExists() {
        return Files.exists(Config.getCacheRoot().resolve("concatenated").resolve("0.dat"));
    }

    private void concatenateInternal() throws IOException {
        long startTime = System.nanoTime();
        if (concatenationsExists()) {
            if (!Config.getBool("corpuscreator.overwrite")) {
                log.info("Skipping pyramid concatenation as concats in " + cRoot + " exists and overwrite == false");
                return;
            }
            log.info("Overwriting old pyramid concatenations from " + cRoot + " as overwrite == true");
            Util.deleteFolder(cRoot);
            log.debug("Old pyramid concatenations removed successfully");

        }
        if (!Files.exists(cRoot)) {
            Files.createDirectories(cRoot);
        }
        log.info("Creating concatenated pyramid data in " + cRoot);

        Util.wrappedList(Config.getCacheRoot()).
                filter(sub1 -> Files.isDirectory(sub1) && sub1.getFileName().toString().length() == 2).
                forEach(sub1 -> Util.wrappedList(sub1).
                        filter(sub2 -> Files.isDirectory(sub2) && sub2.getFileName().toString().length() == 2).
                        forEach(sub2 -> Util.wrappedList(sub2).
                                filter(dat -> Files.isRegularFile(dat) && dat.toString().endsWith(".dat")).
                                forEach(this::concatenatePyramid)));
        if (currentStream != null) {
            log.debug("Closing last concatenated file");
            currentStream.close();
        }
        log.info("Finished " + pyramidCount + " pyramid concatenations into " + (concatFileCount+1) + " files in " +
                 (System.nanoTime()-startTime)/1000000 + "ms");
    }

    private void concatenatePyramid(Path pyramidFile) {
        PyramidGrey23 pyramid;
        try {
            if ((pyramid = Config.imhotep.createNew(pyramidFile)) == null) {
                log.debug("Unable to load pyramid from " + pyramidFile);
                return;
            }
        } catch (Exception e) {
            log.warn("Unable to load Pyramid from '" + pyramidFile + "'", e);
            return;
        }
        if (currentSize > MAX_CONCAT_SIZE) {
            if (currentStream != null) {
                try {
                    currentStream.flush();
                    currentStream.close();
                } catch (IOException e) {
                    throw new RuntimeException(
                            "IOException closing previous concatenation stream #" + concatFileCount, e);
                }
            }
            File file = cRoot.resolve(++concatFileCount + ".dat").toFile();
            log.info("Creating new pyramid concatenation file " + file);
            try {
                currentStream = new FileOutputStream(file);
                currentSize = 0;
            } catch (IOException e) {
                throw new RuntimeException("IOException opening new concatenation stream #" + file, e);
            }
        }

        try {
            currentSize += pyramid.store(currentStream);
            pyramidCount++;
            log.debug("Added Pyramid #" + pyramidCount + "(" + pyramid + ") to concatenation cache");
        } catch (IOException e) {
            throw new RuntimeException("IOException storing " + pyramid + " to concatenation");
        }
    }

}
