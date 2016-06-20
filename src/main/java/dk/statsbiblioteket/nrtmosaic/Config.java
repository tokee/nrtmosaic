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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Config {
    private static Log log = LogFactory.getLog(Config.class);
    private static final String PROPS = "nrtmosaic.properties";
    private static final Properties conf;

    static { // Default values
        URL url = Thread.currentThread().getContextClassLoader().getResource(PROPS);
        try {
            if (url == null) {
                throw new RuntimeException("Unable to locate properties '" + PROPS + "'");
            }
            try (InputStream is = url.openStream()) {
                conf = new Properties();
                conf.load(is);
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to load properties from '" + url + "'", e);
        }
        /*
//        conf.put("pyramid.source", "nrtmosaic/sources.dat"); // List of URLs to use as source
//        conf.put("pyramid.cache", "nrtmosaic/cache");        // Where to store the cache
        conf.put("pyramid.source", "/home/te/tmp/nrtmosaic/sources.dat"); // List of URLs to use as source
        conf.put("pyramid.cache", "/home/te/tmp/nrtmosaic/cache");        // Where to store the cache
        conf.put("corpuscreator.overwrite", false);           // True if existing pyramid data should be overwritten
        conf.put("prime.firstbasiclevel", 8);
        conf.put("prime.lastbasiclevel", 13);
        conf.put("pyramid.maxlevel", 8);
        conf.put("tile.fillgrey", 0xEE);
        conf.put("tile.debuggrey", 0x99);
        conf.put("tile.edge", 256);
        conf.put("tile.cachesize", 100);*/
    }

    public static final PyramidGrey23 imhotep = new PyramidGrey23(CorpusCreator.MAX_LEVEL);
    public static Integer getInt(String key) {
        return Integer.parseInt(conf.getProperty(key));
    }
    public static Long getLong(String key) {
        return Long.parseLong(conf.getProperty(key));
    }
    public static String getString(String key) {
        return conf.getProperty(key);
    }
    public static Boolean getBool(String key) {
        return Boolean.parseBoolean(conf.getProperty(key));
    }
    public static Double getDouble(String key) {
        return Double.parseDouble(conf.getProperty(key));
    }



}
