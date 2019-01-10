package org.n3r.acc.process.input;

import org.n3r.config.Configable;
import org.n3r.core.lang.Substituters;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.tag.FromSpecConfig;
import org.n3r.core.utils.RThread;
import org.n3r.core.utils.TimeSpanParser;
import org.n3r.net.ftp.client.FtpDownloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import static java.util.concurrent.TimeUnit.*;

@EcRocTag("Ftp")
public class FtpInput implements DataInput, FromSpecConfig<FtpInput> {
    private Logger logger = LoggerFactory.getLogger(FtpInput.class);

    protected String ftpHost;
    protected int ftpPort = 21;
    protected String ftpPass;
    protected String ftpUser;
    protected String ftpRemote;
    protected File ftpLocalFile;
    private long maxRetryTimeInMillis;
    private long retrySleepTimeInMillis;

    @Override
    public InputStream getInputStream() {
        return getInputStream(new FtpDownloader());
    }

    protected InputStream getInputStream(FtpDownloader ftpDownloader) {
        long startTime = System.currentTimeMillis();
        while(true) {
            boolean ok = ftpDownloader
                    .connect(ftpHost, ftpPort)
                    .login(ftpUser, ftpPass)
                    .remote(ftpRemote).local(ftpLocalFile)
                    .download();

            if (ok) return toInputStream();

            if (expireRetryMaxDuration(startTime))
                throw new RuntimeException("DownFile Error");

            logger.info("waiting {} milliseconds and then retry", retrySleepTimeInMillis);
            RThread.sleepMilis(retrySleepTimeInMillis);
        }
    }

    private boolean expireRetryMaxDuration(long startTime) {
        long currentTimeInMillis = System.currentTimeMillis();
        return currentTimeInMillis - startTime > maxRetryTimeInMillis;
    }

    protected FileInputStream toInputStream() {
        try {
            return new FileInputStream(ftpLocalFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public FtpInput fromSpec(Configable config, Map<String, String> context) {
        ftpHost = config.getStr("ftp.host");
        ftpUser = config.getStr("ftp.user");
        ftpPort = config.getInt("ftp.port", 21);

        ftpPass = config.getStr("ftp.pass");
        String tmpFtpRemote = config.getStr("ftp.remote");
        ftpRemote = Substituters.parse(tmpFtpRemote, context);
        String tmpFtpLocal = config.getStr("ftp.local");
        String ftpLocal = Substituters.parse(tmpFtpLocal, context);
        ftpLocalFile = new File(ftpLocal);

        String tempMaxRetryTimeInMillis = config.getStr("ftp.maxRetryTime", "1h");
        maxRetryTimeInMillis = TimeSpanParser.parse(tempMaxRetryTimeInMillis, MILLISECONDS);

        String retrySleepTime = config.getStr("ftp.retrySleepTime", "10s");
        retrySleepTimeInMillis = TimeSpanParser.parse(retrySleepTime, MILLISECONDS);

        return this;
    }
}
