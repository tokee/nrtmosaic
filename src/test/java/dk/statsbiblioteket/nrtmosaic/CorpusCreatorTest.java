package dk.statsbiblioteket.nrtmosaic;

import junit.framework.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

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
public class CorpusCreatorTest {

    // Ideally the sample would be 256x384 pixels
//    public static final String SAMPLE = "http://achernar/iipsrv/?GAM=2.0&CNT=1.1&DeepZoom=/avis-show/symlinks/" +
//                                        "9/c/0/5/9c05d958-b616-47c1-9e4f-63ec2dd9429e.jp2_files/8/0_0.jpg";
    public static final String SAMPLE = "sample_9c05d958-b616-47c1-9e4f-63ec2dd9429e.jpg";

    public static final String[] SAMPLES = new String[] {
            "sample_02823b5f-223a-4124-9913-985cb5ad815f.jpg",
            "sample_18575b46-769a-47f3-95b5-789f9a2682fb.jpg",
            "sample_1cdc4cea-f1b5-423f-b9b7-6b5b51e3f206.jpg",
            "sample_207eb0ea-9d26-4379-9fb4-fb8d3094986c.jpg",
            "sample_2f5a3fa8-89f7-4e4e-abbc-085eb8dab2db.jpg",
            "sample_36c94cb5-56b4-43c9-9cb3-d1b5959fa2b6.jpg",
            "sample_3ec6edb8-8729-4f63-917e-aa635de34532.jpg",
            "sample_49e0b0ec-6fe6-48a9-9884-3c3957c4ebaa.jpg",
            "sample_580aacb8-a2d0-40d0-9957-485d5ced2170.jpg",
            "sample_5ecbabdb-e028-40b4-8ad5-6cdad770b55d.jpg",
            "sample_60cf4b52-cecf-43fa-9e42-468c16ff3d11.jpg",
            "sample_7b580441-082e-4c35-9b84-28356bf195c4.jpg",
            "sample_9c05d958-b616-47c1-9e4f-63ec2dd9429e.jpg",
            "sample_a4d19a5c-085b-4964-bffa-c4f13ea2bf56.jpg",
            "sample_ac00527d-bda4-4272-84ec-9c349231f050.jpg",
            "sample_b04576cf-e2b3-4154-a5c7-ec1b7cd60feb.jpg",
            "sample_b116c837-dfe4-4185-9f66-6d41dac22718.jpg",
            "sample_c036c9cc-ecac-4421-856c-af4ea01a1d7d.jpg",
            "sample_f9c37b95-25b7-4b9d-82ca-7dbda839bb32.jpg",
            "sample_ff2f9ece-9c79-4cb9-aa23-8e1e17655af3.jpg"};

    // Input images will be treated

    @Test
    public void testBreakdown() throws IOException {
        String EXPECTED_ID = "9c05d958-b616-47c1-9e4f-63ec2dd9429e".replace("-", "");
        PyramidCreator cc = new PyramidCreator();
        PyramidGrey23 pyramid = cc.breakDownImage(Thread.currentThread().getContextClassLoader().getResource(SAMPLE));
        Assert.assertEquals("The pyramid should have the right ID", EXPECTED_ID, pyramid.getID().toHex());
    }

    @Test
    public void testGenerateDefault() {
        CorpusCreator.generateCache();
    }

    // Simple crash/no-crash test
    @Test
    public void testBreakdowns() throws IOException {
        PyramidCreator cc = new PyramidCreator();
        Path root = Files.createTempDirectory("pyramid_test");
        for (String sample : SAMPLES) {
            String expectedID = sample.replace("-", "").replace("sample_", "").replace(".jpg", "");
            PyramidGrey23 pyramid =
                    cc.breakDownImage(Thread.currentThread().getContextClassLoader().getResource(sample));
            Assert.assertEquals("The pyramid should have the right ID for " + sample,
                                expectedID, pyramid.getID().toHex());
        }
    }

    public static Path createPyramidSample() throws IOException {
        PyramidCreator cc = new PyramidCreator();
        Path root = Files.createTempDirectory("pyramid_test");
        for (String sample : SAMPLES) {
            URL image = Thread.currentThread().getContextClassLoader().getResource(sample);
            PyramidGrey23 pyramid = cc.breakDownImage(image);
            pyramid.store(root);
        }
        return root;
    }
}