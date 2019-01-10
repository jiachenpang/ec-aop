package org.n3r;

import java.io.IOException;

import org.apache.zookeeper.KeeperException;

public class ConfigUpdater {

    private final ActiveKeyValueStore store;

    public ActiveKeyValueStore getStore() {
        return store;
    }

    public ConfigUpdater(String hosts) throws IOException, InterruptedException {
        store = new ActiveKeyValueStore();
        store.connect(hosts);
    }

    public static void main(String[] args) throws KeeperException, InterruptedException, IOException {
        // ConfigUpdater config = new ConfigUpdater("10.40.32.11:2187");
        // ConfigUpdater config = new ConfigUpdater(
        // "10.20.16.25:2187,10.20.16.26:2187,10.20.16.27:2187,10.20.16.28:2187,10.20.16.29:2187");
        // config.store.write("/TdTRedisSwitchCache", TdTRedisSwitchCache.class.getName());
        ConfigUpdater config = new ConfigUpdater("132.35.81.197:2187");
        config.store.write("/Config", "org.n3r.config.Config");
        config.store.write("/EcAopConfigLoader", "org.n3r.ecaop.core.conf.EcAopConfigLoader");
        System.out.println("刷新完成");
    }
}
