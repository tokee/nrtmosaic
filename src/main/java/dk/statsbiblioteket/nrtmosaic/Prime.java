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

import javax.imageio.IIOException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class Prime {
    private static Log log = LogFactory.getLog(Prime.class);
    private final Keeper keeper;

    private final TileProvider tileProvider;
    private final int FIRST_BASIC_LEVEL; // 8 at Statsbiblioteket
    private final int LAST_BASIC_LEVEL;
    private final int LAST_RENDER_LEVEL;
    private final int TURTLE_LEVEL;
    private final int edge;
    private final int FW;
    private final int FH;

    private final String IMAGE_SERVER;
    private final Pattern IMAGE_SERVER_PATH_REGEXP;
    private final String IMAGE_SERVER_PATH_REPLACEMENT;
    private BufferedImage TURTLE = null;

    private static Prime singleton;
    public static synchronized Prime instance() {
        if (singleton == null) {
            try {
                singleton = new Prime();
            } catch (Exception e) {
                log.fatal("Exception initializing Prime", e);
                throw e;
            }
        }
        return singleton;
    }

    private Prime() {
        log.debug("Constructing Prime in thread " + Thread.currentThread().getId());
        long startTime = System.nanoTime();
        if (Config.getInt("pyramid.maxlevel") == 0) { // Ugly hack. We need to request Config before Keeper!
            throw new IllegalArgumentException("Config stated pyramid.maxlevel=0. This should be > 0");
        }
        FIRST_BASIC_LEVEL = Config.getInt("prime.firstbasiclevel");
        LAST_BASIC_LEVEL = Config.getInt("prime.lastbasiclevel");
        LAST_RENDER_LEVEL = LAST_BASIC_LEVEL + Config.getInt("pyramid.maxlevel");
        CorpusCreator.generateCache();
        keeper = new Keeper();
        tileProvider = new TileProvider(keeper);
        edge = Config.getInt("tile.edge");
        FW = Config.imhotep.getFractionWidth();
        FH = Config.imhotep.getFractionHeight();
        IMAGE_SERVER = Config.getString("imageserver");
        IMAGE_SERVER_PATH_REGEXP = Pattern.compile(Config.getString("imageserver.path.regexp"));
        IMAGE_SERVER_PATH_REPLACEMENT = Config.getString("imageserver.path.replacement");

        TURTLE_LEVEL = Config.getInt("prime.turtlelevel");
        try {
            TURTLE = Util.toGrey(ImageIO.read(Util.resolveURL("turtle.png")), true);
            log.debug("Loaded turtle of size " + TURTLE.getWidth() + "x" + TURTLE.getHeight());
        } catch (NullPointerException | IOException e) {
            log.error("Unable to open turtle.png", e);
        }
        log.info("Prime constructed in " + (System.nanoTime()-startTime)/1000000 + "ms");
    }

    private static final Pattern DEEPZOOM = Pattern.compile("(.*)/([0-9]+)/([0-9]+)_([0-9]+)(.*)");
    public BufferedImage deepzoom(String deepZoomSnippet, String gam, String cnt) throws IOException {
        return deepzoom(deepZoomSnippet, gam, cnt, false, false);
    }
    public BufferedImage deepzoom(String deepZoomSnippet, String gam, String cnt, boolean pad, boolean border)
            throws IOException {

        // /avis-show/symlinks/9/c/0/5/9c05d958-b616-47c1-9e4f-63ec2dd9429e.jp2_files/0/0_0.jpg
        // Group 1                                                                    2 3 45
        // /avis-show/symlinks/9/c/0/5/9c05d958-b616-47c1-9e4f-63ec2dd9429e.jp2_files
        // 0
        // 0
        // 0
        // .jpg

        Matcher deepMatch = DEEPZOOM.matcher(deepZoomSnippet);
        if (!deepMatch.matches()) {
            throw new IllegalAccessError("The deepzoom request '" + deepZoomSnippet + "' could not be parsed");
        }
        final String pre = deepMatch.group(1);
        final int level = Integer.parseInt(deepMatch.group(2));
        final long fx = Long.parseLong(deepMatch.group(3));
        final long fy = Long.parseLong(deepMatch.group(4));
        final String post = deepMatch.group(5);

        BufferedImage result;
        if (level >= TURTLE_LEVEL && TURTLE != null) {
            log.debug("deepzoom level " + level + " >= " + TURTLE_LEVEL + ", returning turtle");
            result = TURTLE;
        } else if (level > LAST_RENDER_LEVEL) {
            result = deepzoomRedirect(pre, fx, fy, level, post, gam, cnt);
        } else if (level > LAST_BASIC_LEVEL) {
            result = deepzoomRender(pre, fx, fy, level, post, gam, cnt);
        } else {
            result = deepZoomBasic(deepZoomSnippet, fx, fy, level, gam, cnt, pad);
        }
        if (border) {
            Util.drawBorder(result);
        }
        return result;
    }

    // Topmost levels where NRTMosaic works as a plain image server
    private BufferedImage deepZoomBasic(String deepZoomSnippet, long fx, long fy, int level, String gam, String cnt,
                                        boolean pad) throws IOException {
        log.trace("deepzoom basic tile for " + deepZoomSnippet + ", pad=" + pad);
        final long startTime = System.nanoTime();
        PyramidGrey23 pyramid = keeper.getPyramid(deepZoomSnippet);

        if (pyramid != null) { // Check if the wanted tile is outside of the image pixels
            final int sourceW = pyramid.getSourceWidth();
            final int sourceH = pyramid.getSourceHeight();
            if (sourceW == 0 || pyramid.getSourceHeight() == 0) {
                log.warn("The pyramid for " + deepZoomSnippet + " has cached dimensions " + sourceW + "x" + sourceH);
            }
            final int zoomFactor = (int) (Math.pow(2, level - FIRST_BASIC_LEVEL));
            long maxExistingX = (long) zoomFactor * sourceW / Util.EDGE;
            long maxExistingY = (long) zoomFactor * sourceH / Util.EDGE;
/*        log.info(fx + "x" + fy + " at level " + level + ": level-FBL=" + (level-FIRST_BASIC_LEVEL) + ", p.sw=" +
                 sourceW + ", EDGE=" + Util.EDGE + ", 2^(l-FBL)=" + Math.pow(2, level-FIRST_BASIC_LEVEL) +
                 ", 2^(l-FBL)*p.sw=" + (Math.pow(2, level - FIRST_BASIC_LEVEL) * pyramid.getSourceWidth()) +
                 ", maxExistingX=" + maxExistingX);*/
            if (fx > maxExistingX || fy > maxExistingY) {
                log.trace("Basic image has no tile for " + fx + "x" + fy + " at level " + level + ". Returning blank");
                return Util.getBlankTile(keeper.getFillGrey(deepZoomSnippet));
            }
        }

        URL external = new URL(toExternalURL(gam, cnt, deepZoomSnippet));
        try {
            BufferedImage unpadded = ImageIO.read(external);
            if (unpadded == null) {
                throw new IOException("Unable to resolve external image '" + external + "'");
            }
            if (!pad) {
                return unpadded;
            }
            return Util.pad(unpadded, edge, edge, keeper.getFillGrey(deepZoomSnippet));
        } catch (IIOException e) {
            if (pad) {
                log.debug("No basic tile at '" + deepZoomSnippet + "' but pad==true so default blank is returned");
                return Util.getBlankTile(keeper.getFillGrey(deepZoomSnippet));
            }
            throw new IIOException("Unable to read '" + external + "' as an image", e);
        } finally {
            log.debug("deepzoom basic tile for " + deepZoomSnippet + ", pad=" + pad + " piped in " +
                      (System.nanoTime()-startTime)/1000000 + "ms");
        }
    }

    // Middle level where NRTMosaic renders tiles
    private BufferedImage deepzoomRender(
            String pre, long fx, long fy, int level, String post, String gam, String cnt) {
        log.debug("deepzoom render tile for " + pre + ", " + fx + "x" + fy + ", level " + level);
        final int zoomFactor = (int) Math.pow(2, level - LAST_BASIC_LEVEL);

        // Coordinates for the basic tile: ...3ec2dd9429e.jp2_files/LAST_BASIC_LEVEL/sourceFX_sourceFY
        final long basicFX = fx/zoomFactor;
        final long basicFY = fy/zoomFactor;
        Tile23 tile = tileProvider.getTile(toExternalURL(
                gam, cnt, pre + "/" + LAST_BASIC_LEVEL + "/" + basicFX + "_" + basicFY + post), true);

        // Upper left corner of the basic tile, measured in global coordinates
        final long origoFX = basicFX*zoomFactor;
        final long origoFY = basicFY*zoomFactor;

        // Upper left corner of the wanted sub-tile, inside of the basic tile. With level having basic leves as origo
        final int renderFX = (int) (fx - origoFX);
        final int renderFY = (int) (fy - origoFY);
        final int renderLevel = level - LAST_BASIC_LEVEL + 1;

        return tile.renderImage(renderFX, renderFY, renderLevel, null);
//            return TileProvider.getTileRender("/home/te/tmp/nrtmosaic/256/source_9c05d958-b616-47c1-9e4f-63ec2dd9429e_13_13_13.jpg", 0, 0, 1);
    }

    // /avis-show/symlinks/9/c/0/5/9c05d958-b616-47c1-9e4f-63ec2dd9429e.jp2_files
    // 0
    // 0
    // 0
    // .jpg

    // Bottom level where NRTMosaic passes tiles from the image server for different images
    private BufferedImage deepzoomRedirect(String pre, long fx, long fy, int level, String post, String gam, String cnt)
            throws IOException {
        log.trace("deepzoom redirect tile for " + pre + ", " + fx + "x" + fy + ", level " + level);
        final long startTime = System.nanoTime();

        final int zoomFactorToRender = (int) Math.pow(2, level - LAST_RENDER_LEVEL);

        // Coordinates for the wanted Pyramid in the render tile
        final int renderFX = (int) (fx / zoomFactorToRender);
        final int renderFY = (int) (fy / zoomFactorToRender);

        final int zoomFactorToBasic = (int) Math.pow(2, LAST_RENDER_LEVEL - LAST_BASIC_LEVEL);

        // Coordinates for the basic tile: ...3ec2dd9429e.jp2_files/LAST_BASIC_LEVEL/sFX_sourceFY
        final long basicFX = renderFX/zoomFactorToBasic;
        final long basicFY = renderFY/zoomFactorToBasic;
        Tile23 tile = tileProvider.getTile(toExternalURL(
                gam, cnt, pre + "/" + LAST_BASIC_LEVEL + "/" + basicFX + "_" + basicFY + post), true);

        final long basicOrigoFX = basicFX*zoomFactorToBasic;
        final long basicOrigoFY = basicFY*zoomFactorToBasic;

        // TODO: Mimick the logic from Tile23 renderTop, renderBottom and renderDual
        final int pyramidX = (int) (renderFX - basicOrigoFX);
        final int pyramidY = (int) (renderFY - basicOrigoFY);

        final int renderOrigoFX = renderFX*zoomFactorToRender;
        final int renderOrigoFY = renderFY*zoomFactorToRender;
        final int basicLevel = level-LAST_RENDER_LEVEL+FIRST_BASIC_LEVEL;
        final int basicHTiles = (int) Math.pow(2, level-LAST_RENDER_LEVEL);
        final int basicVTiles = basicHTiles/FW*FH;

        long redirectFX = fx-renderOrigoFX;
        long redirectFY = fy-renderOrigoFY;

        boolean border = false;
        PyramidGrey23 pyramid;
        switch (pyramidY * 2 % 3) {
            case 0:  // Top-down
                log.trace("Redirect getting top-down from " + pyramidX + "x" + pyramidY + " to level " + basicLevel +
                          " " + redirectFX + "x" + redirectFY);
                pyramid = tile.getPyramid(pyramidX, pyramidY);
                break;
            case 2:  // Middle
                redirectFY += basicHTiles;
                if (redirectFY < basicVTiles) { // Bottom of top pyramid
                    log.trace("Redirect middle getting top-bottom from " + pyramidX + "x" + (pyramidY - 1) + " to level " +
                              basicLevel + " " + redirectFX + "x" + redirectFY);
                    pyramid = tile.getPyramid(pyramidX, pyramidY+1);
                } else { // Top of bottom pyramid
                    log.trace("Redirect middle getting bottom-top from " + pyramidX + "x" + (pyramidY + 1) + " to level " +
                              basicLevel + " " + redirectFX + "x" + (redirectFY - basicVTiles));
                    pyramid = tile.getPyramid(pyramidX, pyramidY+1);
                    redirectFY -= basicVTiles;
                }
                //border = true; // Debugging
                break;
            case 1: // Bottom up
                redirectFY += basicVTiles-basicHTiles; // Upper part is already rendered
                log.trace("Redirect getting bottom-up from " + pyramidX + "x" + pyramidY + " to level " + basicLevel +
                          " " + redirectFX + "x" + redirectFY);
                pyramid = tile.getPyramid(pyramidX, pyramidY);
                break;
            default:
                throw new IllegalStateException("Modulo 3 should always result in 0, 1 or 2. Input was " + pyramidY*2);
        }

        // /avis-show/symlinks/9/c/0/5/9c05d958-b616-47c1-9e4f-63ec2dd9429e.jp2_files/0/0_0.jpg
        final String basicSnippet = toBasicDeepzoomSnippet(pyramid, redirectFX, redirectFY, level);
        log.debug("deepzoom redirect from " + pre + " " + fx + "x" + fy + ", level " + level + " to deepzoom " +
                  basicSnippet + " in " + (System.nanoTime()-startTime)/1000000+"ms");
        return deepzoom(basicSnippet, gam, cnt, true, border);
    }

    private String toBasicDeepzoomSnippet(PyramidGrey23 pyramid, long redirectFX, long redirectFY, int level) {
        return String.format("%s_files/%d/%d_%d.jpg",
                             idToPath(pyramid.getID()),
                             level - LAST_RENDER_LEVEL + FIRST_BASIC_LEVEL, redirectFX, redirectFY);
    }

    private String idToPath(UUID uuid) {
        Matcher matcher = IMAGE_SERVER_PATH_REGEXP.matcher(uuid.toHex());
        if (!matcher.matches()) {
            throw new IllegalStateException("The uuid " + uuid.toHex() + " did not match pattern " + matcher.pattern());
        }
        return matcher.replaceFirst(IMAGE_SERVER_PATH_REPLACEMENT);
    }

    private String toExternalURL(String gam, String cnt, String deepZoom) {
        String url = IMAGE_SERVER + "?GAM=" + gam + "&CNT=" + cnt + "&DeepZoom=" + deepZoom;
        log.trace("Redirecting to " + url);
        return url;
    }

    public TileProvider getTileProvider() {
        return tileProvider;
    }

}
