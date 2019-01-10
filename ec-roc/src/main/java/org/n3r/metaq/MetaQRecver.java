package org.n3r.metaq;

import java.io.Closeable;
import java.io.IOException;

import org.n3r.metaq.session.SessionType;
import org.n3r.metaq.session.factory.ConsumerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.taobao.metamorphosis.client.consumer.MessageConsumer;
import com.taobao.metamorphosis.client.consumer.MessageListener;
import com.taobao.metamorphosis.exception.MetaClientException;

public class MetaQRecver implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(MetaQRecver.class);
    // TODO: 配置化 metaq.message.maxsize=1024*1024
    private static final int MAX_SIZE = 1024*1024;
    private static final String DEFAULT_GROUP = "default-group";

    private String group;
    private String topic;
    private SessionType sessionType;
    private MessageListener messageListner;
    private MessageConsumer consumer;

    public MetaQRecver(String topic, MessageListener messageListner) {
        this(DEFAULT_GROUP, topic, messageListner);
    }

    public MetaQRecver(String group, String topic, MessageListener messageListner) {
        this(SessionType.SIMPLE, group, topic, messageListner);
    }

    public MetaQRecver(SessionType sessionType, String topic, MessageListener messageListner) {
        this(sessionType, DEFAULT_GROUP, topic, messageListner);
    }

    public MetaQRecver(SessionType sessionType, String group, String topic, MessageListener messageListner) {
        this.group = group;
        this.topic = topic;
        this.sessionType = sessionType;
        this.messageListner = messageListner;
    }

    public MetaQRecver receive() {
        consumer = ConsumerFactory.create(sessionType, group);
        try {
            consumer.subscribe(topic, MAX_SIZE, messageListner);
            consumer.completeSubscribe();
        } catch (Exception e) {
            logger.error("Initialize metaq recver '{}' failed", messageListner);
            throw new RuntimeException(e.getMessage());
        }
        return this;
    }

    @Override
    public void close() throws IOException {
        try {
            if (consumer != null) consumer.shutdown();
        } catch (MetaClientException e) {
            logger.error("Shutdown metaq recver '{}' failed: {}", messageListner, e.getMessage());
        }
    }

}
