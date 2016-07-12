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

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PyramidConcatenator {
    private static Log log = LogFactory.getLog(PyramidConcatenator.class);

    public static void concatenate() throws IOException {
        new PyramidConcatenator().concatenateInternal();
    }

    private void concatenateInternal() throws IOException {
        long startTime = System.nanoTime();
        final boolean overwrite = Config.getBool("corpuscreator.overwrite");
        final Path cRoot = Config.getCacheRoot().resolve("concatenated");
        if (Files.exists(cRoot.resolve("0.dat"))) {
            if (!overwrite) {
                log.info("Skipping pyramid concatenation as concats in " + cRoot + " exists and overwrite == false");
                return;
            }
            log.info("Overwriting old pyramid concatenations from " + cRoot + " as overwrite == true");
            for (int i = 0 ; i < 256 ; i++) {
                Files.deleteIfExists(cRoot.resolve(i + ".dat"));
            }
            log.debug("Old pyramid concatenations removed successfully");

        }
        log.info("Creating concatenated pyramid data in " + cRoot);

        final FileOutputStream[] concats = new FileOutputStream[256];
        if (!Files.exists(cRoot)) {
            Files.createDirectories(cRoot);
        }
        for (int i = 0 ; i < 256 ; i++) {
            concats[i] = new FileOutputStream(cRoot.resolve(i + ".dat").toFile());
        }
        Util.wrappedList(Config.getCacheRoot()).
                filter(sub1 -> Files.isDirectory(sub1) && sub1.getFileName().toString().length() == 2).
                forEach(sub1 -> Util.wrappedList(sub1).
                        filter(sub2 -> Files.isDirectory(sub2) && sub2.getFileName().toString().length() == 2).
                        forEach(sub2 -> Util.wrappedList(sub2).
                                filter(dat -> Files.isRegularFile(dat) && dat.toString().endsWith(".dat")).
                                forEach(pyramidFile -> concatenatePyramid(concats, pyramidFile))));
        log.debug("Closing concatenate files");
        for (int i = 0 ; i < 256 ; i++) {
            concats[i].flush();
            concats[i].close();
        }
        log.info("Finished pyramid concatenation in " + (System.nanoTime()-startTime)/1000000 + "ms");

    }

    private void concatenatePyramid(FileOutputStream[] concats, Path pyramidFile) {
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
        try {
            pyramid.store(concats[pyramid.getTopPrimary()]);
        } catch (IOException e) {
            throw new RuntimeException("IOException storing " + pyramid + " to concat " + pyramid.getTopPrimary());
        }
        log.debug("Concatenation grey " + pyramid.getTopPrimary() + " was extended with " + pyramid);
    }

}
