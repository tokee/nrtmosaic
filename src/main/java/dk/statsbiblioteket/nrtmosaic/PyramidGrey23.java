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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Representations of a greyscale image of aspect ratio 2:3 at different zoom levels.
 * </p><p>
 * The image consists of 6 tiles.
 * </p><p>
 * A pixel of grey 0 means that the pixel was not present in the original image.
 * </p>
 */
public class PyramidGrey23 {
    private static final Log log = LogFactory.getLog(PyramidGrey23.class);

    private final ByteBuffer backingData;
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
    private static final int MISSING_PIXELS_FRAC= HEIGHT_INDEX+2; // Fraction = missingPixels/(edge*edge)*256
    private static final int TILE_START_INDEX =   MISSING_PIXELS_FRAC+1;

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
    public static final int MEM_DATA_LEVEL = 2; // Memory cache up to and including tile level 2 (2x2 * 6 pixels)
    private final int memDataSize = tileOffsets[MEM_DATA_LEVEL+2];
    private final byte[] memData = new byte[memDataSize];


    public PyramidGrey23(int maxTileLevel) {
        this.maxTileLevel = maxTileLevel;
        this.byteCount = tileOffsets[maxTileLevel+1];
        this.backingData = ByteBuffer.allocate(byteCount);
        this.origo = 0;
    }
/*    private PyramidGrey23(byte[] data, int origo, int maxTileLevel) {
        this.maxTileLevel = maxTileLevel;
        this.byteCount = tileOffsets[maxTileLevel+1];
        this.data = ByteBuffer.wrap(data, origo, byteCount);
        this.origo = origo;
    }*/

    private PyramidGrey23(int maxTileLevel, Path dat) throws IOException {
        this.origo = 0;
        this.maxTileLevel = maxTileLevel;
        this.byteCount = tileOffsets[maxTileLevel+1];
        byte[] data = new byte[byteCount];

        FileInputStream fis = null;
        try  {
            fis = new FileInputStream(dat.toFile());
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
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
        this.backingData = ByteBuffer.wrap(data, origo, byteCount);
        syncMemData();
    }

    public PyramidGrey23(MappedByteBuffer buffer, int origo, int maxTileLevel) {
        this.maxTileLevel = maxTileLevel;
        this.byteCount = tileOffsets[maxTileLevel+1];
        this.backingData = buffer;
        this.origo = origo;
        syncMemData();
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
    public PyramidGrey23 createNew(MappedByteBuffer buffer, int offset) {
        return new PyramidGrey23(buffer, offset, maxTileLevel);
    }

    public final void setByte(int index, byte value) {
        backingData.put(origo+index, value);
        if (index-origo < memDataSize) {
            memData[index-origo] = value;
        }
    }
    public final void setByte(int index, long value) {
        setByte(index, (byte)value);
    }
    public final void setByte(int index, int value) {
        setByte(index, (byte)value);
    }
    public final byte getByte(int index) {
        return index < memDataSize ? memData[index] : backingData.get(origo+index);
    }
    public final int getByteAsInt(int index) {
        return 0xFF & getByte(index);
    }
    public final long getByteAsLong(int index) {
        return 0xFF & getByte(index);
    }

    public PyramidGrey23 setID(UUID id) {
        setLong(IDPART1_INDEX, id.getFirst64());
        setLong(IDPART2_INDEX, id.getSecond64());
        return this;
    }
    public void setAverageGrey(int averageGrey) {
        setByte(AVERAGE_GREY_INDEX, averageGrey);
    }
    public int getAverageGrey() {
        return getByteAsInt(AVERAGE_GREY_INDEX);
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

    public void setMissingPixelsFraction(double fraction) {
        setByte(MISSING_PIXELS_FRAC, (int)(fraction*256));
    }
    public double getMissingPixelsFraction() {
        return getByteAsInt(MISSING_PIXELS_FRAC)/256;
    }

    private void setShort(int offset, long value) {
        for (int i = 0; i < 2; ++i) {
          setByte(offset+i, value >>> (2-i-1 << 3));
        }
    }
    private short getShort(int offset) {
        long value = 0;
        for (int i = 0; i < 2; i++) {
            value |= (long)(getByteAsInt(offset+i)) << (2-i-1 << 3);
        }
        return (short) value;
    }
    public void setLong(int offset, long value) { // TODO: Change get & set of atomics to ByteBuffer native
        for (int i = 0; i < 8; ++i) {
          setByte(offset+i, value >>> (8-i-1 << 3));
        }
    }
    public long getLong(int offset) {
        long value = 0;
        for (int i = 0; i < 8; i++) {
            value |= (long)(getByteAsInt(offset+i)) << (8-i-1 << 3);
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

    /**
     * @return the amount of significant bytes, containing all Pyramid data.
     */
    public int getBytecount() {
        return byteCount;
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
        return getMissingAwareAverage(getTilesOffset(1), 4);
    }
    public int getTopSecondary() { // ([2,0]+[2,1])/4
        return getMissingAwareAverage(getTilesOffset(1)+4, 2);
    }
    public int getBottomPrimary() { // ([1,0]+[1,1]+[2,0]+[2,1])/4
        return getMissingAwareAverage(getTilesOffset(1)+2, 4);
    }
    public int getBottomSecondary() { // ([0,0]+[0,1])/4
        return getMissingAwareAverage(getTilesOffset(1), 2);
    }
    private int getMissingAwareAverage(int offset, int length) {
        int count = 0;
        int sum = 0;
        for (int i = offset ; i < offset+length ; i++) {
            int grey = getByteAsInt(i);
            if (grey != Util.MISSING_GREY) {
                sum += grey;
                count++;
            }
        }
        return count == 0 ? Util.MISSING_GREY : sum/count;
    }

    // TODO: This does not take into account that topPrimary is likely to be less affectable than topSecondary
    public Range getPossibleAverages() {
        int overallAverage = getMissingAwareAverage(getTilesOffset(1), getFractionWidth()*getFractionHeight());
        if (overallAverage == Util.MISSING_GREY) {
            return new Range(0, 255);
        }
        // average = overallAverage*(1-getMissingPixelsFraction())+dynamic*getMissingPixelsFraction()
        return new Range(Math.max(0, (int) (overallAverage * (1 - getMissingPixelsFraction()))),
                         Math.min(255, (int) (overallAverage*(1-getMissingPixelsFraction()) +
                                              255*getMissingPixelsFraction())));
    }
    public int getDynamic(int wantedAverage) {
        int overallAverage = getMissingAwareAverage(getTilesOffset(1), getFractionWidth()*getFractionHeight());
        // wantedAverage = overallAverage*(1-getMissingPixelsFraction())+dynamic*getMissingPixelsFraction()
        double dynamicGrey = (wantedAverage-overallAverage*(1-getMissingPixelsFraction()))/getMissingPixelsFraction();
        return (int) Math.max(0, Math.min(255, dynamicGrey));
    }

    public static final class Range {
        public final int from;
        public final int to;

        public Range(int from, int to) {
            this.from = from;
            this.to = to;
        }
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
            log.info("Overwriting Pyramid " + hex + " with new version");
            Files.delete(full);
        } else {
            log.debug("Storing " + this + " as " + full);
        }
        if (!Files.exists(folder)) {
            Files.createDirectories(folder);
        }
        try (FileOutputStream fos = new FileOutputStream(full.toFile())) {
            WritableByteChannel channel = Channels.newChannel(fos);
            backingData.position(origo);
            channel.write(backingData); // TODO: Only write bytecount bytes
//            fos.write(data.array(), origo, byteCount);
        }
        return true;
    }

    /**
     * Write the data for the Pyramid to the given stream.
     * @return the amount of bytes written.
     */
    public synchronized int store(FileOutputStream outputStream) throws IOException {
        WritableByteChannel channel = Channels.newChannel(outputStream);
        backingData.position(origo);
        channel.write(backingData); // TODO: Only write bytecount bytes
        return byteCount;
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
        backingData.position(origo+tileOffset);
        backingData.put(data, 0, blockSize);
        syncMemData();
    }

    private synchronized void syncMemData() {
        backingData.position(origo);
        backingData.get(memData, 0, memDataSize);
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
     * @param missingReplacement if a pixel is marked as missing, it will be filled with this grey.
     */
    public void copyPixels(
            int level, int fx, int fy, int[] canvas, int origoX, int origoY, int canvasWidth, int missingReplacement) {
        // TODO Special case level 0
        final int tileEdge = getTileEdge(level);
        final int tileOffset = getTileOffset(level, fx, fy);
        for (int ty = 0 ; ty < tileEdge ; ty++) {
            for (int tx = 0; tx < tileEdge; tx++) {
                if (origoX+tx < canvasWidth) {
                    final int canvasIndex = (origoY + ty) * canvasWidth + origoX + tx;
                    final int dataIndex = tileOffset + (ty * tileEdge) + tx;
                    // Overflow is clipped
                    if (canvasIndex < canvas.length && canvasIndex >= 0 && dataIndex < byteCount
                        && dataIndex >= 0) {
                        int grey = getByteAsInt(dataIndex);
                        canvas[canvasIndex] = grey == Util.MISSING_GREY ? missingReplacement : grey;
                    }
                }
            }
        }
    }

}
