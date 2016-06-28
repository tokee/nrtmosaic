package dk.statsbiblioteket.nrtmosaic.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import dk.statsbiblioteket.nrtmosaic.Prime;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import dk.statsbiblioteket.nrtmosaic.service.exception.InternalServiceException;
import dk.statsbiblioteket.nrtmosaic.service.exception.InvalidArgumentServiceException;
import dk.statsbiblioteket.nrtmosaic.service.exception.ServiceException;


@Path("/")
public class NrtmosaicResource implements ServletContextListener {

    private static Log log = LogFactory.getLog(NrtmosaicResource.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        Prime.instance(); // Start everything
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Nothing to do right now
    }

    //"http://achernar/iipsrv/?GAM=2.0&CNT=1.1&DeepZoom=/avis-show/symlinks/${P}.jp2_files/8/0_0"

    //http://achernar/iipsrv/?GAM=2.0&CNT=1.1&DeepZoom=/avis-show/symlinks/5/3/8/6/5386aa42-92bd-4ffc-b4f4-ad69b0610d00.jp2.dzi/10/2_1.jpg
    //DeepZoom=/avis-show/symlinks/${P}.jp2_files/8/0_0
    @GET
    @Path("/image/iipsrv/")
    @Produces("image/jpeg")
    public Response getImage(@QueryParam("GAM") String gam, @QueryParam("CNT") String cnt,
                             @QueryParam("DeepZoom") String deepZoom) throws ServiceException {
        try {

            //Do something with gam, cnt deepZoom
            log.info("iipsrv called with GAM="+gam +" , CNT="+cnt +" ,DeepZoom="+deepZoom);
            BufferedImage image = renderSampleImage();

            ResponseBuilder response = Response.ok((Object) image);
            return response.build();
        } catch (Exception e) {
            throw handleServiceExceptions(e);
        }
    }


    @GET
    @Path("/image/deepzoom/")
    @Produces({"image/jpeg", "text/plain"})
    public Response getImageDeepzoom(@QueryParam("GAM") String gam, @QueryParam("CNT") String cnt,
                                     @QueryParam("DeepZoom") String deepZoom) throws ServiceException {
        try {
            if (deepZoom.contains(".dzi")) {
                return Response.ok(Prime.instance().getDZI(deepZoom), MediaType.TEXT_PLAIN).build();
            }

            BufferedImage image = Prime.instance().deepzoom(deepZoom, gam, cnt);
            if (image == null) {
                image = renderSampleImage();
            }
            return Response.ok(image).build();
        } catch (Exception e) {
            throw handleServiceExceptions(e);
        }
    }

    @GET
    @Path("/image/random/")
    @Produces("text/plain")
    public Response getRandomImage() throws ServiceException {
        try {
            return Response.ok(Prime.instance().getRandomImage()).build();
        } catch (Exception e) {
            throw handleServiceExceptions(e);
        }
    }

    @GET
    @Path("/image")
    @Produces("image/jpeg")
    public Response getImage(@QueryParam("source") String arcFilePath, @QueryParam("x") double x,
                             @QueryParam("y") double y, @QueryParam("z") double z) throws ServiceException {
        try {

            //Do somethign with source, x, y, z parameters and then delete renderSampleImage method

            BufferedImage image = renderSampleImage();

            ResponseBuilder response = Response.ok(image);
            return response.build();
        } catch (Exception e) {
            throw handleServiceExceptions(e);
        }
    }

    //
    private static BufferedImage renderSampleImage() {
        System.setProperty("java.awt.headless", "true");

        final int size = 100;

        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D gfx = img.createGraphics();
        gfx.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        gfx.setStroke(new BasicStroke(size / 40f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        gfx.setColor(Color.BLACK);
        gfx.setBackground(Color.WHITE);
        gfx.clearRect(0, 0, size, size);

        int b = size / 30;
        gfx.drawOval(b, b, size - 1 - 2 * b, size - 1 - 2 * b);

        int esz = size / 7;
        int ex = (int) (0.27f * size);
        gfx.drawOval(ex, ex, esz, esz);
        gfx.drawOval(size - 1 - esz - ex, ex, esz, esz);

        b = size / 5;
        gfx.drawArc(b, b, size - 1 - 2 * b, size - 1 - 2 * b, 200, 140);

        return img;
    }

    private ServiceException handleServiceExceptions(Exception e) {
        log.warn("ServiceException", e);
        if (e instanceof ServiceException) {
            log.info("Handling serviceException:" + e.getMessage());
            return (ServiceException) e; // Do nothing, exception already correct
        } else if (e instanceof IllegalArgumentException) {
            log.error("ServiceException(HTTP 400) in Service:", e);
            return new InvalidArgumentServiceException(e.getMessage());
        } else {// SQL and other unforseen exceptions.... should not happen.
            log.error("ServiceException(HTTP 500) in Service:", e);
            return new InternalServiceException(e.getMessage());
        }
    }
}
