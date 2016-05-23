/* $Id:$
 *
 * WordWar.
 * Copyright (C) 2012 Toke Eskildsen, te@ekot.dk
 *
 * This is confidential source code. Unless an explicit written permit has been obtained,
 * distribution, compiling and all other use of this code is prohibited.    
  */
package dk.statsbiblioteket.nrtmosaic;

/**
 * Simple representation of a 128bit UUID.
 */
public class UUID {
    private final long first64;
    private final long second64;

    public UUID(long first64, long second64) {
        this.first64 = first64;
        this.second64 = second64;
    }

    public long getFirst64() {
        return first64;
    }

    public long getSecond64() {
        return second64;
    }

    public String toHex() {
        throw new UnsupportedOperationException("Not implemented yet. Remember to pad left");
    }
}
