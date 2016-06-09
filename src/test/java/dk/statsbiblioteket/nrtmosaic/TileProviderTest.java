package dk.statsbiblioteket.nrtmosaic;

import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

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
public class TileProviderTest {

    private static final String SAMPLE_1 = "256/source_9c05d958-b616-47c1-9e4f-63ec2dd9429e_13_13_13.jpg";
    private static final String SAMPLE_W = "256/white.png";
    private static final String SAMPLE_B = "256/black.png";

    @Test
    public void testSampleTileBasic() throws InterruptedException, IOException {
        String SAMPLE = SAMPLE_1;
        TileProvider tp = new TileProvider();
        BufferedImage tile = tp.getTile(SAMPLE, 0, 0, 3);
    }

    @Test
    public void showSample() throws IOException, InterruptedException {
        show(SAMPLE_1);
    }

    private void show(String original) throws InterruptedException, IOException {
        TileProvider tp = new TileProvider();
        JDialog dialog = new JDialog();
        dialog.setTitle("Original -> Mosaic");
        dialog.getContentPane().setLayout(new GridLayout(3, 3));
        dialog.getContentPane().add(new JLabel(new ImageIcon(ImageIO.read(Util.resolveURL(original)))));
        for (int level = 1 ; level <= 8 ; level++) {
            dialog.getContentPane().add(new JLabel(new ImageIcon(tp.getTile(original, 0, 0, level))));
        }
        dialog.pack();
        dialog.setVisible(true);
        dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        Thread.sleep(1000000); // TODO: Add code to wait for window close
    }
}