package org.n3r.metaq.session.factory;

import org.n3r.metaq.session.SessionType;
import org.n3r.metaq.session.singleton.BroadcastSessionSingleton;
import org.n3r.metaq.session.singleton.SimpleSessionSingleton;

import com.taobao.metamorphosis.client.MetaMessageSessionFactory;
import com.taobao.metamorphosis.client.consumer.ConsumerConfig;
import com.taobao.metamorphosis.client.consumer.MessageConsumer;
import com.taobao.metamorphosis.client.extension.MetaBroadcastMessageSessionFactory;

public class ConsumerFactory {

    public static MessageConsumer create(SessionType sessionType, String group) {
        if (sessionType == SessionType.BROADCAST) {
            MetaBroadcastMessageSessionFactory broadcast = BroadcastSessionSingleton.getSingleton()
                    .getBroadcastMessageSessionFactory();
            return broadcast.createBroadcastConsumer(new ConsumerConfig(group));
        }
        MetaMessageSessionFactory simple = SimpleSessionSingleton.getSingleton().getSimpleMessageSessionFactory();
        return simple.createConsumer(new ConsumerConfig(group));
    }

}
