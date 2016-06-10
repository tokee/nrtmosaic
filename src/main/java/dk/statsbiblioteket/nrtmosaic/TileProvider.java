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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

/**
 *
 */
public class TileProvider {
    private static Log log = LogFactory.getLog(TileProvider.class);

    private static final Keeper keeper = new Keeper();
    private static final Map<String, Tile23> tileCache = new LRUCache<String, Tile23>(Config.getInt("tile.cachesize"));
    private static int edge = Config.getInt("tile.edge");

    /**
     * Resolve a Tile from the source and generate an image based on it.
     * @param source an image, the same size as the tile.
     * @param x logical x within the source: Level 1 has [0..0], level 2 has [0..1], level 3 has [0..3], 4 has [0..7].
     * @param y same principle as x.
     * @param z 2 returns image made up of pyramids scaled to 2x3 pixels.
     * @return a mosaic that should look approximately like the source at the given z level.
     */
    public static BufferedImage getTile(String source, int x, int y, int z) {
        CorpusCreator.generateCache();
        Tile23 tile = tileCache.get(source);
        if (tile == null) {
            URL imageURL = Util.resolveURL(source);
            if (imageURL == null) {
                throw new IllegalArgumentException("Unable to load image from '" + source + "'");
            }
            try {
                BufferedImage image = ImageIO.read(imageURL);
                if (image.getWidth() != edge || image.getHeight() != edge) {
                    throw new IllegalArgumentException(
                            "The image from '" + source + "' should be " + edge + "x" + edge + " pixels but was " +
                            image.getWidth() + "x" + image.getHeight() + " pixels");
                }
                tile = Tile23.createTile(image, keeper);
                tileCache.put(source, tile);
            } catch (IOException e) {
                throw new RuntimeException("Unable to resolve tile for source=" + source + ", x=" + x + ", y=" + y + ", z=" + z);
            }
        }
        long startTime = System.nanoTime();
        BufferedImage rendered = tile.renderImage(x, y, z, null);
        log.debug("Rendered tile for source=" + source + ", x=" + x + ", y=" + y + ", z=" + z + " in " +
                  (System.nanoTime()-startTime)/1000000 + "ms");
        return rendered;
    }
}
