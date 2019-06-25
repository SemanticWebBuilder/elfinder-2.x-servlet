package cn.bluejoe.elfinder.controller.executors;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.json.JSONObject;

import cn.bluejoe.elfinder.controller.executor.AbstractJsonCommandExecutor;
import cn.bluejoe.elfinder.controller.executor.CommandExecutor;
import cn.bluejoe.elfinder.controller.executor.FsItemEx;
import cn.bluejoe.elfinder.service.FsService;
import cn.bluejoe.elfinder.service.FsVolume;
import cn.bluejoe.elfinder.impl.DefaultFsService;
import cn.bluejoe.elfinder.localfs.LocalFsVolume;
import java.util.ArrayList;

public class OpenCommandExecutor extends AbstractJsonCommandExecutor implements
        CommandExecutor {

    @Override
    public void execute(FsService fsService, HttpServletRequest request,
            ServletContext servletContext, JSONObject json) throws Exception {
        
        boolean init = request.getParameter("init") != null;
        boolean tree = request.getParameter("tree") != null;
        String target = request.getParameter("target");
        String altVolumeName = (String) request.getAttribute("altVolumeName");
        String sessionAttrib = request.getParameter("attrib");
        FsVolume v = null;

        Map<String, FsItemEx> files = new LinkedHashMap<String, FsItemEx>();
        if (init) {
            json.put("api", 2.1);
            json.put("netDrivers", new Object[0]);
        }

        System.out.println("Open - JSON antes de agregar volumenes: \n" + json.toString(2));
        System.out.println("sessionAttrib: " + sessionAttrib);
        System.out.println("----------------------------------------------------");
        
        
        if (tree) {
            if ((null == altVolumeName || altVolumeName.isEmpty()) && null == sessionAttrib) {
                altVolumeName = "A";
                if (init) {
                    target = null;
                }
            }
            //Para trabajar con plantillas o sin restriccion de filtros
            if (null == sessionAttrib || "".equals(sessionAttrib)) {
                //for (FsVolume v : fsService.getVolumes()) {
                v = ((DefaultFsService) fsService).getVolume(altVolumeName);
                if (null != v) {
                    FsItemEx root = new FsItemEx(v.getRoot(), fsService);
                    files.put(root.getHash(), root);
                    addSubfolders(files, root);
                    System.out.println("Open - Agregando volumen: " + v.getName());
                } else {
                    json.put("error", "Path could not be reached");
                }
            } else {
                
                
                //TODO: Agregado para mostrar rutas permitidas al usuario
                //Se obtienen los volumenes permitidos al usuario
                ArrayList<String> userPaths = (ArrayList) request.getSession()
                        .getAttribute(sessionAttrib);
                int pathNumber = 1;
                for (String allowedPath : userPaths) {
                    String volumeName = sessionAttrib + "-" + pathNumber;
                    String escapedPath = "vol" + allowedPath.replaceAll("/", "_");
                    v = ((DefaultFsService) fsService).getVolume(escapedPath);
                    if (null != v) {
                        FsItemEx root = new FsItemEx(v.getRoot(), fsService);
                        files.put(root.getHash(), root);
                        addSubfolders(files, root);
                        System.out.println("Open - Agregando volumen: " + v.getName() +
                                "\ncon root de volumen: " + v.getRoot());
                    } else {
                        json.put("error", "Path could not be reached");
                    }
                    pathNumber++;
                    
                }
                target = v.getName();
                
            }
        }

        FsItemEx cwd = findCwd(fsService, v, target);
        files.put(cwd.getHash(), cwd);
        String[] onlyMimes = request.getParameterValues("mimes[]");
        addChildren(files, cwd, onlyMimes);

        json.put("files", files2JsonArray(request, files.values()));
        json.put("cwd", getFsItemInfo(request, cwd));
        json.put("options", getOptions(request, cwd));
        System.out.println("Open - JSON despues de agregar volumenes: \n" + json.toString(2));
    }
}
