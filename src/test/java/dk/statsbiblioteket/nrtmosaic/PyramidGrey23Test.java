package dk.statsbiblioteket.nrtmosaic;

import org.junit.Test;

import static org.junit.Assert.*;

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
public class PyramidGrey23Test {

    @Test
    public void testLongs() {
        PyramidGrey23 pyramid = new PyramidGrey23(7);
        for (long value: new long[]{0, 255, 256, 257, Long.MAX_VALUE, Long.MIN_VALUE}) {
            pyramid.setLong(2, value);
            assertEquals("The set value should be read back correctly", value, pyramid.getLong(2));
        }
    }
}