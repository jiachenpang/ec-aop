package org.n3r.loglevel;

import java.util.List;

public interface ILogLevelChanger {

    public  void changeAllLevel(EcLogLevel level);

    public  void changeLevel(EcLogLevel level, Class clazz);

    public  void changeLevel(EcLogLevel level, String name);

    public List<EcLogger> getLevels(String wildcardMatcher);
}
