package dk.statsbiblioteket.nrtmosaic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import static org.junit.Assert.*;

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
public class KeeperTest {

    private Path lastRoot = null;

    // Intentionally disabled
    public void setUp() {
        try {
            lastRoot = CorpusCreatorTest.createPyramidSample();
        } catch (IOException e) {
            throw new RuntimeException("Unable to creste test pyramids", e);
        }
    }

    @After
    public void tearDown() {
        if (lastRoot != null) {
            try {
                Files.walkFileTree(lastRoot, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("IOException deleting " + lastRoot, e);
            }
        }
    }

    @Test
    public void testLoad() {
/*        if (lastRoot == null) {
            return;
        }*/
        //Keeper keeper = new Keeper(lastRoot, CorpusCreator.imhotep);
        Keeper keeper = new Keeper();
        assertTrue("There should be some pyramids in the keeper", keeper.size() > 0);
    }
}