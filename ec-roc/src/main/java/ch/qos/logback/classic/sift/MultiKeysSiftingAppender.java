package ch.qos.logback.classic.sift;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.sift.AppenderFactoryBase;

public class MultiKeysSiftingAppender extends SiftingAppender {

    @Override
    public void setAppenderFactory(AppenderFactoryBase<ILoggingEvent> appenderFactory) {
        super.setAppenderFactory(new MultiKeysAppenderFactory(appenderFactory));
    }
}
