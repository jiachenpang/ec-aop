package org.n3r.loglevel.impl;

import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.n3r.loglevel.EcLogLevel;
import org.n3r.loglevel.EcLogger;
import org.n3r.loglevel.ILogLevelChanger;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.StaticLoggerBinder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

import com.google.common.collect.Lists;

public class LogBackLevelChanger implements ILogLevelChanger {

    private Level transToLogback(EcLogLevel level) {
        Level logbackLevel = null;
        switch (level) {
        case DEBUG:
            logbackLevel = Level.DEBUG;
            break;
        case INFO:
            logbackLevel = Level.INFO;
            break;
        case WARN:
            logbackLevel = Level.WARN;
            break;
        case ERROR:
            logbackLevel = Level.ERROR;
            break;
        }

        return logbackLevel;
    }

    @Override
    public void changeAllLevel(EcLogLevel level) {
        Level logbackLevel = transToLogback(level);
        ILoggerFactory loggerFactory = StaticLoggerBinder.getSingleton().getLoggerFactory();
        if (!(loggerFactory instanceof LoggerContext)) return;

        LoggerContext loggerContext = (LoggerContext) loggerFactory;
        List<Logger> loggerList = loggerContext.getLoggerList();
        for (Logger logger : loggerList) {
            logger.setLevel(logbackLevel);
        }
    }



    @Override
    public void changeLevel(EcLogLevel level, Class clazz) {
        changeLevel(level, clazz.getName());
    }

    @Override
    public void changeLevel(EcLogLevel level, String name) {
        Level logbackLevel = transToLogback(level);

        Logger logger = (Logger) LoggerFactory.getLogger(name);
        logger.setLevel(logbackLevel);
    }

    @Override
    public List<EcLogger> getLevels(String wildcardMatcher) {
        List<EcLogger> loggers = Lists.newArrayList();
        ILoggerFactory loggerFactory = StaticLoggerBinder.getSingleton().getLoggerFactory();
        if (!(loggerFactory instanceof LoggerContext)) return loggers;

        LoggerContext loggerContext = (LoggerContext) loggerFactory;
        List<Logger> loggerList = loggerContext.getLoggerList();
        for (Logger logger : loggerList) {
            EcLogger ecLogger = new EcLogger();
            String loggerName = logger.getName();
            if (!FilenameUtils.wildcardMatch(logger.getName(), wildcardMatcher)) continue;

            ecLogger.setName(loggerName);
            Level level = logger.getLevel();
            ecLogger.setLevel(null == level ? null : level.levelStr);
            loggers.add(ecLogger);
        }
        return loggers;
    }

}
