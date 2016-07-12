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
    private static final Log log = LogFactory.getLog(Tile23.class);

    public static final int edge = Config.getInt("tile.edge");
     // Potential optimization: 1/3 of these are always empty.
    private final PyramidGrey23[] pyramids = new PyramidGrey23[edge*edge];
    private final byte[] dynamicGreys = new byte[edge*edge];

    public void setPyramid(int x, int y, PyramidGrey23 pyramid, int wantedAverage) {
        pyramids[y*edge+x] = pyramid;
        dynamicGreys[y*edge+x] = (byte) pyramid.getDynamic(wantedAverage);
    }

    public PyramidGrey23 getPyramid(int x, int y) {
        try {
            return pyramids[y * edge + x];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new ArrayIndexOutOfBoundsException(
                    "OutOfBounds while requesting pyramid at " + x + "x" + y + " from a tile of " + edge + "x" + edge);
        }
    }
    public int getDynamic(int x, int y) {
        switch (Util.DEFAULT_FILL_STYLE) {
            case fixed:   return Util.FILL_COLOR_INT;
            case average: return getPyramid(x, y).getAverageGrey();
            case dynamic: return 0xFF & dynamicGreys[y*edge+x];
            default: throw new UnsupportedOperationException(
                    "The fill style '" + Util.DEFAULT_FILL_STYLE +"' is not supported yet");
        }
    }

    /**
     * Maps the pixels in the input image into Pyramids provided by the keeper.
     * Note: For better mosaic diversity, use {@link #createTile(BufferedImage, Keeper, Random)} instead.
     * @param source the image to be mapped to Pyramids.
     * @param keeper available pyramids.
     * @return a tile containing the mapping from source to Pyramids.
     */
    public static Tile23 createTile(BufferedImage source, Keeper keeper) {
        return createTile(source, keeper, new Random(87));
    }
    /**
     * Maps the pixels in the input image into Pyramids provided by the keeper.
     * @param source the image to be mapped to Pyramids.
     * @param keeper available pyramids.
     * @param random a Random seeded specifically to the source (e.g. use the hash of the source URL).
     * @return a tile containing the mapping from source to Pyramids.
     */
    public static Tile23 createTile(BufferedImage source, Keeper keeper, Random random) {
        if (source.getWidth() != edge || source.getHeight() != edge) {
            throw new IllegalArgumentException(
                    "Width and height should be equal to " + edge + ", but image dimensions were (" +
                    source.getWidth() + ", " + source.getHeight() + ")");
        }
        long startNS = System.nanoTime();
        Tile23 tile = new Tile23();

        int[] pixels = new int[edge*edge];
        source.getRaster().getPixels(0, 0, edge, edge, pixels);
        // TODO: Map from -1 to edge instead of 0 to edge-1 to avoid the black horizontal lines
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
                        tile.setPyramid(x, y, keeper.getClosestTop(primary, secondary, random), primary);
                    }
                    break;
                }
                case 1: { // Bottom-up (2, 5, 8, 11...)
                    for (int x = 0; x < edge; x++) {
                        int primary = pixels[y * edge + x];
                        int secondary = pixels[(y - 1) * edge + x];
                        tile.setPyramid(x, y, keeper.getClosestBottom(primary, secondary, random), primary);
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
        final long startNS = System.nanoTime();
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
//        final int zoomFactor = 1<<shift;
        final int levelEdge = edge>>shift ;
        // TODO: Special-case level 1 as it needs its pixels adjusted instead of just the missing replaced
//        log.debug("Rendering source cutout (" + startX + ", " + startY + "), (" +
//                  (startX+levelEdge) + ", " + (startY+levelEdge) + ") with zoomFactor=" + zoomFactor +
//                  " and levelEdge=" + levelEdge);
        // startY+levelEdge+1 to avoid black rectangles at the bottom, due to non-square pyramid aspect ratio
        for (int sourceY = startY ; sourceY < startY+levelEdge+1 && sourceY < edge ; sourceY++) {
            for (int sourceX = startX; sourceX < startX+levelEdge; sourceX++) {
                //final int canvasX = (int) (((sourceX - startX) << shift) * sourceToCanvasFactorX);
                final int canvasX = (int) ((sourceX - startX) * sourceToCanvasFactorX);
                final int canvasY = (int) ((sourceY - startY) * sourceToCanvasFactorY);
//                log.debug("Rendering source(" + sourceX + ", " + sourceY + ") -> canvas(" +
//                          canvasX + ", " + canvasY + ")");
                switch (sourceY * 2 % 3) {
                    case 0: { // Top-down (0, 3, 6, 9...)
                        final PyramidGrey23 pyramid = getPyramid(sourceX, sourceY); // Will be null for y*2%3==2
                        if (pyramid == null) {
                            continue; // Bit dangerous as we do not discover if everything is null
                        }
                        renderTop(pyramid, pyramidLevel, canvas, canvasX, canvasY, getDynamic(sourceX, sourceY));
                        break;
                    }
                    case 2:   // None (1, 4, 7, 10...)
                        final PyramidGrey23 pyramidTop = getPyramid(sourceX, sourceY-1);
                        final PyramidGrey23 pyramidBottom = getPyramid(sourceX, sourceY+1);
                        if (pyramidTop == null || pyramidBottom == null) {
                            continue; // Bit dangerous as we do not discover if everything is null
                        }
                        renderDual(pyramidTop, pyramidBottom, pyramidLevel, canvas, canvasX, canvasY,
                                   getDynamic(sourceX, sourceY-1), getDynamic(sourceX, sourceY+1));
                        break;
                    case 1: { // Bottom-up (2, 5, 8, 11...)
                        final PyramidGrey23 pyramid = getPyramid(sourceX, sourceY); // Will be null for y*2%3==2
                        if (pyramid == null) {
                            continue; // Bit dangerous as we do not discover if everything is null
                        }
                        renderBottom(pyramid, pyramidLevel, canvas, canvasX, canvasY, getDynamic(sourceX, sourceY));
                        break;
                    }
                }
            }
        }
        reuse.getRaster().setPixels(0, 0, edge, edge, canvas);
        log.debug("Rendered tile for " + subTileX + "x" + subTileY + ", level " + level + " in " +
                  (System.nanoTime() - startNS) / 1000000 + "ms");
        return reuse;
    }

    // Render top 2/3 of the Pyramid, which will be square
    private void renderTop(PyramidGrey23 pyramid, final int level, final int[] canvas,
                           final int canvasOrigoX, final int canvasOrigoY, int dynamic) {
        if (level == 0) {
            pyramid.copyPixels(1, 0, 0, canvas, canvasOrigoX, canvasOrigoY, edge, dynamic);
            return;
        }
        final int pTileEdge = pyramid.getTileEdge(level);
//        log.debug("Render pyramid(edge=" + pTileEdge + ") -> canvas(" + canvasOrigoX + ", " + canvasOrigoY + ")");
        final int squareSide = pyramid.getFractionWidth();

        for (int fy = 0 ; fy < squareSide ; fy++) {
            for (int fx = 0; fx < squareSide; fx++) {
                pyramid.copyPixels(
                        level, fx, fy, canvas, canvasOrigoX+fx*pTileEdge, canvasOrigoY+fy*pTileEdge, edge, dynamic);
            }
        }
    }
    private void debugRect(int[] canvas, int left, int top, int right, int bottom, int grey) {
        for (int x = left ; x <= right ; x++) {
            debugSet(canvas, x, top, grey);
            debugSet(canvas, x, top+1, grey);
            debugSet(canvas, x, bottom-1, grey);
            debugSet(canvas, x, bottom, grey);
        }
        for (int y = top ; y <= bottom ; y++) {
            debugSet(canvas, left, y, grey);
            debugSet(canvas, left+2, y, grey);
            debugSet(canvas, right-1, y, grey);
            debugSet(canvas, right, y, grey);
        }
    }

    private void debugSet(int[] canvas, int x, int y, int grey) {
        int index = y*edge+x;
        if (index < edge*edge) {
            canvas[index] = grey;
        }
    }

    // Render bottom 2/3 of the Pyramid, which will be square
    private void renderBottom(PyramidGrey23 pyramid, final int level, final int[] canvas,
                              final int canvasOrigoX, final int canvasOrigoY, int dynamic) {
        if (level == 0) {
            pyramid.copyPixels(1, 0, 1, canvas, canvasOrigoX, canvasOrigoY, edge, dynamic);
            return;
        }
        final int pTileEdge = pyramid.getTileEdge(level);
//        log.debug("Render pyramid(edge=" + pTileEdge + ") -> canvas(" + canvasOrigoX + ", " + canvasOrigoY + ")");
        final int fw = pyramid.getFractionWidth();
        final int fh = pyramid.getFractionHeight();
        final int height = fh-fw;

        for (int fy = height ; fy < fh ; fy++) {
            for (int fx = 0; fx < pyramid.getFractionWidth(); fx++) {
                pyramid.copyPixels(level, fx, fy, canvas,
                                   canvasOrigoX+fx*pTileEdge, canvasOrigoY+(fy-height)*pTileEdge, edge, dynamic);
            }
        }

//        debugRect(canvas, canvasOrigoX, canvasOrigoY, canvasOrigoX+2*pTileEdge, canvasOrigoY+2*pTileEdge, 100);
    }

    // Render bottom 1/3 of pyramidTop and top 1/3 of pyramidBottom, the result should be square
    private void renderDual(PyramidGrey23 pyramidTop, PyramidGrey23 pyramidBottom, final int level, final int[] canvas,
                              final int canvasOrigoX, final int canvasOrigoY, int dynamicTop, int dynamicBottom) {
        if (level == 0) {
            pyramidTop.copyPixels(1, 0, 1, canvas, canvasOrigoX, canvasOrigoY, edge, dynamicTop); // Should really be average
            return;
        }
        final int pTileEdge = pyramidTop.getTileEdge(level);
//        log.debug("Render pyramid(edge=" + pTileEdge + ") -> canvas(" + canvasOrigoX + ", " + canvasOrigoY + ")");
        final int fw = pyramidTop.getFractionWidth();
        final int fh = pyramidTop.getFractionHeight();
        final int height = fh-fw;

        // Bottom 1/3 of pyramidTop
        for (int fy = fw ; fy < fh ; fy++) {
            for (int fx = 0; fx < fw; fx++) {
                pyramidTop.copyPixels(level, fx, fy, canvas,
                                      canvasOrigoX+fx*pTileEdge, canvasOrigoY+(fy-fw)*pTileEdge, edge, dynamicTop);
            }
        }
        // Top 1/3 of pyramidBottom
        for (int fy = 0 ; fy < fh-fw ; fy++) {
            for (int fx = 0; fx < fw; fx++) {
                pyramidBottom.copyPixels(
                        level, fx, fy, canvas,
                        canvasOrigoX+fx*pTileEdge, canvasOrigoY+(fy+height)*pTileEdge, edge, dynamicBottom);
            }
        }
        //        debugRect(canvas, canvasOrigoX, canvasOrigoY, canvasOrigoX+2*pTileEdge, canvasOrigoY+2*pTileEdge, 50);
    }


    // Renders the 6 pyramid sub-tiles at the given position of the canvas
    private void render(PyramidGrey23 pyramid, final int level, final int[] canvas,
                        final int canvasOrigoX, final int canvasOrigoY, int dynamic) {
        if (level == 0) {
            renderZero(pyramid, canvas, canvasOrigoX, canvasOrigoY);
            return;
        }
        final int pTileEdge = pyramid.getTileEdge(level);
//        log.debug("Render pyramid(edge=" + pTileEdge + ") -> canvas(" + canvasOrigoX + ", " + canvasOrigoY + ")");

        for (int fy = 0 ; fy < pyramid.getFractionHeight() ; fy++) {
            for (int fx = 0; fx < pyramid.getFractionWidth(); fx++) {
                pyramid.copyPixels(level, fx, fy, canvas, canvasOrigoX+fx*pTileEdge, canvasOrigoY+fy*pTileEdge, edge,
                                   dynamic);
            }
        }
    }

    private void renderZero(PyramidGrey23 pyramid, final int[] canvas, final int canvasOrigoX, final int canvasOrigoY) {
        for (int fy = 0 ; fy < 2 ; fy++) {
            // TODO: Special-case the zero and avoid the ugly default missingReplacement
            pyramid.copyPixels(1, 0, fy, canvas, canvasOrigoX, canvasOrigoY+fy, edge, Util.FILL_COLOR_INT);
        }
    }

}
