package org.n3r.net.ftp.client;

import com.google.common.io.Files;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.n3r.net.ftp.server.RFtpServer;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FtpDownloaderTest {

    @Test
    public void test1() throws Exception {
        File ftphome = Files.createTempDir();
        File remote = new File(ftphome, "remote");
        String data = "" + System.currentTimeMillis();
        FileUtils.writeStringToFile(remote, data);

        File localFile = File.createTempFile("ftp", ".tmp");

        RFtpServer ftpServer = new RFtpServer().startFtpServer(ftphome.getAbsolutePath(), 1251, "user", "pass");

        boolean ok = new FtpDownloader().connect("127.0.0.1", 1251).login("user", "pass")
                .remote("remote").local(localFile).download();

        ftpServer.stop();

        assertTrue(ok);
        assertEquals(data, FileUtils.readFileToString(localFile));

        FileUtils.deleteDirectory(ftphome);
        FileUtils.deleteQuietly(localFile);
    }

    @Test
    public void test2() throws Exception {
        File ftphome = Files.createTempDir();

        File localFile = File.createTempFile("ftp", ".tmp");

        RFtpServer ftpServer = new RFtpServer().startFtpServer(ftphome.getAbsolutePath(), 1251, "user", "pass");

        boolean ok = new FtpDownloader().connect("127.0.0.1", 1251).login("user", "pass")
                .remote("remote").local(localFile).download();

        ftpServer.stop();

        assertFalse(ok);

        assertEquals("", FileUtils.readFileToString(localFile));
        FileUtils.deleteDirectory(ftphome);
        FileUtils.deleteQuietly(localFile);
    }
}
