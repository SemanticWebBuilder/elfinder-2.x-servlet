package cn.bluejoe.elfinder.controller.executors;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.json.JSONObject;

import cn.bluejoe.elfinder.controller.executor.AbstractJsonCommandExecutor;
import cn.bluejoe.elfinder.controller.executor.CommandExecutor;
import cn.bluejoe.elfinder.controller.executor.FsItemEx;
import cn.bluejoe.elfinder.service.FsService;
import java.util.regex.Pattern;

public class RenameCommandExecutor extends AbstractJsonCommandExecutor implements CommandExecutor {

    @Override
    public void execute(FsService fsService, HttpServletRequest request,
            ServletContext servletContext, JSONObject json) throws Exception {
        
        String target = request.getParameter("target");
        String current = request.getParameter("current");
        String name = request.getParameter("name");
        boolean nameIsValid = false;
        FsItemEx fsi = super.findItem(fsService, target);
        
        if (!name.isEmpty()) {
            name = name.trim();
        }
        //si fsi es directorio >> el nombre debe ser al menos de un caracter: [a-zA-Z0-9] o _
        if (fsi.isFolder() && !name.equalsIgnoreCase(".") && !name.equalsIgnoreCase("..")) {
            Pattern pat = Pattern.compile("[^\\\"|\\<\\>/\\:\\?\\*]+");
            nameIsValid = pat.matcher(name).matches();
//            System.out.println("cadena: '" + name + "' es valida?" + nameIsValid);
        } else if (!fsi.isFolder() && !name.equalsIgnoreCase(".") && !name.equalsIgnoreCase("..")) {
        //si fsi es archivo >> el nombre debe tener al menos un caracter: [a-zA-Z0-9] o _ antes de un punto
            //  ([^\\\"|\\<\\>/\\:\\?\\*]+)(.([^\\\"|\\<\\>/\\:\\?\\*]+))?
            Pattern pat = Pattern.compile("[^\\\"|\\<\\>/\\:\\?\\*]+");
            nameIsValid = pat.matcher(name).matches();
//            System.out.println("cadena: '" + name + "' es valida?" + nameIsValid);
        }
        if (nameIsValid) {
            FsItemEx dst = new FsItemEx(fsi.getParent(), name);
            if (!dst.exists()) {
                fsi.renameTo(dst);
                json.put("added", new Object[] {getFsItemInfo(request, dst)});
                json.put("removed", new String[]{target});
            } else {
                json.put("error", "Already exists!");
            }
        } else {
            json.put("error", "Not a valid name");
        }
    }
}
