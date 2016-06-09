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
                    if (y+1>=edge) {
                        continue; // TODO: Figure out how to handle top-bottom borders between tiles
                    }
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
    public BufferedImage renderImage(final int subTileX, final int subTileY, final int level, BufferedImage reuse) {
        final int pyramidLevel = level-1;
        if (reuse == null) {
            reuse = new BufferedImage(edge, edge, BufferedImage.TYPE_BYTE_GRAY);
        }
        final int[] canvas = new int[edge*edge];
        final int pyramidTileEdge = Config.imhotep.getTileEdge(pyramidLevel); // Will be 0 for pyramidLevel 0
        final double sourceToCanvasFactorX =
                pyramidLevel == 0 ? 1 : 2*pyramidTileEdge;
                //pyramidLevel == 0 ? 1 : 1d*Config.imhotep.getFractionWidth()*pyramidTileEdge;
                //pyramidLevel == 0 ? 1 : 1d*pyramidTileEdge;
        final double sourceToCanvasFactorY =
                pyramidLevel == 0 ? 1 : 2*pyramidTileEdge;
                //pyramidLevel == 0 ? 1 : 1d*Config.imhotep.getFractionHeight()*pyramidTileEdge;
                //pyramidLevel == 0 ? 1 : 1d*pyramidTileEdge;

        // We iterate the mappings from a rectangle on the original Tile image and
        // render the right level from the pyramids onto the canvas
        final int shift = level-1; // z==1 -> 0 shift

        final int startX = subTileX*(edge>>shift);
        final int startY = subTileY*(edge>>shift);
        final int zoomFactor = 1<<shift;
        final int levelEdge = edge>>shift ;
        log.debug("Rendering source cutout (" + startX + ", " + startY + "), (" +
                  (startX+levelEdge) + ", " + (startY+levelEdge) + ") with zoomFactor=" + zoomFactor +
                  " and levelEdge=" + levelEdge);
        for (int sourceY = startY ; sourceY < startY+levelEdge && sourceY < edge ; sourceY++) {
            for (int sourceX = startX; sourceX < startX + levelEdge; sourceX++) {
                //final int canvasX = (int) (((sourceX - startX) << shift) * sourceToCanvasFactorX);
                final int canvasX = (int) ((sourceX - startX) * sourceToCanvasFactorX);
                final int canvasY = (int) ((sourceY - startY) * sourceToCanvasFactorY);
//                log.debug("Rendering source(" + sourceX + ", " + sourceY + ") -> canvas(" +
//                          canvasX + ", " + canvasY + ")");
                final PyramidGrey23 pyramid = map[sourceY * edge + sourceX]; // Will be null for y*2%3==2
                if (pyramid == null) {
                    continue; // Bit dangerous as we do not discover if everything is null
                }
                switch (sourceY * 2 % 3) {
                    case 2:   // None (1, 4, 7, 10...)
                        continue;
                    case 0: { // Top-down (0, 3, 6, 9...)
                        render(pyramid, pyramidLevel, canvas, canvasX, canvasY);
                        break;
                    }
                    case 1: { // Bottom-up (2, 5, 8, 11...)
                        render(pyramid, pyramidLevel, canvas, canvasX, canvasY-pyramidTileEdge);
                        break;
                    }
                }
            }
        }
        reuse.getRaster().setPixels(0, 0, edge, edge, canvas);
        return reuse;
    }

    // Renders the 6 pyramid sub-tiles at the given position of the canvas
    private void render(PyramidGrey23 pyramid, final int level, final int[] canvas,
                        final int canvasOrigoX, final int canvasOrigoY) {
        if (level == 0) {
            renderZero(pyramid, canvas, canvasOrigoX, canvasOrigoY);
            return;
        }
        final int pTileEdge = pyramid.getTileEdge(level);
//        log.debug("Render pyramid(edge=" + pTileEdge + ") -> canvas(" + canvasOrigoX + ", " + canvasOrigoY + ")");
        for (int fy = 0 ; fy < pyramid.getFractionHeight() ; fy++) {
            for (int fx = 0; fx < pyramid.getFractionWidth(); fx++) {
                renderSubTile(pyramid, pyramid.getTileOffset(level, fx, fy), pTileEdge, canvas,
                       canvasOrigoX + fx * pTileEdge, canvasOrigoY + (fy * pTileEdge));
            }
        }
    }
    private void renderZero(PyramidGrey23 pyramid, final int[] canvas, final int canvasOrigoX, final int canvasOrigoY) {
        final byte[] data = pyramid.getData();
        for (int fy = 0 ; fy < 2 ; fy++) {
            renderSubTile(pyramid, pyramid.getTileOffset(0, 0, fy), 0, canvas, canvasOrigoX, canvasOrigoY + fy);
            final int canvasIndex = (canvasOrigoY+fy)*edge + canvasOrigoX;
            if (canvasIndex >= edge*edge) {
                continue;
            }
            final int tileOffset = pyramid.getTileOffset(1, 0, fy); // Maybe top and bottom instead of top and middle?
            canvas[canvasIndex] = data[tileOffset];
        }
    }

    // Renders a single sub-tile at the given place on the canvas
    private void renderSubTile(PyramidGrey23 pyramid, final int tileOffset, final int pTileEdge, final int[] canvas,
                               final int canvasOrigoX, final int canvasOrigoY) {
        final byte[] data = pyramid.getData();

        for (int pyramidY = 0 ; pyramidY < pTileEdge ; pyramidY++) {
            for (int pyramidX = 0; pyramidX < pTileEdge; pyramidX++) {
                final int canvasIndex = (canvasOrigoY+pyramidY)*edge + canvasOrigoX+pyramidX;
                if (canvasIndex >= edge*edge || canvasIndex < 0) {
                    continue;
                }
                final int pyramidIndex = tileOffset + (pyramidY*pTileEdge) + pyramidX;
/*                if (pyramidIndex > data.length) {
                    continue; // TODO: This should not happen!?
                }*/
                canvas[canvasIndex] = data[pyramidIndex];
            }
        }
    }
}
