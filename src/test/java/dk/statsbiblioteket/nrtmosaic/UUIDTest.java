package dk.statsbiblioteket.nrtmosaic;

import org.junit.Assert;
import org.junit.Test;

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
public class UUIDTest {

    @Test
    public void parse() {
        String[] TESTS = {"02823b5f-223a-4124-9913-985cb5ad815f"};
        for (String test: TESTS) {
            Assert.assertEquals(test.replace("-", ""), new UUID("foo_" + test + ".jpg").toHex());
        }
    }
}