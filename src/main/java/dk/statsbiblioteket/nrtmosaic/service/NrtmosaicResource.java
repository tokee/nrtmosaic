package dk.statsbiblioteket.nrtmosaic.service;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import dk.statsbiblioteket.nrtmosaic.service.exception.InternalServiceException;
import dk.statsbiblioteket.nrtmosaic.service.exception.InvalidArgumentServiceException;
import dk.statsbiblioteket.nrtmosaic.service.exception.ServiceException;


@Path("test")
public class NrtmosaicResource {

    private static Log log = LogFactory.getLog(NrtmosaicResource.class);

    @GET
    @Path("/image")
    @Produces("image/jpeg")
    public Response getImage(@QueryParam("source") String arcFilePath, @QueryParam("x") double x, @QueryParam("y") double y, @QueryParam("z") double z)
            throws ServiceException {
        try {
    
            //Do somethign with source, x, y, z parameters and then delete renderSampleImage method
            
            BufferedImage image = renderSampleImage();
                                    
                ResponseBuilder response = Response.ok((Object) image);
                return response.build();            
        } catch (Exception e) {            
            throw handleServiceExceptions(e);
        }
    }

    //
    private static BufferedImage renderSampleImage() {
        System.setProperty("java.awt.headless", "true");

        final int size = 100;

        BufferedImage img = new BufferedImage(size, size,
                BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D gfx = img.createGraphics();
        gfx.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        gfx.setStroke(new BasicStroke(size / 40f, BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND));

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