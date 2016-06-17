package dk.statsbiblioteket.nrtmosaic;

import org.junit.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;

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

    @Test
    public void testExperiment() throws IOException {
        // /avis-show/symlinks/9/c/0/5/9c05d958-b616-47c1-9e4f-63ec2dd9429e.jp2_files/0/0_0.jpg
        final String BASE = "/avis-show/symlinks/9/c/0/5/9c05d958-b616-47c1-9e4f-63ec2dd9429e.jp2_files/";
        final int START = 12;
        final int END = 22;

        BufferedImage[] tiles = new BufferedImage[END-START+1];
        int c = 5;
        for (int zoom = START ; zoom <= END ; zoom++) {
            tiles[zoom-START] = Prime.instance().deepzoom(String.format(
                    "%s/%d/%d_%d", BASE, zoom, c, c), "2.0", "1.2");
            c *= 2;
        }
        Util.show(tiles);
    }
}