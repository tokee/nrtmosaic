/* $Id:$
 *
 * WordWar.
 * Copyright (C) 2012 Toke Eskildsen, te@ekot.dk
 *
 * This is confidential source code. Unless an explicit written permit has been obtained,
 * distribution, compiling and all other use of this code is prohibited.    
  */
package dk.statsbiblioteket.nrtmosaic;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

/**
 * Representations of a greyscale image of aspect ration 2:3 at different zoom levels.
 * </p><p>
 * The image consists of 6 tiles.
 * </p>
 */
public class PyramidGrey23 {
    private static Log log = LogFactory.getLog(PyramidGrey23.class);
    private final long[] data;
    private final int offset;
    private final long maxTileLevel;

    // Shared offsets for locating the right tiles in the data
    private static int[] tileOffsets; // Offset is byte offset, aligned to mod 8
    static {
        tileOffsets = new int[18]; // Theoretically infinite, but we stop at 65K*65K pixel tiles
        tileOffsets[0] = 0;  // Level 0: ID (128 bit)
        tileOffsets[1] = 16; // Level 1: 6*1*1
        int lastSize = 1;
        for (int level = 2 ; level < tileOffsets.length-1 ; level++) {
            tileOffsets[level] = tileOffsets[level-1] + 6*lastSize*lastSize;
            while (tileOffsets[level] % 8 != 0) { // Dumb-slow, but we only do this once
                level++;
            }
            lastSize++;
        }
    }

    public PyramidGrey23(int maxTileLevel) {
        this.data = new long[tileOffsets[maxTileLevel+1]/8+1]; // TODO: Verify exact boundaries
        this.offset = 0;
        this.maxTileLevel = maxTileLevel;
    }

    public PyramidGrey23(long[] data, int offset, int maxTileLevel) {
        this.data = data;
        this.offset = offset;
        this.maxTileLevel = maxTileLevel;
    }

    public void setID(UUID id) {
        data[offset] =   id.getFirst64();
        data[offset+1] = id.getSecond64();
    }

    public UUID getID() {
        return new UUID(data[offset], data[offset+1]);
    }

    public long[] getData() {
        return data;
    }

    // 1: 1x1
    // 2: 2x2
    // 3: 4x4
    public int getTileOffset(int level) {
        return tileOffsets[level];
    }

}
