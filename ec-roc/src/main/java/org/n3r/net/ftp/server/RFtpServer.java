package org.n3r.net.ftp.server;

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;

public class RFtpServer {
    private FtpServer ftpServer;
    public RFtpServer startFtpServer(String homePath, int port, String user, String pass) {
        FtpServerFactory serverFactory = new FtpServerFactory();
        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(port);

        serverFactory.addListener("default", listenerFactory.createListener());
        serverFactory.setUserManager(new FtpUserManager(homePath, user, pass));

        ftpServer = serverFactory.createServer();
        try {
            ftpServer.start();
            return this;
        } catch (FtpException e) {
            throw new RuntimeException(e);
        }
    }

    public void stop() {
        ftpServer.stop();
    }
}
