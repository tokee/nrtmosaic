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

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.Log;

/**
 *
 */
public class Prime {
    private static Log log = LogFactory.getLog(Prime.class);

    private final Keeper keeper;
    private final TileProvider tileProvider;

    private static Prime singleton;
    public static synchronized Prime instance() {
        if (singleton == null) {
            try {
                singleton = new Prime();
            } catch (Exception e) {
                log.fatal("Exception initializing Prime", e);
                throw e;
            }
        }
        return singleton;
    }

    private Prime() {
        log.info("Constructing Prime in thread " + Thread.currentThread().getId());
        long startTime = System.nanoTime();
        if (Config.getInt("pyramid.maxlevel") == 0) { // Ugly hack. We need to request Config before Keeper!
            throw new IllegalArgumentException("Config stated pyramid.maxlevel=0. This should be > 0");
        }
        CorpusCreator.generateCache();
        keeper = new Keeper();
        tileProvider = new TileProvider(keeper);
        log.info("Prime constructed in " + (System.nanoTime()-startTime)/1000000 + "ms");
    }

    public TileProvider getTileProvider() {
        return tileProvider;
    }
}
