package cn.bluejoe.elfinder.controller.executors;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.json.JSONObject;

import cn.bluejoe.elfinder.controller.executor.AbstractJsonCommandExecutor;
import cn.bluejoe.elfinder.controller.executor.CommandExecutor;
import cn.bluejoe.elfinder.controller.executor.FsItemEx;
import cn.bluejoe.elfinder.service.FsService;
import java.util.regex.Pattern;

public class MkdirCommandExecutor extends AbstractJsonCommandExecutor implements CommandExecutor {
    
    @Override
    public void execute(FsService fsService, HttpServletRequest request, 
            ServletContext servletContext, JSONObject json) throws Exception {

        String target = request.getParameter("target");
        String name = request.getParameter("name");
        boolean nameIsValid = false;
        
        if (!name.isEmpty()) {
            name = name.trim();
        }
        if (!name.equalsIgnoreCase(".") && !name.equalsIgnoreCase("..")) {
            Pattern pat = Pattern.compile("[^\\\"|\\<\\>/\\:\\?\\*]+");
            nameIsValid = pat.matcher(name).matches();
        }
        if (nameIsValid) {
            FsItemEx fsi = super.findItem(fsService, target);
            FsItemEx dir = new FsItemEx(fsi, name);
            dir.createFolder();
            json.put("added", new Object[] { getFsItemInfo(request, dir) });
        } else {
            json.put("error", "Not a valid name");
        }
    }
}
