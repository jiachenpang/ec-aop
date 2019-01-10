package org.n3r.acc;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.n3r.acc.process.DataProcessor;
import org.n3r.net.ftp.server.RFtpServer;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FtpInputTest {
    @Test
    public void test1() throws Exception {

        File remote = new File("remote.txt");
        String data = "" + System.currentTimeMillis();
        FileUtils.writeStringToFile(remote, data);
        File localFile = new File("local.txt");
        RFtpServer ftpServer = new RFtpServer().startFtpServer(".", 1251, "user", "pass");

        new DataProcessor().fromSpec("ftp", null).process();

        ftpServer.stop();
        assertTrue(localFile.exists());
        assertEquals(data, FileUtils.readFileToString(localFile));

        FileUtils.deleteQuietly(localFile);
        FileUtils.deleteQuietly(remote);


    }

    @Test
    public void test2() throws Exception {

        File localFile = new File("local.txt");
        localFile.delete();

        new DataProcessor().fromSpec("sftp", null).process();

        assertTrue(localFile.exists());

        FileUtils.deleteQuietly(localFile);


    }
}
