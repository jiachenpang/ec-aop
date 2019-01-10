package org.n3r.metaq.session.singleton;

import org.n3r.config.Config;

import com.taobao.metamorphosis.client.MetaClientConfig;
import com.taobao.metamorphosis.utils.ZkUtils.ZKConfig;


public class ClientConfigSingleton {
    private MetaClientConfig metaClientConfig = null;

    private static ClientConfigSingleton singleton = new ClientConfigSingleton();

    private ClientConfigSingleton() {
        ZKConfig zkConfig = new ZKConfig();
        zkConfig.zkConnect = Config.getStr("ecaop.log.metaq.zkconnect", "127.0.0.1:2181");
        metaClientConfig = new MetaClientConfig();
        metaClientConfig.setZkConfig(zkConfig);
    }

    public MetaClientConfig getMetaClientConfig() {
        return metaClientConfig;
    }

    public static ClientConfigSingleton getSingleton() {
        return singleton;
    }


}
