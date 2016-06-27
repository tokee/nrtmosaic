/* $Id:$
 *
 * WordWar.
 * Copyright (C) 2012 Toke Eskildsen, te@ekot.dk
 *
 * This is confidential source code. Unless an explicit written permit has been obtained,
 * distribution, compiling and all other use of this code is prohibited.    
  */
package dk.statsbiblioteket.nrtmosaic;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Representations of a greyscale image of aspect ration 2:3 at different zoom levels.
 * </p><p>
 * The image consists of 6 tiles.
 * </p>
 */
public class PyramidGrey23 {
    private static Log log = LogFactory.getLog(PyramidGrey23.class);
    private final ByteBuffer data;
    private final int origo;
    private final int maxTileLevel; // 1:1x1, 2:2x2, 4:4x4, 5:16x16, 6:32:32, 7:64x64, 8:128x128
    private final int byteCount; // Number of significant bytes in data
    // Shared offsets for locating the right tiles in the data
    private static final int[] tileOffsets; // Offset is byte offset, aligned to mod 8

    private static final int[] tileEdges;

    private static final int IDPART1_INDEX =      0;
    private static final int IDPART2_INDEX =      IDPART1_INDEX+8;
    private static final int AVERAGE_GREY_INDEX = IDPART2_INDEX+8;
    private static final int WIDTH_INDEX =        AVERAGE_GREY_INDEX +1;
    private static final int HEIGHT_INDEX =       WIDTH_INDEX+2;
    private static final int TILE_START_INDEX =   HEIGHT_INDEX+2;

    static {
        tileOffsets = new int[18]; // Theoretically infinite, but we stop at 65K*65K pixel tiles
        tileEdges = new int[18];
        tileOffsets[0] = 0;  // Level 0: ID (128 bit / 16 bytes)
        tileEdges[0] = 0;

        tileOffsets[1] = TILE_START_INDEX; //  Level 1: 6*1*1
        tileEdges[1] = 1;
        int lastSize = 1;
        for (int level = 2 ; level < tileOffsets.length-1 ; level++) {
            tileOffsets[level] = tileOffsets[level-1] + 6*lastSize*lastSize;
            lastSize *= 2;
            tileEdges[level] = lastSize;
        }
    }

    public PyramidGrey23(int maxTileLevel) {
        this.maxTileLevel = maxTileLevel;
        this.byteCount = tileOffsets[maxTileLevel+1];
        this.data = ByteBuffer.allocate(byteCount);
        this.origo = 0;
    }
    private PyramidGrey23(byte[] data, int origo, int maxTileLevel) {
        this.maxTileLevel = maxTileLevel;
        this.byteCount = tileOffsets[maxTileLevel+1];
        this.data = ByteBuffer.wrap(data, origo, byteCount);
        this.origo = origo;
    }

    private PyramidGrey23(int maxTileLevel, Path dat) throws IOException {
        this.origo = 0;
        this.maxTileLevel = maxTileLevel;
        this.byteCount = tileOffsets[maxTileLevel+1];
        byte[] data = new byte[byteCount];

        try (FileInputStream fis = new FileInputStream(dat.toFile())) {
            int read = 0;
            while (read < byteCount) {
                int r = fis.read(data, origo+read, byteCount-read);
                if (r <= 0) {
                    break;
                }
                read += r;
            }
            if (read != byteCount) {
                throw new IOException("Expected " + byteCount + " bytes from '" + dat + "' but got only " + read);
            }
        }
        this.data = ByteBuffer.wrap(data, origo, byteCount);
    }

    public PyramidGrey23 createNew(Path dat) throws IOException {
        return new PyramidGrey23(maxTileLevel, dat);
    }
    public PyramidGrey23 createNew() {
        return new PyramidGrey23(maxTileLevel);
    }
    public PyramidGrey23 createNew(UUID id) {
        return new PyramidGrey23(maxTileLevel).setID(id);
    }


    public PyramidGrey23 setID(UUID id) {
        setLong(IDPART1_INDEX, id.getFirst64());
        setLong(IDPART2_INDEX, id.getSecond64());
        return this;
    }
    public void setAverageGrey(int averageGrey) {
        data.put(origo+ AVERAGE_GREY_INDEX, (byte)averageGrey);
    }
    public int getAverageGrey() {
        return 0xFF & data.get(origo+ AVERAGE_GREY_INDEX);
    }
    public void setSourceSize(int width, int height) {
        log.trace("Setting source size " + width + "x" + height);
        setShort(WIDTH_INDEX, width);
        setShort(HEIGHT_INDEX, height);
    }
    public int getSourceWidth() {
        return getShort(WIDTH_INDEX);
    }
    public int getSourceHeight() {
        return getShort(HEIGHT_INDEX);
    }

    private void setShort(int offset, long value) {
        for (int i = 0; i < 2; ++i) {
          data.put(origo+offset+i, (byte) (value >>> (2-i-1 << 3)));
        }
    }
    private short getShort(int offset) {
        long value = 0;
        for (int i = 0; i < 2; i++) {
            value |= (long)(0xFF&data.get(origo+offset+i)) << (2-i-1 << 3);
        }
        return (short) value;
    }
    public void setLong(int offset, long value) { // TODO: Change get & set of atomics to ByteBuffer native
        for (int i = 0; i < 8; ++i) {
          data.put(origo+offset+i, (byte) (value >>> (8-i-1 << 3)));
        }
    }
    public long getLong(int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value |= (long)(0xFF&data.get(origo+offset+i)) << (8-i-1 << 3);
        }
        return value;
    }

    public int getMaxTileLevel() {
        return maxTileLevel;
    }
    public int getFractionWidth() {
        return 2;
    }
    public int getFractionHeight() {
        return 3;
    }

    public UUID getID() {
        return new UUID(getLong(0), getLong(8));
    }

    //public byte[] getData() {
//        return data;
  //  }

    // 1: 1x1
    // 2: 2x2
    // 3: 4x4
    public int getTilesOffset(int level) {
//        if (level > maxTileLevel) {
//            throw new IllegalArgumentException("Requested tileLevel=" + level + " with maxTileLevel=" + maxTileLevel);
//        }
        return tileOffsets[level];
    }

    public int getTileOffset(int level, int fx, int fy) {
        final int edge = getTileEdge(level);
        final int blockSize = edge*edge;
        return getTilesOffset(level) + (fx * blockSize) + (fy * 2 * blockSize);
    }

    public int getTileEdge(int level) {
        return tileEdges[level];
    }

    public int getTopPrimary() { // ([0,0]+[0,1]+[1,0]+[1,1])/4
        final int index = origo+getTilesOffset(1);
        return ((0xFF&data.get(index)) + (0xFF&data.get(index+1)) + (0xFF&data.get(index+2)) +
                (0xFF&data.get(index+3))) / 4;
    }
    public int getTopSecondary() { // ([2,0]+[2,1])/4
        final int index = origo+getTilesOffset(1);
        return ((0xFF&data.get(index+4)) + (0xFF&data.get(index+5))) / 2;
    }
    public int getBottomPrimary() { // ([1,0]+[1,1]+[2,0]+[2,1])/4
        final int index = origo+getTilesOffset(1);
        return ((0xFF&data.get(index+2)) + (0xFF&data.get(index+3)) + (0xFF&data.get(index+4)) +
                (0xFF&data.get(index+5))) / 4;
    }
    public int getBottomSecondary() { // ([0,0]+[0,1])/4
        final int index = origo+getTilesOffset(1);
        return ((0xFF&data.get(index)) + (0xFF&data.get(index+1))) / 2;
    }

    @Override
    public String toString() {
        return "PyramidGrey23(id=" + getID().toHex() + ", primary=" + getTopPrimary() + ")";
    }

    public boolean store(Path root) throws IOException {
        return store(root, false);
    }
    public synchronized boolean store(Path root, boolean overwrite) throws IOException {
        final String hex = getID().toHex();

        final Path folder = root.resolve(hex.substring(0, 2)).resolve(hex.substring(2, 4));
        final Path full = folder.resolve(hex + ".dat");

        if (Files.exists(full)) {
            if (!overwrite) {
                log.info("Pyramid " + hex + " was already processed. Leaving untouched");
                return false;
            }
            log.info("Pyramid " + hex + " was already processed. Overwriting with new version");
            Files.delete(full);
        }
        if (!Files.exists(folder)) {
            Files.createDirectories(folder);
        }
        log.debug("Storing " + this + " as " + full);
        try (FileOutputStream fos = new FileOutputStream(full.toFile())) {
            WritableByteChannel channel = Channels.newChannel(fos);
            data.position(origo);
            channel.write(data);
//            fos.write(data.array(), origo, byteCount);
        }
        return true;
    }

    public Path getFullPath(Path root, UUID id) {
        String hex = id.toHex();
        Path folder = root.resolve(hex.substring(0, 2)).resolve(hex.substring(2, 4));
        return folder.resolve(hex + ".dat");
    }
    // Setting position does not seem thread safe
    public synchronized void setData(byte[] data, int level, int fx, int fy) {
        final int edge = getTileEdge(level);
        final int tileOffset = getTileOffset(level, fx, fy);
        final int blockSize = edge*edge;
//        log.debug(this.data.array().length + " - " + (origo+tileOffset) + " - " + blockSize);
        this.data.position(origo+tileOffset);
        this.data.put(data, 0, blockSize);
    }

    /**
     * Copies the given tile to the given position on the canvas.
     * @param level       zoom level.
     * @param fx          fraction X, must be less than {@link #getFractionWidth()}.
     * @param fy          fraction Y, must be less than {@link #getFractionHeight()}.
     * @param canvas      destination canvas.
     * @param origoX      upper left corner X.
     * @param origoY      upper left corner Y.
     * @param canvasWidth the width of the canvas (needed for calculating canvas y). Height is assumes to be the same.
     */
    public void copyPixels(int level, int fx, int fy, int[] canvas, int origoX, int origoY, int canvasWidth) {
        // TODO Special case level 0
        final int tileEdge = getTileEdge(level);
        final int tileOffset = getTileOffset(level, fx, fy);
        for (int ty = 0 ; ty < tileEdge ; ty++) {
            for (int tx = 0; tx < tileEdge; tx++) {
                if (origoX+tx < canvasWidth) {
                    final int canvasIndex = (origoY + ty) * canvasWidth + origoX + tx;
                    final int dataIndex = origo + tileOffset + (ty * tileEdge) + tx;
                    // Overflow is clipped
                    if (canvasIndex < canvas.length && canvasIndex >= 0 && dataIndex < origo+byteCount
                        && dataIndex >= 0) {
                        canvas[canvasIndex] = 0xFF & data.get(dataIndex);
                    }
                }
            }
        }
    }
}
