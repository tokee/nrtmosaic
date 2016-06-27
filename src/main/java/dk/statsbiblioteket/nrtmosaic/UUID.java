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

    /**
     * Attempts to extract an 128bit ID, represented as 32 hex digits, from the input.
     * @param name a String representation of the resource.
     */
    public UUID(String name) {
        String normalised = name.toLowerCase().replace("-", "");
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UUID uuid = (UUID) o;
        return first64 == uuid.first64 && second64 == uuid.second64;
    }

    @Override
    public int hashCode() {
        int result = (int) (first64 ^ (first64 >>> 32));
        result = 31 * result + (int) (second64 ^ (second64 >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "UUID(" + toHex() + ")";
    }
}
