package org.n3r.servlet;

import java.util.EventListener;

import javax.servlet.http.HttpServlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class EcRocJettyServer {

    private Server server;
    private ServletContextHandler context;

    public EcRocJettyServer() {
        context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
    }

    public void join() throws InterruptedException {
        server.join();
    }

    public EcRocJettyServer start() throws Exception {
        return start(8080);
    }

    public EcRocJettyServer start(int port) throws Exception {
        server = new Server(port);
        server.setHandler(context);
        server.start();

        return this;
    }

    public EcRocJettyServer addServlet(HttpServlet servlet, String pathSpec) {
        context.addServlet(new ServletHolder(servlet), pathSpec);
        return this;
    }

    public EcRocJettyServer addListener(EventListener listener) {
        context.addEventListener(listener);
        return this;
    }

}
