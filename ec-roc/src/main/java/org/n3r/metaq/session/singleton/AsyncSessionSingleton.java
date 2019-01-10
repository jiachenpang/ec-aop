package org.n3r.metaq.session.singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.taobao.metamorphosis.client.extension.AsyncMetaMessageSessionFactory;
import com.taobao.metamorphosis.exception.MetaClientException;

public class AsyncSessionSingleton {
    private static final Logger logger = LoggerFactory.getLogger(AsyncSessionSingleton.class);

    private AsyncMetaMessageSessionFactory  asyncMessageSessionFactory = null;

    private static AsyncSessionSingleton singleton = new AsyncSessionSingleton();

    private AsyncSessionSingleton() {
        try {
            asyncMessageSessionFactory = new AsyncMetaMessageSessionFactory(ClientConfigSingleton.getSingleton()
                    .getMetaClientConfig());
        } catch (MetaClientException e) {
            logger.error("Initialize asyncMessageSessionFactory failed", e);
        }
    }

    public static AsyncSessionSingleton getSingleton() {
        return singleton;
    }

    public AsyncMetaMessageSessionFactory getAsyncMessageSessionFactory() {
        return asyncMessageSessionFactory;
    }

}
