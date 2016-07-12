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

public class CorpusCreator {
    private static final Log log = LogFactory.getLog(CorpusCreator.class);
    private static boolean cacheGenerated = false;

    public static synchronized void generateCache() {
        if (cacheGenerated) {
            log.info("Corpus cache already generated");
            return;
        }
        cacheGenerated = true;

        try {
            PyramidCreator.create();
        } catch (Exception e) {
            final String message = "Exception during pyramid creation";
            log.fatal(message, e);
            throw new RuntimeException(message, e);
        }

        try {
            PyramidConcatenator.concatenate();
        } catch (Exception e) {
            final String message = "Exception during pyramid concatenation";
            log.fatal(message, e);
            throw new RuntimeException(message, e);
        }
    }

}
