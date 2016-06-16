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
    private final int LAST_BASIC_LEVEL;
    private final int LAST_RENDER_LEVEL;
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
        LAST_BASIC_LEVEL = Config.getInt("prime.lastbasiclevel");
        LAST_RENDER_LEVEL = LAST_BASIC_LEVEL + Config.getInt("pyramid.maxlevel");
        CorpusCreator.generateCache();
        keeper = new Keeper();
        tileProvider = new TileProvider(keeper);
        log.info("Prime constructed in " + (System.nanoTime()-startTime)/1000000 + "ms");
    }

    private static final Pattern DEEPZOOM = Pattern.compile("(.*)/([0-9]+)/([0-9]+)_([0-9]+)(.*)");
    public BufferedImage deepzoom(String deepZoomSnippet, String gam, String cnt) throws IOException {

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
        return deepZoomBasic(deepZoomSnippet, gam, cnt);
    }

    // Topmost levels where NRTMosaic works as a plain image server
    private BufferedImage deepZoomBasic(String deepZoomSnippet, String gam, String cnt) throws IOException {
        return ImageIO.read(new URL(toExternalURL(gam, cnt, deepZoomSnippet)));
    }

    // Middle level where NRTMosaic renders tiles
    private BufferedImage deepzoomRender(
            String pre, int fx, int fy, int level, String post, String gam, String cnt) {
        log.trace("Rendering tile for " + pre + ", " + fx + "x" + fy + ", level " + level);
        final int zoomFactor = (int) Math.pow(2, level - LAST_BASIC_LEVEL);
        final int sourceFX = fx/zoomFactor;
        final int sourceFY = fy/zoomFactor;

        final int origoFX = sourceFX*zoomFactor;
        final int origoFY = sourceFY*zoomFactor;
        String external = toExternalURL(
                gam, cnt, pre + "/" + LAST_BASIC_LEVEL + "/" + sourceFX + "_" + sourceFY + post);
        Tile23 tile = tileProvider.getTile(external);
        return tile.renderImage(fx - origoFX, fy - origoFY, level - LAST_BASIC_LEVEL + 1, null);
//            return TileProvider.getTileRender("/home/te/tmp/nrtmosaic/256/source_9c05d958-b616-47c1-9e4f-63ec2dd9429e_13_13_13.jpg", 0, 0, 1);
    }

    // /avis-show/symlinks/9/c/0/5/9c05d958-b616-47c1-9e4f-63ec2dd9429e.jp2_files
    // 0
    // 0
    // 0
    // .jpg

    // Bottom level where NRTMosaic passes tiles from the image server for different images
    private BufferedImage deepzoomRedirect(String pre, int fx, int fy, int level, String post, String gam, String cnt) {
        log.trace("Redirecting tile for " + pre + ", " + fx + "x" + fy + ", level " + level);
        Tile23 tile;
        {
            // Get render-tile
            final int zoomFactor = (int) Math.pow(2, level - LAST_BASIC_LEVEL);
            final int sourceFX = fx / zoomFactor;
            final int sourceFY = fy / zoomFactor;
            String external = toExternalURL(
                    gam, cnt, pre + "/" + LAST_BASIC_LEVEL + "/" + sourceFX + "_" + sourceFY + post);
            tile = tileProvider.getTile(external);

            // Locate the right Pyramid in the Tile
            final int origoFX = sourceFX * zoomFactor;
            final int origoFY = sourceFY * zoomFactor;

        }

        throw new UnsupportedOperationException("Level " + level + " zoom not implemented yet");
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
