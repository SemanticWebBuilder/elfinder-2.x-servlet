package cn.bluejoe.elfinder.controller.executors;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.json.JSONObject;

import cn.bluejoe.elfinder.controller.executor.AbstractJsonCommandExecutor;
import cn.bluejoe.elfinder.controller.executor.CommandExecutor;
import cn.bluejoe.elfinder.controller.executor.FsItemEx;
import cn.bluejoe.elfinder.impl.DefaultFsService;
import cn.bluejoe.elfinder.localfs.LocalFsVolume;
import cn.bluejoe.elfinder.service.FsItemFilter;
import cn.bluejoe.elfinder.service.FsService;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import javax.servlet.http.HttpSession;
import org.semanticwb.SWBPortal;

public class UploadCommandExecutor extends AbstractJsonCommandExecutor
        implements CommandExecutor {

    @Override
    public void execute(FsService fsService, HttpServletRequest request,
            ServletContext servletContext, JSONObject json) throws Exception {
        
        List<File> listFiles = (ArrayList<File>) request.getAttribute("filesInReq");
        List<FsItemEx> added = new ArrayList<FsItemEx>();
        List<Path> toConfirm = new ArrayList<Path>();
        String sessionId = request.getSession(true).getId();

        String target = request.getParameter("target");
        String altVolumeName = (String) request.getAttribute("altVolumeName");
        String attrib = request.getParameter("attrib");    //nombre de atributo de sesion para manejo de filtros
        Path pDest = null;
        FsItemEx dir = super.findItem(fsService, target);
        FsItemFilter filter = getRequestedFilter(request);
        
        if (null != attrib) {
            altVolumeName = this.getVolumeName(target, request.getSession(), attrib);
        }
        
        for (File fis : listFiles) {
            //fis.getName() returns full path such as 'C:\temp\abc.txt' in IE10
            //while returns 'abc.txt' in Chrome
            //see https://github.com/bluejoe2008/elfinder-2.x-servlet/issues/22
            
            System.out.println("Archivo en peticion:\n" + fis.getAbsolutePath());
            
            java.nio.file.Path p = java.nio.file.Paths.get(fis.getAbsolutePath());
            if (null == altVolumeName || altVolumeName.isEmpty()) {
                pDest = Paths.get(SWBPortal.getWorkPath()
                        .substring(0,
                            SWBPortal.getWorkPath().lastIndexOf("/") + 1),
                            dir.getPath(),
                            p.getFileName().toString());
            } else {
                LocalFsVolume volume = (LocalFsVolume) ((DefaultFsService) 
                    fsService).getVolume(altVolumeName);
                pDest = Paths.get(volume.getRootDir().getAbsolutePath(),
                        dir.getPath(), p.getFileName().toString());
            }
            
            System.out.println("Archivo destino:\n" + pDest.toAbsolutePath().toString());
            
            if (pDest.toFile().exists()) {
                toConfirm.add(p);
            } else {
                Files.move(p, pDest, StandardCopyOption.REPLACE_EXISTING);
            }
            
            FsItemEx newFile = new FsItemEx(dir, p.getFileName().toString());
//            FsItemEx newFile = new FsItemEx(dir, fileName);
            /*
             String fileName = fis.getName();
             FsItemEx newFile = new FsItemEx(dir, fileName);
             */
            if (filter.accepts(newFile)) {
                added.add(newFile);
            }
        }
        
        json.put("added", files2JsonArray(request, added));
        if (toConfirm.size() > 0) {
            request.getSession(true).setAttribute("files2Confirm" + sessionId, toConfirm);
            request.getSession(true).setAttribute("targetDir" + sessionId, dir);
            json.put("confirm", listToArray(toConfirm));
        }
    }
    
//    private String getFileName(String filePath) {
//        
//        String[] pathParts = filePath.toString().split("/");
//        
//        return pathParts[pathParts.length - 1];
//    }
    
    private String[] listToArray(List<Path> pathsToAdd) {
        
        String[] pathsArray = new String[pathsToAdd.size()];
        int cont = 0;
        for (Path path : pathsToAdd) {
            pathsArray[cont] = path.getFileName().toString();
            cont++;
        }
        return pathsArray;
    }
    
    /**
     * Obtiene el nombre del volumen a utilizar a partir del atributo en sesion con nombre {@code attrib}
     * y el {@code target}
     * @param target
     * @param session
     * @return 
     */
    private String getVolumeName(String target, HttpSession session, String attrib) {
        
        ArrayList<String> userPaths = (ArrayList) session.getAttribute(attrib);
        String volumeName = "";
        
        for (String allowedPath : userPaths) {
            String escapedPath = "vol" + allowedPath.replaceAll("/", "_");
            if (target.startsWith(escapedPath)){
                volumeName = escapedPath;
                break;
            }
        }
        return volumeName;
    }
}
