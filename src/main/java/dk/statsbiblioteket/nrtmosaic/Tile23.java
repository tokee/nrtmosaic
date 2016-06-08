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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Contains mapping from an imput image to Pyramids. Tiles are always square.
 * The 23 in the name refers to the aspect ratio of the Pyramids.
 */
public class Tile23 {
    private static Log log = LogFactory.getLog(Tile23.class);

    public static final int edge = 256;

    private final PyramidGrey23[] map = new PyramidGrey23[edge*edge*2/3+2]; // width(edge) * height(2/3 of edge) + round

    public static Tile23 createTile(BufferedImage source, Keeper keeper) {
        if (source.getWidth() != edge || source.getHeight() != edge) {
            throw new IllegalArgumentException(
                    "Width and height should be equal to " + edge + ", but image dimensions were (" +
                    source.getWidth() + ", " + source.getHeight() + ")");
        }
        long startNS = System.nanoTime();
        Tile23 tile = new Tile23();
        Random random = new Random(87); // Same seed every time! If not, zooming out & in again will change tile mapping

        int[] pixels = new int[edge*edge];
        source.getRaster().getPixels(0, 0, edge, edge, pixels);
        int index = 0;
        for (int y = 0 ; y < edge ; y++) {
            switch (y*2%3) {
                case 2:   // None (1, 4, 7, 10...)
                    continue;
                case 0: { // Top-down (0, 3, 6, 9...)
                    for (int x = 0 ; x < edge ; x++) {
                        int primary = pixels[y*edge + x];
                        int secondary = pixels[(y+1)*edge + x];
                        tile.map[index++] = keeper.getClosestTop(primary, secondary, random);
                    }
                    break;
                }
                case 1: { // Bottom-up (2, 5, 8, 11...)
                    for (int x = 0; x < edge; x++) {
                        int primary = pixels[y * edge + x];
                        int secondary = pixels[(y - 1) * edge + x];
                        tile.map[index++] = keeper.getClosestBottom(primary, secondary, random);
                    }
                }
            }
        }
        log.debug("Mapped tile in " + (System.nanoTime()-startNS)/1000000 + "ms");
        return tile;
    }
}
