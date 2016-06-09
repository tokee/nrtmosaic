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

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Util {
    private static Log log = LogFactory.getLog(Util.class);

    // Tries local file, classloader and URL in that order
    public static URL resolveURL(String resource) {
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
