package cn.bluejoe.elfinder.controller.executors;

import cn.bluejoe.elfinder.controller.ConnectorController;
import cn.bluejoe.elfinder.controller.executor.AbstractJsonCommandExecutor;
import cn.bluejoe.elfinder.controller.executor.CommandExecutor;
import cn.bluejoe.elfinder.controller.executor.FsItemEx;
import cn.bluejoe.elfinder.service.FsItemFilter;
import cn.bluejoe.elfinder.service.FsService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import org.json.JSONObject;
import org.semanticwb.SWBPortal;

/**
 *
 * @author jose.jimenez
 */
public class ConfirmCommandExecutor extends AbstractJsonCommandExecutor
        implements CommandExecutor {
    
    @Override
    public void execute(FsService fsService, HttpServletRequest request,
            ServletContext servletContext, JSONObject json) throws Exception {
        
        String sessionId = request.getSession(true).getId();
        //elementos a confirmar enviados al cliente
        ArrayList<Path> toConfirm = (ArrayList<Path>) request.getSession(true).getAttribute("files2Confirm" + sessionId);
        //directorio padre de los elementos a confirmar
        FsItemEx dir = (FsItemEx) request.getSession(true).getAttribute("targetDir" + sessionId);
        //elementos confirmados por el cliente
        String toCopy = request.getParameter("filenames");
        FsItemFilter filter = getRequestedFilter(request);
        List<FsItemEx> added = new ArrayList<FsItemEx>();
        List<String> filesToDelete = new ArrayList<String>(toConfirm.size());
        
        if (toCopy != null) {
            String[] files2Copy = toCopy.split(",");
            for (Path pathFrom : toConfirm) {
                boolean confirmed = false;
                for (String fileName : files2Copy) {
                    //si el elemento confirmado existe en los almacenados en sesion, se actualiza con el archivo cargado
                    if (pathFrom.getFileName().toString().equals(fileName)) {
                        java.nio.file.Path p = java.nio.file.Paths.get(ConnectorController.getTmpUpload(), fileName);
                        Path pDest = Paths.get(SWBPortal.getWorkPath().substring(0, SWBPortal.getWorkPath().lastIndexOf("/") + 1),
                                dir.getPath(), fileName);
                        Files.move(p, pDest, StandardCopyOption.REPLACE_EXISTING);
                        confirmed = true;
                        FsItemEx newFile = new FsItemEx(dir, p.getFileName().toString());
                        if (filter.accepts(newFile)) {
                            added.add(newFile);
                            //toConfirm.remove(pathFrom);
                        }
                    }
                }
                if (!confirmed) {
                    filesToDelete.add(pathFrom.getFileName().toString());
                }
            }
        }
        
        //se debe eliminar los archivos no confirmados por el usuario, del directorio de carga
        for (String toDelete : filesToDelete) {
            Path deletionPath = Paths.get(ConnectorController.getTmpUpload(), toDelete);
            java.io.File file2Delete = deletionPath.toFile();
            if (file2Delete.exists()) {
                if (!file2Delete.delete()) {
                    //Si se elimino, no hay mas que hacer
                    System.err.print("File " + deletionPath.toString() + " could not be deleted!");
                }
            }
        }
        json.put("added", files2JsonArray(request, added));
        json.put("changed", files2JsonArray(request, added));
        request.getSession(true).removeAttribute("files2Confirm" + sessionId);
    }
}
