package org.n3r.loglevel;

public enum EcLogLevel {
    DEBUG("DEBUG", 10000), INFO("INFO", 20000), WARN("WARN", 30000), ERROR("ERROR", 40000);

    private String levelStr;
    private int levelInt;

    private EcLogLevel(String levelStr, int levelInt) {
        this.levelStr = levelStr;
        this.levelInt = levelInt;
    }

    @Override
    public String toString() {
        return levelStr;
    }

    public String getLevelStr() {
        return levelStr;
    }

    public void setLevelStr(String levelStr) {
        this.levelStr = levelStr;
    }

    public int getLevelInt() {
        return levelInt;
    }

    public void setLevelInt(int levelInt) {
        this.levelInt = levelInt;
    }

}
