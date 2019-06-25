package cn.bluejoe.elfinder.controller;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.fileupload.util.Streams;

import cn.bluejoe.elfinder.controller.executor.CommandExecutionContext;
import cn.bluejoe.elfinder.controller.executor.CommandExecutor;
import cn.bluejoe.elfinder.controller.executor.CommandExecutorFactory;
import cn.bluejoe.elfinder.impl.DefaultFsService;
import cn.bluejoe.elfinder.impl.StaticFsServiceFactory;
import cn.bluejoe.elfinder.localfs.LocalFsVolume;
import cn.bluejoe.elfinder.service.FsServiceFactory;
import cn.bluejoe.elfinder.service.FsVolume;
import cn.bluejoe.elfinder.servlet.SWBConnectorServlet;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import org.json.JSONException;
import org.json.JSONObject;
import org.semanticwb.Logger;
import org.semanticwb.SWBPlatform;
import org.semanticwb.SWBPortal;
import org.semanticwb.SWBUtils;
import org.semanticwb.model.SWBContext;
import org.semanticwb.model.User;
import org.semanticwb.model.WebPage;
import org.semanticwb.model.WebSite;
import org.semanticwb.platform.SemanticObject;
import org.semanticwb.servlet.internal.DistributorParams;

public class ConnectorController {
    
    @Resource(name = "commandExecutorFactory")
    private CommandExecutorFactory _commandExecutorFactory;

    @Resource(name = "fsServiceFactory")
    private FsServiceFactory _fsServiceFactory;
    
    /** representa la ruta en disco duro de la carpeta en la que se cargaran los archivos temporales */
    private static String tmpUpload = null;
    
    private static Logger log = SWBUtils.getLogger(ConnectorController.class);

    
    private void addTemplateVolume(String altVolumeName, String siteName, String template, String version) {
        
        long period = 3600000;  //1 hora
        DefaultFsService service = (DefaultFsService) ((StaticFsServiceFactory) this._fsServiceFactory)
                        .getFsService();
        LocalFsVolume mainVolume = (LocalFsVolume) service.getVolume("A");
        String rootPath = mainVolume.getRootDir().getAbsolutePath() + "\\work\\models\\"
                + siteName + "\\Template\\" + template + "\\" + version;
        long timeInMillis = System.currentTimeMillis();
        
        for (FsVolume v : service.getVolumes()) {
            long volumeLastAccess = ((LocalFsVolume) v).getLastAccess();
            if (!"A".equals(v.getName()) && (timeInMillis - volumeLastAccess) > period) {
                service.removeVolume(v.getName());
            }
        }
        
        LocalFsVolume localFsVolume = new LocalFsVolume();
        localFsVolume.setName(altVolumeName);
        localFsVolume.setRootDir(new File(rootPath));
        localFsVolume.setLastAccess(System.currentTimeMillis());
        service.addVolume(altVolumeName, localFsVolume);
    }
    
    /**
     * Adds each allowed path to the user as a volume in the file system service
     * @param sessionAttrib the name for the attribute in session, which contains 
     * the allowed paths to the user
     * @param request the HttpServletRequest from which the session is obtained
     */
    private void addUserVolumes(String sessionAttrib, HttpServletRequest request) {
        
        long period = 3600000;  //1 hora
        DefaultFsService service = (DefaultFsService) ((StaticFsServiceFactory) this._fsServiceFactory)
                        .getFsService();
        LocalFsVolume mainVolume = (LocalFsVolume) service.getVolume("A");
        String volumePrefix = sessionAttrib + "-";
        long timeInMillis = System.currentTimeMillis();
        
        System.out.println("addUserVolumes - Volumenes existentes: ");
        for (FsVolume v : service.getVolumes()) {
            System.out.println("volumen: " + v.getName() +",\n root path: " + v.getRoot());
            long volumeLastAccess = ((LocalFsVolume) v).getLastAccess();
            if (v.getName().startsWith(volumePrefix) && (timeInMillis - volumeLastAccess) > period) {
                service.removeVolume(v.getName());
            }
        }
        
        ArrayList<String> userPaths = (ArrayList) request.getSession()
                .getAttribute(sessionAttrib);
        int pathNumber = 1;
        for (String allowedPath : userPaths) {
            String volumeName = sessionAttrib + "-" + pathNumber;
            String escapedPath = "vol" + allowedPath.replaceAll("/", "_");
            LocalFsVolume localFsVolume = new LocalFsVolume();
            localFsVolume.setName(escapedPath);
            String volumeRoot = mainVolume.getRootDir().getAbsolutePath() + allowedPath;
            localFsVolume.setRootDir(new File(volumeRoot));
            localFsVolume.setLastAccess(System.currentTimeMillis());
            service.addVolume(escapedPath, localFsVolume);
            
            System.out.println("addUserVolumes - Agregando volumenes:");
            System.out.println("name: " + escapedPath + ",\n root: " + volumeRoot);
            
            pathNumber++;
        }
        
    }

    public void connector(HttpServletRequest request,
                    final HttpServletResponse response, final DistributorParams dparams) throws IOException {
        
        if (ConnectorController.getTmpUpload() == null) {
            String resourcePath = request.getHeader("resourcePath");
            if (resourcePath != null && !resourcePath.isEmpty()) {
                File uploadDir = new File(SWBPortal.getWorkPath() + resourcePath);
                if (!uploadDir.exists()) {
                    if (!uploadDir.mkdirs()) {
                        ConnectorController.log.warn("Upload directory for server documents could not be created");
                    }
                }
                ConnectorController.tmpUpload = SWBPortal.getWorkPath() + resourcePath;
            }
        }
        
        try {
            request = parseMultipartContent(request);
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }

        String cmd = request.getParameter("cmd");
        CommandExecutor ce = this._commandExecutorFactory.get(cmd);
        
        //sitio, plantilla y version; para evaluar si es operacion con plantillas
        String siteName = request.getParameter("site");
        String template = request.getParameter("template");
        String version = request.getParameter("version");
        //parametro con nombre de atributo de sesion con listado de directorios permitidos al usuario
        String userPaths = request.getParameter("attrib");
        boolean useAltVolume = (null != template || null != userPaths);
        
        if (useAltVolume) {
            String altVolumeName = "template" + siteName + template + version;
//20/06/19 Sustituir verificacion de volumen por busqueda de todos los volumenes del usuario
//            boolean volumeExists = ((DefaultFsService) 
//                    ((StaticFsServiceFactory) this._fsServiceFactory).getFsService())
//                    .hasVolumeName(altVolumeName);
            //Verifica si existen los volumenes asociados a la plantilla o al usuario
            boolean volumeExists = this.findVolumeName(altVolumeName, userPaths, request);
            if (!volumeExists) {
                if (null != template) {
                    this.addTemplateVolume(altVolumeName, siteName, template, version);
                    request.setAttribute("altVolumeName", altVolumeName);
                }
                if (null != userPaths) {
                    this.addUserVolumes(userPaths, request);
                    //ya se tiene un atributo en la session, el valor de: userPaths
                }
            }
        }

        if (ce == null) {
            // This shouldn't happen as we should have a fallback command set.
            throw new FsException(String.format("unknown command: %s", cmd));
        }
        
        JSONObject json = new JSONObject();
        try {
            final HttpServletRequest finalRequest = request;
            
            if (isValidUser(request, useAltVolume)) {

                ce.execute(new CommandExecutionContext() {

                    @Override
                    public FsServiceFactory getFsServiceFactory() {
                        return _fsServiceFactory;
                    }

                    @Override
                    public HttpServletRequest getRequest() {
                        return finalRequest;
                    }

                    @Override
                    public HttpServletResponse getResponse() {
                        return response;
                    }

                    @Override
                    public ServletContext getServletContext() {
                        return finalRequest.getSession().getServletContext();
                    }

                    @Override
                    public DistributorParams getDistributorParams() {
                        return dparams;
                    }
                });
            
            } else {
                json.put("error", "User has no permissions");
            }
            
        } catch (Exception e) {
            if (e.getMessage() != null) {
                try {
                    String message = e.getMessage();
                    if (message.lastIndexOf(":") != -1) {
                        message = message.substring(message.lastIndexOf(":"));
                    }
                    json.put("error", message);
                } catch(JSONException jsone) {}
            }
            throw new FsException("unknown error", e);
        } finally {
            if (json.has("error")) {
                response.setContentType("application/json; charset=UTF-8");
                PrintWriter writer = response.getWriter();
                try {
                    json.write(writer);
                } catch (JSONException jsone) {
                    writer.println("{\"error\":\"User has no permissions - " +
                            jsone.getMessage() + "\"}");
                }
                writer.flush();
                writer.close();
            }
        }
    }

    /**
     * Validates if any of volumeName or the n paths referred by attribName are available
     * for usage
     * @param volumeName the name for the volume associated to a template
     * @param attribName the session attribute´s name which refers to the paths the user is allowed to access
     * @param request the HTTP request from the client
     */
    private boolean findVolumeName(String volumeName, String attribName,
            HttpServletRequest request) {
        
        boolean volumeExists = false;
        
        if (null != volumeName) {
            volumeExists = ((DefaultFsService) 
                    ((StaticFsServiceFactory) this._fsServiceFactory).getFsService())
                    .hasVolumeName(volumeName);
        } else if (null != attribName) {
            ArrayList<String> userPaths = (ArrayList) request.getSession().getAttribute(attribName);
            for (int i = 0; i < userPaths.size(); i++) {
                String escapedPath = "vol" + userPaths.get(i).replaceAll("/", "_");
                volumeExists = ((DefaultFsService) 
                        ((StaticFsServiceFactory) this._fsServiceFactory).getFsService())
                        .hasVolumeName(escapedPath);
                if (!volumeExists) {
                    break;
                }
            }
        }
        return volumeExists;
    }
    
    public CommandExecutorFactory getCommandExecutorFactory() {
        return _commandExecutorFactory;
    }

    public FsServiceFactory getFsServiceFactory() {
        return _fsServiceFactory;
    }

    /**
     * @return the temporary directory's path used to upload files
     */
    public static String getTmpUpload() {
        return tmpUpload;
    }

    /**
     * Validates if the user making the request is in condition to be answered
     * @param request the HTTP request
     * @param isForTemplates indicates if the request is related to a template or isn't
     * @return a boolean indicating if the user should be answered
     */
    private boolean isValidUser(HttpServletRequest request, boolean isForTemplates) {
        
        boolean userIsSigned = false;
        boolean userIsAllowed = false;
        String sectionUri = request.getParameter("URI");
        
        try {
            WebSite website = SWBContext.getWebSite("SWBAdmin");
            System.out.println("website: " + website.getId());
            User user = SWBPortal.getUserMgr().getUser(request, website);
            userIsSigned = user.isSigned();
            if (!isForTemplates) {
                //userIsSuperUser = user.hasUserGroup(SWBConnectorServlet.SUPER_USER_GROUP);
                SemanticObject semObject = SemanticObject.createSemanticObject(sectionUri);
                WebPage adminSection = (WebPage) semObject.createGenericInstance();
                userIsAllowed = user.haveAccess(adminSection);
            }
            System.out.println("user: " + user + " - fullname: "+ user.getFullName() +
                    "\nisForTemplates: " + isForTemplates +
                    "\nuserIsSigned: " + userIsSigned + 
                    "\nuserIsAllowed: " + userIsAllowed);
        } catch (Exception e) {
            userIsSigned = false;
            userIsAllowed = false;
        }
        return ((isForTemplates && userIsSigned) || (!isForTemplates && userIsSigned && userIsAllowed));
        
    }
    
    private HttpServletRequest parseMultipartContent(
                    final HttpServletRequest request) throws Exception {
        
        if (!ServletFileUpload.isMultipartContent(request)) {
            return request;
        }

        final Map<String, String> requestParams = new HashMap<String, String>();
        List<File> listFiles = new ArrayList<File>();

        // Parse the request
        ServletFileUpload sfu = new ServletFileUpload();
        String characterEncoding = request.getCharacterEncoding();
        if (characterEncoding == null) {
            characterEncoding = "UTF-8";
        }
        sfu.setHeaderEncoding(characterEncoding);
        
        FileItemIterator iter = sfu.getItemIterator(request);
        String saveFilePath = ConnectorController.getTmpUpload() + "/";

        while (iter.hasNext()) {
            final FileItemStream item = iter.next();
            String name = item.getFieldName();
            InputStream stream = item.openStream();
            if (item.isFormField()) {
                requestParams.put(name, Streams.asString(stream, characterEncoding));
            } else {
                String fileName = item.getName();
                if (fileName != null && !"".equals(fileName.trim())) {
                    
                    File newFile = new File(saveFilePath + fileName);
                    FileOutputStream fout = new FileOutputStream(newFile);
                    byte[] bcont = new byte[8192];
                    int ret = stream.read(bcont);
                    while (ret != -1) {
                        fout.write(bcont, 0, ret);
                        ret = stream.read(bcont);
                    }
                    stream.close();
                    fout.close();
                    listFiles.add(newFile);
                }
            }
        }

        request.setAttribute("filesInReq", listFiles);

        // 'getParameter()' method can not be called on original request object
        // after parsing
        // so we stored the request values and provide a delegate request object

        return (HttpServletRequest) Proxy.newProxyInstance(
                this.getClass().getClassLoader(),
                new Class[] { HttpServletRequest.class },
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object arg0, Method arg1, Object[] arg2) throws Throwable {
                        // we replace getParameter() and getParameterValues()
                        // methods
                        if ("getParameter".equals(arg1.getName())) {
                            String paramName = (String) arg2[0];
                            return requestParams.get(paramName);
                        }

                        if ("getParameterValues".equals(arg1.getName())) {
                            String paramName = (String) arg2[0];

                            // normalize name 'key[]' to 'key'
                            if (paramName.endsWith("[]")) {
                                paramName = paramName.substring(0, paramName.length() - 2);
                            }
                            if (requestParams.containsKey(paramName)) {
                                return new String[] { requestParams.get(paramName) };
                            }
                            // if contains key[1], key[2]...
                            int i = 0;
                            List<String> paramValues = new ArrayList<String>();
                            while (true) {
                                String name2 = String.format("%s[%d]", paramName, i++);
                                if (requestParams.containsKey(name2)) {
                                    paramValues.add(requestParams.get(name2));
                                } else {
                                    break;
                                }
                            }

                            return paramValues.isEmpty() ? new String[0]
                                                         : paramValues.toArray(new String[0]);
                        }

                        return arg1.invoke(request, arg2);
                    }
                });
    }

    public void setCommandExecutorFactory(CommandExecutorFactory _commandExecutorFactory) {
        this._commandExecutorFactory = _commandExecutorFactory;
    }

    public void setFsServiceFactory(FsServiceFactory _fsServiceFactory)	{
        this._fsServiceFactory = _fsServiceFactory;
    }

}