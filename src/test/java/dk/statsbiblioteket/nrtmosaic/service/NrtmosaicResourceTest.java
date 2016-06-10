package dk.statsbiblioteket.nrtmosaic.service;

import dk.statsbiblioteket.nrtmosaic.Util;
import dk.statsbiblioteket.nrtmosaic.service.exception.ServiceException;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;


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
public class NrtmosaicResourceTest {

    @Test
    public void testPlainRequest() throws ServiceException {
        NrtmosaicResource resource = new NrtmosaicResource();
        resource.getImageDeepzoom(
                "2", "3", "/avis-show/symlinks/9/c/0/5/9c05d958-b616-47c1-9e4f-63ec2dd9429e.jp2_files/9/1_1.jpg");
    }

    @Test
    public void testExternalRender() throws ServiceException, IOException {
        String TILE = "/avis-show/symlinks/9/c/0/5/9c05d958-b616-47c1-9e4f-63ec2dd9429e.jp2_files/13/3_4.jpg";
        //String TILE = "/avis-show/symlinks/9/c/0/5/9c05d958-b616-47c1-9e4f-63ec2dd9429e.jp2_files/12/3_4.jpg";
        String external = "http://achernar/iipsrv/?DeepZoom=" + TILE;

        NrtmosaicResource resource = new NrtmosaicResource();
        BufferedImage source = ImageIO.read(new URL(external));
        BufferedImage render = resource.checkRedirect("2", "3", TILE);
        Util.show(source, render);
    }


}