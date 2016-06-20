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
    private final int FIRST_BASIC_LEVEL;
    private final int LAST_BASIC_LEVEL;
    private final int LAST_RENDER_LEVEL;
    private final int edge;
    // Last level is REDIRECT

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
        log.info("Constructing Prime in thread " + Thread.currentThread().getId());
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
        log.info("Prime constructed in " + (System.nanoTime()-startTime)/1000000 + "ms");
    }

    private static final Pattern DEEPZOOM = Pattern.compile("(.*)/([0-9]+)/([0-9]+)_([0-9]+)(.*)");
    public BufferedImage deepzoom(String deepZoomSnippet, String gam, String cnt) throws IOException {
        return deepzoom(deepZoomSnippet, gam, cnt, false);
    }
    private BufferedImage deepzoom(String deepZoomSnippet, String gam, String cnt, boolean pad) throws IOException {

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
        final int fx = Integer.parseInt(deepMatch.group(3));
        final int fy = Integer.parseInt(deepMatch.group(4));
        final String post = deepMatch.group(5);

        if (level > LAST_RENDER_LEVEL) {
            return deepzoomRedirect(pre, fx, fy, level, post, gam, cnt);
        } else if (level > LAST_BASIC_LEVEL) {
            return deepzoomRender(pre, fx, fy, level, post, gam, cnt);
        }
        return deepZoomBasic(deepZoomSnippet, gam, cnt, pad);
    }

    // Topmost levels where NRTMosaic works as a plain image server
    private BufferedImage deepZoomBasic(String deepZoomSnippet, String gam, String cnt, boolean pad)
            throws IOException {
        URL external = new URL(toExternalURL(gam, cnt, deepZoomSnippet));
        try {
            BufferedImage unpadded = ImageIO.read(external);
            return pad ? Util.pad(unpadded, edge, edge) : unpadded;
        } catch (IIOException e) {
            throw new IIOException("Unable to read '" + external + "' as an image", e);
        }
    }

    // Middle level where NRTMosaic renders tiles
    private BufferedImage deepzoomRender(
            String pre, int fx, int fy, int level, String post, String gam, String cnt) {
        log.trace("Rendering tile for " + pre + ", " + fx + "x" + fy + ", level " + level);
        final int zoomFactor = (int) Math.pow(2, level - LAST_BASIC_LEVEL);

        // Coordinates for the basic tile: ...3ec2dd9429e.jp2_files/LAST_BASIC_LEVEL/sourceFX_sourceFY
        final int basicFX = fx/zoomFactor;
        final int basicFY = fy/zoomFactor;
        Tile23 tile = tileProvider.getTile(toExternalURL(
                gam, cnt, pre + "/" + LAST_BASIC_LEVEL + "/" + basicFX + "_" + basicFY + post));

        // Upper left corner of the basic tile, measured in global coordinates
        final int origoFX = basicFX*zoomFactor;
        final int origoFY = basicFY*zoomFactor;

        // Upper left corner of the wanted sub-tile, inside of the basic tile. With level having basic leves as origo
        final int renderFX = fx-origoFX;
        final int renderFY = fy-origoFY;
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
    private BufferedImage deepzoomRedirect(String pre, int fx, int fy, int level, String post, String gam, String cnt)
            throws IOException {
        log.debug("Redirecting tile for " + pre + ", " + fx + "x" + fy + ", level " + level);

        final int zoomFactorToRender = (int) Math.pow(2, level - LAST_RENDER_LEVEL);

        // Coordinates for the wanted Pyramid in the render tile
        final int renderFX = fx/zoomFactorToRender;
        final int renderFY = fy/zoomFactorToRender;

        final int zoomFactorToBasic = (int) Math.pow(2, LAST_RENDER_LEVEL - LAST_BASIC_LEVEL);

        // Coordinates for the basic tile: ...3ec2dd9429e.jp2_files/LAST_BASIC_LEVEL/sFX_sourceFY
        final int basicFX = renderFX/zoomFactorToBasic;
        final int basicFY = renderFY/zoomFactorToBasic;
        Tile23 tile = tileProvider.getTile(toExternalURL(
                gam, cnt, pre + "/" + LAST_BASIC_LEVEL + "/" + basicFX + "_" + basicFY + post));

        final int basicOrigoFX = basicFX*zoomFactorToBasic;
        final int basicOrigoFY = basicFY*zoomFactorToBasic;

        // TODO: Mimick the logic from Tile23 renderTop, renderBottom and renderDual
        final int pyramidX = renderFX-basicOrigoFX;
        final int pyramidY = renderFY-basicOrigoFY;

        final int renderOrigoFX = renderFX*zoomFactorToRender;
        final int renderOrigoFY = renderFY*zoomFactorToRender;
        final int basicLevel = level-LAST_RENDER_LEVEL+FIRST_BASIC_LEVEL;
        int redirectFX = fx-renderOrigoFX;
        int redirectFY = fy-renderOrigoFY;

        PyramidGrey23 pyramid;
        switch (pyramidY * 2 % 3) {
            case 0:  // Top-down
                log.info("Redirect getting top-down from " + pyramidX + "x" + pyramidY + " level " + basicLevel);
                pyramid = tile.getPyramid(pyramidX, pyramidY);
                break;
            case 2:  // Middle
                log.info("Redirect getting middle from " + pyramidX + "x" + (pyramidY-1) + " level " + basicLevel);
                pyramid = tile.getPyramid(pyramidX, pyramidY-1); // Just for now
                break;
            case 1: // Bottom up
                log.info("Redirect getting bottom-up from " + pyramidX + "x" + pyramidY + " level " + basicLevel);
                pyramid = tile.getPyramid(pyramidX, pyramidY);
                break;
            default:
                throw new IllegalStateException("Modulo 3 should always result in 0, 1 or 2. Input was " + pyramidY*2);
        }

        // /avis-show/symlinks/9/c/0/5/9c05d958-b616-47c1-9e4f-63ec2dd9429e.jp2_files/0/0_0.jpg
        final String basicSnippet = toBasicDeepzoomSnippet(pyramid, redirectFX, redirectFY, level);
        log.info("Resolved redirect from " + pre + " " + fx + "x" + fy + ", level " + level + " to deepzoom call " +
                 basicSnippet);
        return deepzoom(basicSnippet, gam, cnt, true);
    }

    private String toBasicDeepzoomSnippet(PyramidGrey23 pyramid, int redirectFX, int redirectFY, int level) {
        final String id = pyramid.getID().toHex();
        return "/avis-show/symlinks/" + id.substring(0, 1) + "/" + id.substring(1, 2) + "/" +
               id.substring(2, 3) + "/" + id.substring(3, 4) + "/" +
               id.substring(0, 8) + "-" + id.substring(8, 12) + "-" +
               id.substring(12, 16) + "-" + id.substring(16, 20) + "-" +
               id.substring(20, 32) +
               ".jp2_files/" + (level-LAST_RENDER_LEVEL+FIRST_BASIC_LEVEL) +
               "/" + redirectFX + "_" + redirectFY + ".jpg";
    }

    private String toExternalURL(String gam, String cnt, String deepZoom) {
        String url = "http://achernar/iipsrv/?GAM=" + gam + "&CNT=" + cnt + "&DeepZoom=" + deepZoom;
        log.trace("Redirecting to " + url);
        return url;
    }

    public TileProvider getTileProvider() {
        return tileProvider;
    }
}
