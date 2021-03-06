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
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
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
    private static final DecimalFormat MS = new DecimalFormat("#0.00");

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
                String message = "Fatal exception when initializing Prime";
                log.fatal(message + ". nrtmosaic will not be able to start", e);
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
        log.info("Prime constructed in " + MS.format((System.nanoTime() - startTime) / 1000000.0) + "ms");
    }

    private static final Pattern DEEPZOOM = Pattern.compile("(.*)/([0-9]+)/([0-9]+)_([0-9]+)(.*)");
    public BufferedImage deepzoom(String deepZoomSnippet, String gam, String cnt) throws IOException {
        return deepzoom(deepZoomSnippet, gam, cnt, false, false, null);
    }
    public BufferedImage deepzoom(String deepZoomSnippet, String gam, String cnt, boolean pad, boolean border,
                                  Integer dynamicGrey) throws IOException {

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
        PyramidGrey23 pyramid = keeper.getPyramid(deepZoomSnippet);
        if (pyramid == null && Config.getBool("prime.onlyallowknown")) {
            throw new IllegalArgumentException("Requested DZI for unknown pyramid with query " + deepZoomSnippet);
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
            result = deepZoomBasic(deepZoomSnippet, fx, fy, level, gam, cnt, pad, dynamicGrey);
        }
        if (border) {
            Util.drawBorder(result);
        }
        return result;
    }

    // Topmost levels where NRTMosaic works as a plain image server
    private BufferedImage deepZoomBasic(String deepZoomSnippet, long fx, long fy, int level, String gam, String cnt,
                                        boolean pad, Integer dynamicGrey) throws IOException {
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
                return Util.getBlankTile(keeper.getFillGrey(deepZoomSnippet, dynamicGrey));
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
            return Util.pad(unpadded, edge, edge, keeper.getFillGrey(deepZoomSnippet, dynamicGrey));
        } catch (IIOException e) {
            if (pad) {
                log.debug("No basic tile at '" + deepZoomSnippet + "' but pad==true so default blank is returned");
                return Util.getBlankTile(keeper.getFillGrey(deepZoomSnippet, dynamicGrey));
            }
            throw new IIOException("Unable to read '" + external + "' as an image", e);
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("deepzoom basic tile for " + deepZoomSnippet + ", pad=" + pad + " piped in " +
                          MS.format((System.nanoTime() - startTime) / 1000000.0) + "ms");
            }
        }
    }

    // Middle level where NRTMosaic renders tiles
    private BufferedImage deepzoomRender(
            String pre, long fx, long fy, int level, String post, String gam, String cnt) {
        log.trace("deepzoom render tile for " + pre + ", " + fx + "x" + fy + ", level " + level);
        final long startTime = System.nanoTime();
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

        BufferedImage image = tile.renderImage(renderFX, renderFY, renderLevel, null);
        log.debug("deepzoom render from " + pre + " " + fx + "x" + fy + ", level " + level +
                  " in " + MS.format((System.nanoTime()-startTime)/1000000.0) + "ms");
        return image;
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
                  basicSnippet + " in " + MS.format((System.nanoTime()-startTime)/1000000.0) + "ms");
        // TODO: Resolve tile to derive dynamic fill grey and send it forward to the basic
        return deepzoom(basicSnippet, gam, cnt, true, border, null);
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

    private String toExternalDZIURL(String deepZoom) {
        return IMAGE_SERVER + "?DeepZoom=" + deepZoom + ".dzi";
    }

    public TileProvider getTileProvider() {
        return tileProvider;
    }

    // https://openseadragon.github.io/examples/tilesource-dzi/

    // The problem here is that the size interpolated from the Pyramid source widths are quite imprecise and leads
    // to image artefacts.

    // Simple redirect to the external image server, with the slight twist that the size is inflated to trick
    // OpenSeadragon to accept deeper zoom
    public String getDZI(String deepZoom) {
        return scaledDZI(deepZoom, getRawDZI(deepZoom));

/*
        if (pyramid == null) {
            throw new NullPointerException("Unable to locate a pyramid for input '" + deepZoom + "'");
        }
        long dziFactor = Config.getLong("prime.dzifactor");
        log.info("Calc: " + pyramid.getSourceWidth() * dziFactor + "x" + pyramid.getSourceHeight() * dziFactor);
        final long width = (long) (Math.pow(2, dziFactor) * pyramid.getSourceWidth() * dziFactor);
        final long height = (long) (Math.pow(2, dziFactor) * pyramid.getSourceHeight() * dziFactor);
        // TODO: Add check for overflow with JavaScript Double.MAX_INTEGER
        return getDZIXML(width, height);*/
    }

    private String getRawDZI(String deepZoom) {
        PyramidGrey23 pyramid = keeper.getPyramid(deepZoom);
        // TODO: Enable the check below when the code has been tested
        if (pyramid == null && Config.getBool("prime.onlyallowknown")) {
            throw new IllegalArgumentException("Requested DZI for unknown pyramid with query " + deepZoom);
        }

        String externalDZI;
        URL externalURL;
        try {
            externalURL = new URL(toExternalDZIURL(deepZoom));
        } catch (MalformedURLException e) {
            String message = "Unable to derive external URL for '" + deepZoom + "'";
            log.warn(message, e);
            throw new IllegalArgumentException(message, e);
        }
        try {
            externalDZI = Util.fetchString(externalURL);
        } catch (IOException e) {
            String message = "Unable to resolve DZI from image server for request " + deepZoom;
            log.warn(message + " with derived external URL " + externalURL, e);
            throw new RuntimeException(message, e);
        }
        return externalDZI;
    }

    // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER
    private long JAVASCRIPT_MAX = 9007199254740991L;
    private String scaledDZI(String deepZoom, String DZI) {
        long dziFactor = (long) Math.pow(2, Config.getLong("prime.dzifactor"));
        long width = Long.parseLong(DZI.replaceFirst("(?s).*Width=\"([0-9]+)\".*", "$1"));
        long height = Long.parseLong(DZI.replaceFirst("(?s).*Height=\"([0-9]+)\".*", "$1"));
        if (width*dziFactor > JAVASCRIPT_MAX) {
            log.error(
                    "Problem scaling DZI for " + deepZoom + " as width " + width + " * dziFactor " + dziFactor + " is "
                    + width*dziFactor + ", which is larger than the JavaScript integer max of " + JAVASCRIPT_MAX +
                    ". The value will be rounded down to JavaScript max integer");
        }
        if (height*dziFactor > JAVASCRIPT_MAX) {
            log.error(
                    "Problem scaling DZI for " + deepZoom + " as width " + width + " * dziFactor " + dziFactor + " is "
                    + width*dziFactor + ", which is larger than the JavaScript integer max of " + JAVASCRIPT_MAX +
                    ". The value will be rounded down to JavaScript max integer");
        }
        return DZI.
                replaceFirst("(?s)(.*Width=\")([0-9]+)(\".*)",
                             "$1" + Long.toString(Math.min(width*dziFactor, JAVASCRIPT_MAX)) + "$3").
                replaceFirst("(?s)(.*Height=\")([0-9]+)(\".*)",
                             "$1" + Long.toString(Math.min(height*dziFactor, JAVASCRIPT_MAX)) + "$3");
    }

/*    private String getDZIXML(long width, long height) {
        long dziFactor = Config.getLong("prime.dzifactor");
        return String.format("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                             "<Image xmlns=\"http://schemas.microsoft.com/deepzoom/2008\"\n" +
                             "       Format=\"jpg\" Overlap=\"0\" TileSize=\"256\" >\n" +
                             "    <Size Width=\"%d\" Height=\"%d\"/>\n" +
                             "</Image>", width, height);
    }
  */

    public String getRandomImage() {
        return idToPath(keeper.getRandom().getID());
    }

    // http://localhost/iipsrv/iipsrv.fcgi?DeepZoom=/home/te/projects/nrtmosaic/sample/0024b52b-f96a-4d70-b0fa-cec3f1bb1c83.tif.dzi
    public String getRandomImageJSON() {
        String image = getRandomImage();
        String dzi = getRawDZI(image);
        log.debug("Got raw DZI '" + dzi + "'");
        try {
            long width = Long.parseLong(dzi.replaceFirst("(?s).*Width=\"([0-9]+)\".*", "$1"));
            long height = Long.parseLong(dzi.replaceFirst("(?s).*Height=\"([0-9]+)\".*", "$1"));

            return String.format("{ \"image\":\"%s\", \"width\":\"%d\", \"height\":\"%d\" }",
                                 image, width, height);
        } catch (NumberFormatException e) {
            throw new RuntimeException("NumberformatException extracting width and height from " + dzi, e);
        }
    }
}
