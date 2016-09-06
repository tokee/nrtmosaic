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

import javax.security.auth.login.Configuration;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public class Config {
    private static Log log = LogFactory.getLog(Config.class);
    public static final String NRTMOSAIC_HOME_KEY = "nrtmosaic.home";

    private static final String DEFAULT_PROPS = "nrtmosaic.default.properties";
    private static final String PROPS = "nrtmosaic.properties";
    private static final String home;
    private static final Properties conf;

    static { // Default values
        String buildHome = "";
        if (System.getenv(NRTMOSAIC_HOME_KEY) != null) {
            buildHome = System.getenv(NRTMOSAIC_HOME_KEY);
        } else if (System.getProperty(NRTMOSAIC_HOME_KEY) != null) {
            buildHome = System.getProperty(NRTMOSAIC_HOME_KEY);
        }
        if (!buildHome.isEmpty()) {
            if (!buildHome.endsWith("/")) {
                buildHome += "/";
            }
            log.info("Using '" + buildHome + "' as root for property resolving");
        }
        home = buildHome;

        URL urlD = resolveURL(DEFAULT_PROPS);
        Properties confL = null;
        if (urlD == null) {
            log.info("No default properties " + DEFAULT_PROPS + " found. Attempting override properties");
        } else {
            try {
                try (InputStream is = urlD.openStream()) {
                    confL = new Properties();
                    confL.load(is);
                }
                log.info("Loaded default properties from " + urlD);
            } catch (Exception e) {
                String message = "Unable to load default properties from '" + urlD + "'";
                log.fatal(message + ". nrtmosaic will not be able to start", e);
                throw new RuntimeException(message, e);
            }
        }

        log.debug("Attempting load of override properties from " + buildHome + PROPS);
        URL urlO = resolveURL(buildHome + PROPS);
        if (urlO == null) {
            if (confL == null) {
                String message = "Neither " + DEFAULT_PROPS + " nor " + buildHome + PROPS + " could be located";
                log.fatal(message + ". nrtmosaic will not be able to start");
                throw new IllegalStateException(message);
            }
            log.info("Only " + DEFAULT_PROPS + " available. No overrides loaded");
        } else {
            try {
                try (InputStream is = urlO.openStream()) {
                    if (confL == null) {
                        confL = new Properties();
                        confL.load(is);
                        log.info("Loaded default override properties only from " + urlO);
                    } else {
                        confL = new Properties(confL);
                        confL.load(is);
                        log.info("Loaded and layered overrides from " + urlO);
                    }
                }
            } catch (Exception e) {
                String message = "Unable to load override properties from '" + urlO + "'";
                log.fatal(message + ". nrtmosaic will not be able to start", e);
                throw new RuntimeException(message, e);
            }
        }
        conf = confL;
        /*
//        conf.put("pyramid.source", "nrtmosaic/sources.dat"); // List of URLs to use as source
//        conf.put("pyramid.cache", "nrtmosaic/cache");        // Where to store the cache
        conf.put("pyramid.source", "/home/te/tmp/nrtmosaic/sources.dat"); // List of URLs to use as source
        conf.put("pyramid.cache", "/home/te/tmp/nrtmosaic/cache");        // Where to store the cache
        conf.put("corpuscreator.overwrite", false);           // True if existing pyramid data should be overwritten
        conf.put("prime.firstbasiclevel", 8);
        conf.put("prime.lastbasiclevel", 13);
        conf.put("pyramid.maxlevel", 8);
        conf.put("tile.fill.grey", 0xEE);
        conf.put("tile.debuggrey", 0x99);
        conf.put("tile.edge", 256);
        conf.put("tile.cachesize", 100);*/
    }
    public static final PyramidGrey23 imhotep = new PyramidGrey23(Config.getInt("pyramid.maxlevel"));
    public static Integer getInt(String key) {
        return Integer.parseInt(conf.getProperty(key));
    }
    public static Long getLong(String key) {
        return Long.parseLong(conf.getProperty(key));
    }
    public static String getString(String key) {
        return expand(conf.getProperty(key));
    }
    public static Boolean getBool(String key) {
        return Boolean.parseBoolean(conf.getProperty(key));
    }
    public static Double getDouble(String key) {
        return Double.parseDouble(conf.getProperty(key));
    }

    public static Path getCacheRoot() {
        return Paths.get(getString("pyramid.cache"));
    }

    /**
     * Expand selected environment variables in the given String. Currently the list is {@link #NRTMOSAIC_HOME_KEY}.
     * Expansion is "smart", so "${nrtmosaic.home}subfolder" and "${nrtmosaic.home}/subfolder" are both expanded
     * to "thespecifiedhome/subfolder".
     */
    public static String expand(String str) {
        return str == null ? null : str.replaceAll("[$][{]nrtmosaic.home[}]/?", home);
    }

    // Ugly hack to have it here and in Util. Cyclic dependencies stinks!
    private static URL resolveURL(String resource) {
        try {
            Path file = Paths.get(resource);
            if (Files.exists(file)) {
                return file.toUri().toURL();
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Exception resolving '" + resource + "' as file", e);
        }

        URL url = Thread.currentThread().getContextClassLoader().getResource(resource);
        if (url != null) {
            return url;
        }

        try {
            return new URL(resource);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Exception resolving '" + resource + "' as URL", e);
        }
    }
}
