package org.n3r.metaq;

import java.io.Closeable;
import java.io.IOException;

import org.n3r.core.lang.RByte;
import org.n3r.metaq.cache.ProducerCache;
import org.n3r.metaq.session.SessionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.taobao.metamorphosis.Message;
import com.taobao.metamorphosis.client.producer.MessageProducer;
import com.taobao.metamorphosis.client.producer.SendMessageCallback;
import com.taobao.metamorphosis.client.producer.SendResult;

public class MetaQSender implements Closeable {
    private static final Logger logger = LoggerFactory.getLogger(MetaQSender.class);

    private String topic;
    private SessionType sessionType;

    public MetaQSender(String topic) {
        this(SessionType.SIMPLE, topic);
    }

    public MetaQSender(SessionType sessionType, String topic) {
        this.topic = topic;
        this.sessionType = sessionType;
    }

    public MetaQSender send(Object msg) {
        return this.send(msg, 0);
    }

    public MetaQSender send(Object msg, int exp) {
        MessageProducer producer = ProducerCache.getProducer(sessionType, topic);
        final String metaqMsg = MetaQUtils.toMetaQMessage(topic, msg, exp);
        producer.sendMessage(new Message(topic, RByte.toBytes(metaqMsg)), new SendMessageCallback() {
            @Override
            public void onMessageSent(SendResult result) {
                if (result.isSuccess())
                logger.debug("Send message successfully, sent to {}", result.getPartition());
                else
                logger.error("Send message failed, message: {}, error message: {}", metaqMsg, result.getErrorMessage());
            }

            @Override
            public void onException(Throwable e) {
                logger.error("Send message throw exception");
                e.printStackTrace();
            }
        });
        return this;
    }

    @Override
    public void close() throws IOException {
        ProducerCache.shutdownProducer(topic, sessionType);
    }

}
