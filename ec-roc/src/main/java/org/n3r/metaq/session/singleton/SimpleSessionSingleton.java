package org.n3r.metaq.session.singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.taobao.metamorphosis.client.MetaMessageSessionFactory;
import com.taobao.metamorphosis.exception.MetaClientException;

public class SimpleSessionSingleton {
    private static final Logger logger = LoggerFactory.getLogger(SimpleSessionSingleton.class);

    private MetaMessageSessionFactory simpleMessageSessionFactory = null;

    private static SimpleSessionSingleton singleton = new SimpleSessionSingleton();

    private SimpleSessionSingleton() {
        try {
            simpleMessageSessionFactory = new MetaMessageSessionFactory(ClientConfigSingleton.getSingleton()
                    .getMetaClientConfig());
        } catch (MetaClientException e) {
            logger.error("Initialize simpleMessageSessionFactory failed", e);
        }
    }

    public MetaMessageSessionFactory getSimpleMessageSessionFactory() {
        return simpleMessageSessionFactory;
    }

    public static SimpleSessionSingleton getSingleton() {
        return singleton;
    }

}
