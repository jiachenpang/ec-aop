package org.n3r.loglevel;

import java.util.List;

import org.apache.commons.lang3.ClassUtils;
import org.n3r.core.util.ParamsApplyUtils;

import com.google.common.collect.Lists;

public class LogLevelChanger {

    private static List<ILogLevelChanger> impls = Lists.newArrayList();

    static {
        loadManagerImplementation();
    }

    private static void loadManagerImplementation() {

        try {
            ClassUtils.getClass("org.apache.log4j.Logger");
            ILogLevelChanger object = ParamsApplyUtils.createObject("org.n3r.loglevel.impl.Log4jLevelChanger", ILogLevelChanger.class);
            impls.add(object);
        }
        catch (ClassNotFoundException e) {
        }

        try {
            ClassUtils.getClass("ch.qos.logback.classic.Logger");
            ILogLevelChanger object = ParamsApplyUtils.createObject("org.n3r.loglevel.impl.LogBackLevelChanger", ILogLevelChanger.class);
            impls.add(object);
        }
        catch (ClassNotFoundException e) {
        }

    }

    public static void changeAllLevel(EcLogLevel level) {
        for (ILogLevelChanger impl : impls) {
            impl.changeAllLevel(level);
        }
    }

    public static void changeLevel(EcLogLevel level, Class clazz) {
        for (ILogLevelChanger impl : impls) {
            impl.changeLevel(level, clazz);
        }
    }

    public static void changeLevel(EcLogLevel level, String name) {
        for (ILogLevelChanger impl : impls) {
            impl.changeLevel(level, name);
        }
    }

    public static List<EcLogger> getLevels(String wildcardMatcher) {
        List<EcLogger> Levels = Lists.newArrayList();
        for (ILogLevelChanger impl : impls) {
            Levels.addAll(impl.getLevels(wildcardMatcher));
        }
        return Levels;
    }

}
