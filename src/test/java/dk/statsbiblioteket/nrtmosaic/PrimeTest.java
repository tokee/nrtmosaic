package dk.statsbiblioteket.nrtmosaic;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
public class PrimeTest {
    private static Log log = LogFactory.getLog(PrimeTest.class);

    final String BASE = "/avis-show/symlinks/9/c/0/5/9c05d958-b616-47c1-9e4f-63ec2dd9429e.jp2_files/";
    final String BASE2 = "/home/te/projects/nrtmosaic/sample/0010a611-4af0-405c-a60d-977314627740.tif_files";

    @Test
    public void testDZI() {
        final String DZI = "/avis-show/symlinks/9/c/0/5/9c05d958-b616-47c19e4f-63ec2dd9429e.jp2.dzi";
        String actual = Prime.instance().getDZI(DZI);
        log.debug("Got DZI response\n" + actual);
        assertTrue("A DZI structure should be returned", actual.contains("Width"));
    }

    @Test
    public void testRandomImage() {
        int RUNS = 100;
        Set<String> randomIDs = new HashSet<>(RUNS);
        for (int i = 0 ; i < RUNS ; i++) {
            randomIDs.add(Prime.instance().getRandomImage());
        }
        assertFalse("There should be some random IDs", randomIDs.isEmpty());
        assertTrue("There should be more than 1 random ID", randomIDs.size() > 1);
    }

    @Test
    public void testKnownProblem() throws IOException {
        String BASE = TileProviderTest.SAMPLE_1;
        Prime.instance().deepzoom(String.format("%s/%d/%d_%d", BASE, 23, 2, 0), "2.0", "1.2");
        Prime.instance().deepzoom(String.format("%s/%d/%d_%d", BASE, 23, 3, 0), "2.0", "1.2");
    }

    @Test
    public void testTurtle() throws IOException {
        Util.show(ImageIO.read(Util.resolveURL("turtle.png")));
    }

    @Test
    public void testExperiment() throws IOException {
        // /avis-show/symlinks/9/c/0/5/9c05d958-b616-47c1-9e4f-63ec2dd9429e.jp2_files/0/0_0.jpg
        final int START = 12;
        final int END = 26;

        BufferedImage[] tiles = new BufferedImage[END-START+1];
        int c = 6;
        for (int zoom = START ; zoom <= END ; zoom++) {
            tiles[zoom-START] = Prime.instance().deepzoom(String.format(
                    "%s/%d/%d_%d", BASE2, zoom, c, c), "2.0", "1.2");
            c *= 2;
        }
        Util.show(tiles);
    }

    @Test
    public void testTEHome() throws IOException {
        String BASE= "/mnt/active/www/nrtmosaic/tiff/3ec6edb8-8729-4f63-917e-aa635de34532.tif_files/8/0_0";
        final int START = 12;
        final int END = 26;

        BufferedImage[] tiles = new BufferedImage[END-START+1];
        int c = 4;
        for (int zoom = START ; zoom <= END ; zoom++) {
            tiles[zoom-START] = Prime.instance().deepzoom(String.format(
                    "%s/%d/%d_%d", BASE, zoom, c, c), "2.0", "1.2");
            c *= 2;
        }
        Util.show(tiles);
    }

    // https://github.com/tokee/nrtmosaic/issues/11
    @Test
    public void testTileWrap23() throws IOException {
        showSome(BASE, 23, 142, 236);
    }

    // https://github.com/tokee/nrtmosaic/issues/11
    @Test
    public void testTileWrap24() throws IOException {
        showSome(BASE, 24, 0, 0);
    }

    @Test
    public void testTileWrap11() throws IOException {
        showSome(BASE, 11, 0, 4);
    }
    @Test
    public void testTileWrap10() throws IOException {
        showSome(BASE, 10, 0, 0);
    }

    private void showSome(String BASE, int level, int x, int y) throws IOException {
        List<BufferedImage> tiles = new ArrayList<>();
        for (int dy = 0; dy < 3; dy++) {
            for (int dx = 0; dx < 6; dx++) {
                tiles.add(Prime.instance().deepzoom(String.format(
                        "%s/%d/%d_%d", BASE, level, x + dx, y + dy), "2.0", "1.2", true, false));
            }
        }
        Util.show(tiles);
    }

    @Test
    public void testKnownFail() throws IOException {
        // /avis-show/symlinks/9/c/0/5/9c05d958-b616-47c1-9e4f-63ec2dd9429e.jp2_files/0/0_0.jpg
        int c = 5120;
        Prime.instance().deepzoom(String.format("%s/%d/%d_%d", BASE, 22, c, c), "2.0", "1.2");
    }
}