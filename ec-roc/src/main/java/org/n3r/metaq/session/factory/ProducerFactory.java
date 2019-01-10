package org.n3r.metaq.session.factory;

import org.n3r.metaq.session.SessionType;
import org.n3r.metaq.session.singleton.AsyncSessionSingleton;
import org.n3r.metaq.session.singleton.SimpleSessionSingleton;

import com.taobao.metamorphosis.client.MetaMessageSessionFactory;
import com.taobao.metamorphosis.client.extension.AsyncMetaMessageSessionFactory;
import com.taobao.metamorphosis.client.producer.MessageProducer;

public class ProducerFactory {

    public static MessageProducer create(SessionType sessionType) {
        if (sessionType == SessionType.ASYNC) {
            AsyncMetaMessageSessionFactory async = AsyncSessionSingleton.getSingleton().getAsyncMessageSessionFactory();
            return async.createAsyncProducer();
        }
        MetaMessageSessionFactory simple = SimpleSessionSingleton.getSingleton().getSimpleMessageSessionFactory();
        return simple.createProducer();
    }

}
