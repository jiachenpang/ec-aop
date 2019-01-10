package org.n3r.loglevel.impl;

import java.util.Enumeration;
import java.util.List;

import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.n3r.loglevel.EcLogLevel;
import org.n3r.loglevel.EcLogger;
import org.n3r.loglevel.ILogLevelChanger;

import com.google.common.collect.Lists;

public class Log4jLevelChanger implements ILogLevelChanger {

    private Level transToLog4j(EcLogLevel level) {
        Level log4jLevel = null;
        switch (level) {
        case DEBUG:
            log4jLevel = Level.DEBUG;
            break;
        case INFO:
            log4jLevel = Level.INFO;
            break;
        case WARN:
            log4jLevel = Level.WARN;
            break;
        case ERROR:
            log4jLevel = Level.ERROR;
            break;
        }

        return log4jLevel;
    }

    @Override
    public void changeAllLevel(EcLogLevel level) {
        Level log4jLevel = transToLog4j(level);
        Enumeration<Logger> currentLoggers = LogManager.getCurrentLoggers();
        while (currentLoggers.hasMoreElements()) {
            Logger logger = currentLoggers.nextElement();
            logger.setLevel(log4jLevel);
        }

        Logger rootLogger = LogManager.getRootLogger();
        rootLogger.setLevel(log4jLevel);
    }

    @Override
    public void changeLevel(EcLogLevel level, Class clazz) {
        changeLevel(level, clazz.getName());
    }

    @Override
    public void changeLevel(EcLogLevel level, String name) {
        Level log4jLevel = transToLog4j(level);

        Logger logger = Logger.getLogger(name);
        logger.setLevel(log4jLevel);
    }

    private void addLoggerToList(List<EcLogger> loggers, Logger logger) {
        EcLogger ecLogger = new EcLogger();
        ecLogger.setName(logger.getName());
        Level level = logger.getLevel();
        ecLogger.setLevel(null == level ? null : level.toString());
        loggers.add(ecLogger);
    }

    @Override
    public List<EcLogger> getLevels(String wildcardMatcher) {
        List<EcLogger> loggers = Lists.newArrayList();
        Logger rootLogger = LogManager.getRootLogger();
        addLoggerToList(loggers, rootLogger);
        Enumeration<Logger> currentLoggers = LogManager.getCurrentLoggers();
        while (currentLoggers.hasMoreElements()) {
            Logger logger = currentLoggers.nextElement();
            if (FilenameUtils.wildcardMatch(logger.getName(), wildcardMatcher)) {
                addLoggerToList(loggers, logger);
            }
        }
        return loggers;
    }

}
