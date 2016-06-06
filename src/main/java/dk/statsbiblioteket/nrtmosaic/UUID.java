/* $Id:$
 *
 * WordWar.
 * Copyright (C) 2012 Toke Eskildsen, te@ekot.dk
 *
 * This is confidential source code. Unless an explicit written permit has been obtained,
 * distribution, compiling and all other use of this code is prohibited.    
  */
package dk.statsbiblioteket.nrtmosaic;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple representation of a 128bit UUID.
 */
public class UUID {
    private final long first64;
    private final long second64;
    private static final Pattern hex32 = Pattern.compile(".*([a-f0-9]{32})[.].*");

    public UUID(long first64, long second64) {
        this.first64 = first64;
        this.second64 = second64;
    }

    // Tries to locate an 128bit ID, represented as 32 hex sigits, is the input
    public UUID(String name) {
        String normalised = name.toLowerCase().replace("-", "").replace("_", "");
        Matcher matcher = hex32.matcher(normalised);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Unable to extract 32 digit hex from '" + name + "'");
        }
        String match = matcher.group(1); // hex is unsigned 128bit, så we need the shift trick
        first64 = (Long.parseLong(match.substring(0, 8), 16) << 32) | Long.parseLong(match.substring(8, 16), 16);
        second64 = (Long.parseLong(match.substring(16, 24), 16) << 32) | Long.parseLong(match.substring(24, 32), 16);
    }

    public long getFirst64() {
        return first64;
    }

    public long getSecond64() {
        return second64;
    }

    public String toHex() {
        return h8(first64>>>32) + h8(first64 & ~(~1L << 31)) +
               h8(second64>>>32) + h8(second64 & ~(~1L << 31));
    }

    private String h8(long value) {
        String in = Long.toString(value, 16);
        while (in.length() < 8) {
            in = "0" + in;
        }
        return in;
    }
}
