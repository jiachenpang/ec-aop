package org.n3r.net.ftp.client;

import org.junit.Test;
import org.n3r.net.sftp.client.SftpDownloader;

import java.io.File;

public class SftpDownloaderTest {
    @Test
    public void testFtp() throws Exception {
        File localFile = File.createTempFile("ftp", ".tmp");
        boolean ok = new SftpDownloader().connect("10.142.195.61").login("bingoo", "cuser_!9")
                .remote("/home/bingoo/second1.txt").local(localFile).download();

        System.out.println(localFile);

    }
}
