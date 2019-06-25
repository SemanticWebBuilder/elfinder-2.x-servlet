package cn.bluejoe.elfinder.controller.executor;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import cn.bluejoe.elfinder.service.FsServiceFactory;
import org.semanticwb.servlet.internal.DistributorParams;

public interface CommandExecutionContext {

    FsServiceFactory getFsServiceFactory();

    HttpServletRequest getRequest();

    HttpServletResponse getResponse();

    ServletContext getServletContext();

    DistributorParams getDistributorParams();
    
}
