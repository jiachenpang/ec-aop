package org.n3r.acc.process.input;

import org.n3r.config.Configable;
import org.n3r.core.tag.EcRocTag;
import org.n3r.net.sftp.client.SftpDownloader;

import java.io.InputStream;
import java.util.Map;

@EcRocTag("Sftp")
public class SftpInput extends FtpInput {
    public InputStream getInputStream() {
        return getInputStream(new SftpDownloader());
    }

    @Override
    public SftpInput fromSpec(Configable config, Map<String, String> context) {
        super.fromSpec(config, context);
        ftpPort = config.getInt("ftp.port", 22);

        return this;
    }
}
