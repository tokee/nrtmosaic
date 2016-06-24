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

    private final Keeper keeper;
    private final Map<String, Tile23> tileCache;
    private final int edge;

    public TileProvider(Keeper keeper) {
        this.keeper = keeper;
        tileCache  = new LRUCache<>(Config.getInt("tile.cachesize"));
        edge = Config.getInt("tile.edge");
    }

    /**
     * Resolve a Tile from the source and generate an image based on it.
     * @param source an image, the same size as the tile.
     * @param x logical x within the source: Level 1 has [0..0], level 2 has [0..1], level 3 has [0..3], 4 has [0..7].
     * @param y same principle as x.
     * @param z 2 returns image made up of pyramids scaled to 2x3 pixels.
     * @param allowNA allow non-existing sources, in which case a tile with default background will be used.
     * @return a mosaic that should look approximately like the source at the given z level.
     */
    public BufferedImage getTileRender(String source, int x, int y, int z, boolean allowNA) {
        Tile23 tile = getTile(source, allowNA);
        long startTime = System.nanoTime();
        BufferedImage render = tile.renderImage(x, y, z, null);
        log.debug("Rendered tile for source=" + source + ", x=" + x + ", y=" + y + ", z=" + z +
                  " in " + (System.nanoTime()-startTime)/1000000 + "ms");
        return render;
    }

    /**
     * Resolve a Tile for the source, mapping the source to Pyramids if not already cached.
     * @param source an image, expected to be edge*edge pixels, but padding will be applied if too small.
     * @param allowNA allow non-existing sources, in which case a blank tile will be generated.
     * @return a Tile that should look render approximately like the source at the given z level.
     */
    public Tile23 getTile(String source, boolean allowNA) {
        long startTime = System.nanoTime();
        Tile23 tile = tileCache.get(source);
        if (tile != null) {
            return tile;
        }
        URL imageURL = Util.resolveURL(source);
        if (imageURL == null) {
            throw new IllegalArgumentException("Unable to resolve image url '" + source + "'");
        }
        BufferedImage image;
        try {
            image = ImageIO.read(imageURL);
        } catch (IOException e) {
            if (allowNA) {
                log.debug("No tile at '" + source + "' but allowNA==true so default blank is used");
                image = Util.getBlankTile(keeper.getFillGrey(imageURL.toString()));
            } else {
                throw new RuntimeException("Unable to resolve tile for source=" + source);
            }
        }
        if (image.getWidth() != edge || image.getHeight() != edge) {
            int fillGrey = keeper.getFillGrey(imageURL.toString());
            log.trace("Padding tile '" + source + "' of " + image.getWidth() + "x" + image.getHeight() +
                      " pixels to " + edge + "x" + edge + " pixels with fill " + fillGrey);
            image = Util.pad(image, edge, edge, fillGrey);
        }
        tile = Tile23.createTile(image, keeper);
        tileCache.put(source, tile);
        log.debug("Mapped tile for source=" + source + " in " + (System.nanoTime()-startTime)/1000000 + "ms");
        return tile;
    }
}
