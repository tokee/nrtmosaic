package dk.statsbiblioteket.nrtmosaic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Random;

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
public class KeeperTest {

    private Path lastRoot = null;

    // Intentionally disabled
    public void setUp() {
        try {
            lastRoot = CorpusCreatorTest.createPyramidSample();
        } catch (IOException e) {
            throw new RuntimeException("Unable to creste test pyramids", e);
        }
    }

    @After
    public void tearDown() {
        if (lastRoot != null) {
            try {
                Files.walkFileTree(lastRoot, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("IOException deleting " + lastRoot, e);
            }
        }
    }

    @Test
    public void testLoad() {
/*        if (lastRoot == null) {
            return;
        }*/
        //Keeper keeper = new Keeper(lastRoot, CorpusCreator.imhotep);
        Keeper keeper = new Keeper();
        assertTrue("There should be some pyramids in the keeper", keeper.size() > 0);
    }

    @Test
    public void showPyramid() throws InterruptedException {
        PyramidGrey23 pyramid = new Keeper().getClosestBottom(87, 87, new Random());
        show(pyramid);
    }

    private void show(PyramidGrey23 pyramid) throws InterruptedException {
        JDialog dialog = new JDialog();
        dialog.setTitle("Pyramid " + pyramid.getID());
        dialog.getContentPane().setLayout(new GridLayout(1, 1));
        dialog.getContentPane().add(new JLabel(scale(new ImageIcon(render(pyramid)), 3)));
        dialog.pack();
        dialog.setVisible(true);
        dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        Thread.sleep(1000000); // TODO: Add code to wait for window close
    }

    public BufferedImage render(PyramidGrey23 pyramid) {
        int width = 0;
        for (int i = 1; i <= pyramid.getMaxTileLevel(); i++) {
            width += pyramid.getFractionWidth() * pyramid.getTileEdge(i);
        }

        BufferedImage image = new BufferedImage(
                width, pyramid.getFractionHeight() * pyramid.getTileEdge(pyramid.getMaxTileLevel()),
                BufferedImage.TYPE_BYTE_GRAY);
        width = 0;
        for (int i = 1; i <= pyramid.getMaxTileLevel(); i++) {
            render(pyramid, i, image, width, 0);
            width += pyramid.getFractionWidth() * pyramid.getTileEdge(i);
        }
        return image;
    }

    private void render(PyramidGrey23 pyramid, int level, BufferedImage image, int origoX, int origoY) {
        int edge = pyramid.getTileEdge(level);
        for (int fy = 0; fy < pyramid.getFractionHeight(); fy++) {
            for (int fx = 0; fx < pyramid.getFractionWidth(); fx++) {
                renderTile(pyramid, level, fx, fy, image, origoX + fx * edge, origoY + fy * edge);
            }
        }
    }

    private void renderTile(PyramidGrey23 pyramid, int level, int fx, int fy,
                            BufferedImage image, int origoX, int origoY) {
        int edge = pyramid.getTileEdge(level);
        int[] pixels = new int[edge * edge];
        pyramid.copyPixels(level, fx, fy, pixels, 0, 0, edge);
        image.getRaster().setPixels(origoX, origoY, edge, edge, pixels);
    }

    private ImageIcon scale(ImageIcon imageIcon, int factor) {
        return new ImageIcon(imageIcon.getImage().getScaledInstance(
                imageIcon.getIconWidth() * factor, imageIcon.getIconHeight() * factor, Image.SCALE_FAST));
    }
}