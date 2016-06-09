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
    private final byte[] data;
    private final int origo;
    private final int maxTileLevel; // 1:1x1, 2:2x2, 4:4x4, 5:16x16, 6:32:32, 7:64x64, 8:128x128

    // Shared offsets for locating the right tiles in the data
    private static final int[] tileOffsets; // Offset is byte offset, aligned to mod 8
    private static final int[] tileEdges;

    static {
        tileOffsets = new int[18]; // Theoretically infinite, but we stop at 65K*65K pixel tiles
        tileEdges = new int[18];
        tileOffsets[0] = 0;  // Level 0: ID (128 bit / 16 bytes)
        tileEdges[0] = 0;

        tileOffsets[1] = 16; // Level 1: 6*1*1
        tileEdges[1] = 1;
        int lastSize = 1;
        for (int level = 2 ; level < tileOffsets.length-1 ; level++) {
            tileOffsets[level] = tileOffsets[level-1] + 6*lastSize*lastSize;
            lastSize *= 2;
            tileEdges[level] = lastSize;
        }
    }

    public PyramidGrey23(int maxTileLevel) {
        this.data = new byte[tileOffsets[maxTileLevel+1]];
        this.origo = 0;
        this.maxTileLevel = maxTileLevel;
    }
    public PyramidGrey23  createNew() {
        return new PyramidGrey23(maxTileLevel);
    }

    private PyramidGrey23(byte[] data, int origo, int maxTileLevel) {
        this.data = data;
        this.origo = origo;
        this.maxTileLevel = maxTileLevel;
    }

    private PyramidGrey23(int maxTileLevel, Path dat) throws IOException {
        this(maxTileLevel);
        try (FileInputStream fis = new FileInputStream(dat.toFile())) {
            int offset = 0;
            while (offset < data.length) {
                offset += fis.read(data, offset, data.length - offset);
            }
        }
    }
    public PyramidGrey23 createNew(Path dat) throws IOException {
        return new PyramidGrey23(maxTileLevel, dat);
    }

    public void setID(UUID id) {
        setLong(0, id.getFirst64());
        setLong(8, id.getSecond64());
    }

    public void setLong(int offset, long value) {
        for (int i = 0; i < 8; ++i) {
          data[origo+offset+i] = (byte) (value >>> (8-i-1 << 3));
        }
    }
    public long getLong(int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value |= (long)(0xFF&data[origo+offset+i]) << (8-i-1 << 3);
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

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data, int level, int x, int y) {
        final int edge = getTileEdge(level);
        final int blockSize = edge*edge;
        System.arraycopy(data, 0, this.data, origo+getTileOffset(level, x, y), blockSize);
    }

    // 1: 1x1
    // 2: 2x2
    // 3: 4x4
    public int getTilesOffset(int level) {
//        if (level > maxTileLevel) {
//            throw new IllegalArgumentException("Requested tileLevel=" + level + " with maxTileLevel=" + maxTileLevel);
//        }
        return tileOffsets[level];
    }

    public int getTileOffset(int level, int x, int y) {
        final int edge = getTileEdge(level);
        final int blockSize = edge*edge;
        return getTilesOffset(level) + (x * blockSize) + (y * 2 * blockSize);
    }

    public int getTileEdge(int level) {
        return tileEdges[level];
    }

    public int getTopPrimary() { // ([0,0]+[0,1]+[1,0]+[1,1])/4
        final int index = origo+getTilesOffset(1);
        return ((0xFF&data[index]) + (0xFF&data[index+1]) + (0xFF&data[index+2]) + (0xFF&data[index+3])) / 4;
    }
    public int getTopSecondary() { // ([2,0]+[2,1])/4
        final int index = origo+getTilesOffset(1);
        return ((0xFF&data[index+4]) + (0xFF&data[index+5])) / 2;
    }
    public int getBottomPrimary() { // ([1,0]+[1,1]+[2,0]+[2,1])/4
        final int index = origo+getTilesOffset(1);
        return ((0xFF&data[index+2]) + (0xFF&data[index+3]) + (0xFF&data[index+4]) + (0xFF&data[index+5])) / 4;
    }
    public int getBottomSecondary() { // ([0,0]+[0,1])/4
        final int index = origo+getTilesOffset(1);
        return ((0xFF&data[index]) + (0xFF&data[index+1])) / 2;
    }

    @Override
    public String toString() {
        return "PyramidGrey23(id=" + getID().toHex() + ", primary=" + getTopPrimary() + ")";
    }

    public boolean store(Path root) throws IOException {
        return store(root, false);
    }
    public boolean store(Path root, boolean overwrite) throws IOException {
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
            fos.write(data);
        }
        return true;
    }

    public Path getFullPath(Path root, UUID id) {
        String hex = id.toHex();
        Path folder = root.resolve(hex.substring(0, 2)).resolve(hex.substring(2, 4));
        return folder.resolve(hex + ".dat");
    }

    public void copyPixels(int level, int fx, int fy, int[] canvas, int origoX, int origoY, int canvasEdge) {
        // TODO Special case level 0
        final int tileOffset = getTileOffset(level, fx, fy);
        final int tileEdge = getTileEdge(level);
        for (int ty = 0 ; ty < tileEdge ; ty++) {
            for (int tx = 0; tx < tileEdge; tx++) {
                canvas[(origoY+ty)*canvasEdge + origoX+tx] = 0xFF & data[tileOffset+(ty*tileEdge)+tx];
            }
        }
    }
}
