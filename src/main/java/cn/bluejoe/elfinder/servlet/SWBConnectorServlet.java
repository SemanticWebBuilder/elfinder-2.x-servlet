package cn.bluejoe.elfinder.servlet;

import cn.bluejoe.elfinder.controller.ConnectorController;
import cn.bluejoe.elfinder.controller.FsException;
import cn.bluejoe.elfinder.controller.executor.CommandExecutorFactory;
import cn.bluejoe.elfinder.controller.executor.DefaultCommandExecutorFactory;
import cn.bluejoe.elfinder.controller.executors.MissingCommandExecutor;
import cn.bluejoe.elfinder.impl.DefaultFsService;
import cn.bluejoe.elfinder.impl.DefaultFsServiceConfig;
import cn.bluejoe.elfinder.impl.FsSecurityCheckForAll;
import cn.bluejoe.elfinder.impl.StaticFsServiceFactory;
import cn.bluejoe.elfinder.localfs.LocalFsVolume;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.semanticwb.Logger;
import org.semanticwb.SWBPortal;
import org.semanticwb.SWBUtils;
import org.semanticwb.model.SWBContext;
import org.semanticwb.model.User;
import org.semanticwb.model.UserGroup;
import org.semanticwb.model.WebSite;
import org.semanticwb.servlet.internal.DistributorParams;
import org.semanticwb.servlet.internal.InternalServlet;

/**
 * Connector with access to SWB's elements 
 * @author jose.jimenez
 */
public class SWBConnectorServlet implements InternalServlet {
    
    //Core member of this Servlet
    ConnectorController _connectorController;
    
    //Logger for events
    private static Logger log = SWBUtils.getLogger(SWBConnectorServlet.class);
    
    /** Group of users with granted permissions to manage files */
    public static UserGroup SUPER_USER_GROUP;
    
    /** Physical path for the base directory to show on the hierarchical folder tree */
    public static String basePath = SWBPortal.getWorkPath().substring(0,
                                        SWBPortal.getWorkPath().lastIndexOf("/") + 1);
    
    /** Name for the directory to show on the base directory to show on the hierarchical folder tree */
    public static String baseName = "web";
    
    /**
     * creates a command executor factory
     * @return the command executor factory created
     */
    protected CommandExecutorFactory createCommandExecutorFactory() {
        
        DefaultCommandExecutorFactory defaultCommandExecutorFactory = new DefaultCommandExecutorFactory();
        defaultCommandExecutorFactory
                        .setClassNamePattern("cn.bluejoe.elfinder.controller.executors.%sCommandExecutor");
        defaultCommandExecutorFactory
                        .setFallbackCommand(new MissingCommandExecutor());
        return defaultCommandExecutorFactory;
    }
    
    /**
     * Creates a file system volume with a specified root directory
     * @param name the file system volume's name
     * @param rootDir the directory which will be the root directory for the volume
     * @return the local volume created
     */
    private LocalFsVolume createLocalFsVolume(String name, File rootDir) {
        
            LocalFsVolume localFsVolume = new LocalFsVolume();
            localFsVolume.setName(name);
            localFsVolume.setRootDir(rootDir);
            return localFsVolume;
    }
    
    /**
     * Creates a file system service with the default configuration and a width of 80 pixels
     * for the thumbnails created for images in the file system
     * @return the file system created
     */
    protected DefaultFsService createFsService() {
        
        DefaultFsService fsService = new DefaultFsService();
        fsService.setSecurityChecker(new FsSecurityCheckForAll());

        DefaultFsServiceConfig serviceConfig = new DefaultFsServiceConfig();
        serviceConfig.setTmbWidth(80);

        fsService.setServiceConfig(serviceConfig);
        fsService.addVolume("A",
                        createLocalFsVolume(SWBConnectorServlet.baseName,
                                new File(SWBConnectorServlet.basePath)));
//        fsService.addVolume("B",
//                        createLocalFsVolume("Shared", new File("/tmp/b")));

        return fsService;
    }
    
    /**
     * creates a service factory
     * @return
     */
    protected StaticFsServiceFactory createServiceFactory() {
        
        StaticFsServiceFactory staticFsServiceFactory = new StaticFsServiceFactory();
        DefaultFsService fsService = createFsService();

        staticFsServiceFactory.setFsService(fsService);
        return staticFsServiceFactory;
    }
    
    /**
     * creates a connector controller
     * @return
     */
    protected ConnectorController createConnectorController() {
        
        ConnectorController connectorController = new ConnectorController();
        connectorController.setCommandExecutorFactory(
                createCommandExecutorFactory());
        connectorController.setFsServiceFactory(createServiceFactory());
        return connectorController;
    }
    
    /**
     * Instantiates the controller which will serve the requests for file system elements
     * @param context the servlet context of the application
     * @throws ServletException 
     */
    @Override
    public void init(ServletContext context) throws ServletException {
        
        log.event("Initializing InternalServlet elFinder - SWBConnectorServlet...");
        log.event("with path + " + SWBConnectorServlet.basePath);
        _connectorController = createConnectorController();
        int index = SWBPortal.getWorkPath().lastIndexOf("/");
        index = SWBPortal.getWorkPath().lastIndexOf("/", index - 1);
        String newBaseName = SWBPortal.getWorkPath().substring(index + 1,
                SWBPortal.getWorkPath().lastIndexOf("/"));
        if (!newBaseName.isEmpty()) {
            SWBConnectorServlet.baseName = newBaseName;
        }
    }
    
    /**
     * Processes all the requests for file system elements
     * @param request the http request to serve
     * @param response the corresponding http response for the request
     * @param dparams the distributor parameters for this servlet
     * @throws IOException
     * @throws ServletException 
     */
    @Override
    public void doProcess(HttpServletRequest request, HttpServletResponse response,
            DistributorParams dparams) throws IOException, ServletException {
        
        if (SWBConnectorServlet.SUPER_USER_GROUP == null) {
            WebSite website = SWBContext.getWebSite("SWBAdmin");
            User user = SWBPortal.getUserMgr().getUser(request, website);
            Iterator<UserGroup> gpsIt = user.getUserRepository().listUserGroups();
            while (gpsIt.hasNext()) {
                UserGroup group = gpsIt.next();
                if (group.getId().equalsIgnoreCase("su")) {
                    SWBConnectorServlet.SUPER_USER_GROUP = group;
                    break;
                }
            }
        }
        
        try {
            //en el siguiente metodo se valida al usuario, antes de ejecutar el comando solicitado
            _connectorController.connector(request, response, dparams);
        } catch (NumberFormatException nfe) {
            SWBConnectorServlet.log.event("File size exceeded");
        } catch (FsException fe) {
            SWBConnectorServlet.log.error("At processing request", fe);
        }
        
    }
}
