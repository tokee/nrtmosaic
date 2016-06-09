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

import java.util.HashMap;
import java.util.Map;

public class Config {
    private static final Map<String, Object> conf = new HashMap<>();
    static { // Default values
        conf.put("pyramid.source", "nrtmosaic/sources.dat"); // List of URLs to use as source
        conf.put("pyramid.cache", "nrtmosaic/cache");       // Where to store the cache
        conf.put("pyramid.maxlevel", 8);
        conf.put("tile.fillgrey", 0xCC);
        conf.put("tile.edge", 256);
        conf.put("tile.cachesize", 100);
    }

    public static Integer getInt(String key) {
        return (Integer) conf.get(key);
    }
    public static Long getLong(String key) {
        return (Long) conf.get(key);
    }
    public static String getString(String key) {
        return (String) conf.get(key);
    }
    public static Boolean getBool(String key) {
        return (Boolean) conf.get(key);
    }
    public static Double getDouble(String key) {
        return (Double) conf.get(key);
    }

}
