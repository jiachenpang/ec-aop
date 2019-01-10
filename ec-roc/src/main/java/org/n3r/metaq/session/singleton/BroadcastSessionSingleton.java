package org.n3r.metaq.session.singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.taobao.metamorphosis.client.extension.MetaBroadcastMessageSessionFactory;
import com.taobao.metamorphosis.exception.MetaClientException;

public class BroadcastSessionSingleton {
    private static final Logger logger = LoggerFactory.getLogger(BroadcastSessionSingleton.class);

    private MetaBroadcastMessageSessionFactory  broadcastMessageSessionFactory = null;

    private static BroadcastSessionSingleton singleton = new BroadcastSessionSingleton();

    private BroadcastSessionSingleton() {
        try {
            broadcastMessageSessionFactory = new MetaBroadcastMessageSessionFactory(ClientConfigSingleton.getSingleton()
                    .getMetaClientConfig());
        } catch (MetaClientException e) {
            logger.error("Initialize broadcastMessageSessionFactory failed", e);
        }
    }

    public static BroadcastSessionSingleton getSingleton() {
        return singleton;
    }

    public MetaBroadcastMessageSessionFactory getBroadcastMessageSessionFactory() {
        return broadcastMessageSessionFactory;
    }

}
