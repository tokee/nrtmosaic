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

    public static final int edge = Config.getInt("tile.edge");

    private final PyramidGrey23[] map = new PyramidGrey23[edge*edge]; // Optimization: 1/3 of these are always empty.

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
        for (int y = 0 ; y < edge ; y++) {
            switch (y*2%3) {
                case 2:   // None (1, 4, 7, 10...)
                    continue;
                case 0: { // Top-down (0, 3, 6, 9...)
                    for (int x = 0 ; x < edge ; x++) {
                        int primary = pixels[y*edge + x];
                        int secondary = pixels[(y+1)*edge + x];
                        tile.map[y*edge+x] = keeper.getClosestTop(primary, secondary, random);
                    }
                    break;
                }
                case 1: { // Bottom-up (2, 5, 8, 11...)
                    for (int x = 0; x < edge; x++) {
                        int primary = pixels[y * edge + x];
                        int secondary = pixels[(y - 1) * edge + x];
                        tile.map[y*edge+x] = keeper.getClosestBottom(primary, secondary, random);
                    }
                }
            }
        }
        log.debug("Mapped tile in " + (System.nanoTime()-startNS)/1000000 + "ms");
        return tile;
    }

    /**
     * @param subTileX logical x within the tile: Level 1 has [0..0], l2 has [0..1], l3 has [0..3], l4 has [0..7].
     * @param subTileY same principle as subTileX.
     * @param level 2 returns image made up of pyramids scaled to 2x3 pixels.
     * @return a mosaic that should look approximately like the source at the given z level.
     */
    public BufferedImage renderImage(int subTileX, int subTileY, int level, BufferedImage reuse) {
        if (reuse == null) {
            reuse = new BufferedImage(edge, edge, BufferedImage.TYPE_BYTE_GRAY);
        }
        final int[] canvas = new int[edge*edge];

        // We iterate the mappings from a rectangle on the original Tile image and
        // render the right level from the pyramids onto the canvas
        final int shift = level-1; // z==1 -> 0 shift

        final int startX = subTileX*(edge>>shift);
        final int startY = subTileY*(edge>>shift);
        final int zoomFactor = 1<<shift;
        final int levelEdge = (edge>>shift);
        for (int y = startY ; y < startY+levelEdge ; y++) {
            for (int x = startX; y < startY + levelEdge; x++) {
                final int canvasX = (x - startX) << shift;
                final int canvasY = (y - startY) << shift;
                final PyramidGrey23 pyramid = map[y * edge + x]; // Will be null for y*2%3==2
                switch (y * 2 % 3) {
                    case 2:   // None (1, 4, 7, 10...)
                        continue;
                    case 0: { // Top-down (0, 3, 6, 9...)
                        render(pyramid, level, canvas, canvasX, canvasY);
                        break;
                    }
                    case 1: { // Bottom-up (2, 5, 8, 11...)
                        render(pyramid, level, canvas, canvasX, (2*canvasY-zoomFactor)/2);
                        break;
                    }
                }
            }
        }
        // TODO: Set rendered data
        return reuse;
    }

    // Renders the 6 pyramid sub-tiles at the given position of the canvas
    private void render(PyramidGrey23 pyramid, int level, int[] canvas, int canvasOrigoX, int canvasOrigoY) {
        final int pTileEdge = pyramid.getTileEdge(level);
        for (int fy = 0 ; fy < pyramid.getFractionHeight() ; fy++) {
            for (int fx = 0; fx < pyramid.getFractionWidth(); fx++) {
                renderSubTile(pyramid, pyramid.getTileOffset(level, fx, fy), pTileEdge, canvas,
                       canvasOrigoX + fx * pTileEdge, canvasOrigoY + (fy * pTileEdge * edge));
            }
        }
    }

    // Renders a single sub-tile at the given place on the canvas
    private void renderSubTile(PyramidGrey23 pyramid, int tileOffset, int pTileEdge, int[] canvas,
                               int canvasOrigoX, int canvasOrigoY) {
        final byte[] data = pyramid.getData();
        for (int y = canvasOrigoY ; y < canvasOrigoY+pTileEdge ; y++) {
            for (int x = canvasOrigoX ; x < canvasOrigoX+pTileEdge ; x++) {
                canvas[y*edge+x] = data[y*edge+x] & 0xFF;
            }
        }
    }
}
