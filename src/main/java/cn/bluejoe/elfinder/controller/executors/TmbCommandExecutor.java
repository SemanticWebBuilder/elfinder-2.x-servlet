package cn.bluejoe.elfinder.controller.executors;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import javax.imageio.ImageIO;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cn.bluejoe.elfinder.controller.executor.AbstractCommandExecutor;
import cn.bluejoe.elfinder.controller.executor.CommandExecutor;
import cn.bluejoe.elfinder.controller.executor.FsItemEx;
import cn.bluejoe.elfinder.service.FsService;

import com.mortennobel.imagescaling.DimensionConstrain;
import com.mortennobel.imagescaling.ResampleOp;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;
import org.semanticwb.servlet.internal.DistributorParams;

public class TmbCommandExecutor extends AbstractCommandExecutor implements CommandExecutor {
    
    @Override
    public void execute(FsService fsService, HttpServletRequest request, HttpServletResponse response,
                    ServletContext servletContext, DistributorParams distParams) throws Exception {

        String target = request.getParameter("target");
        FsItemEx fsi = super.findItem(fsService, target);
        GregorianCalendar time = new GregorianCalendar();
        time.setTimeInMillis(fsi.getLastModified());
//        time.add(Calendar.DATE, 720); // = 2 * 360
        SimpleDateFormat format = new SimpleDateFormat("d MMM yyyy hh:mm:ss Z 00");
        
        response.setHeader("Last-Modified", format.format(time.getTime()));
        response.setHeader("Expires", format.format(time.getTime()));
        
        InputStream is = fsi.openInputStream();
        BufferedImage image = ImageIO.read(is);
        BufferedImage b = null;
        if (image != null) {
            int width = fsService.getServiceConfig().getTmbWidth();
            ResampleOp rop = null;
            //Si la imagen es mas pequeña que esto, se genera una excepcion
            if (image.getWidth() > 2 && image.getHeight() > 2) {
                rop = new ResampleOp(DimensionConstrain.createMaxDimension(width, -1));
                rop.setNumberOfThreads(4);
                b = rop.filter(image, null);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(b, "png", baos);
                byte[] bytesOut = baos.toByteArray();
            }
        }
        is.close();
        if (b != null) {
            ImageIO.write(b, "png", response.getOutputStream());
        }
    }
}
