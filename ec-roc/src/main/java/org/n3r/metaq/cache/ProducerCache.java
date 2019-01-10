package org.n3r.metaq.cache;

import org.n3r.metaq.session.SessionType;
import org.n3r.metaq.session.factory.ProducerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.taobao.metamorphosis.client.producer.MessageProducer;
import com.taobao.metamorphosis.exception.MetaClientException;

/**
 * MessageConsumer无法复用，不进行缓存。
 * @author wanglei
 *
 */
public class ProducerCache {
    private static final Logger logger = LoggerFactory.getLogger(ProducerCache.class);


    private static final LoadingCache<ProducerBean, MessageProducer> producerCache = CacheBuilder.newBuilder().build(
            new CacheLoader<ProducerBean, MessageProducer>() {
                @Override
                public MessageProducer load(ProducerBean key) {
                    MessageProducer producer = ProducerFactory.create(key.getType());
                    producer.publish(key.getTopic());
                    return producer;
                }
            });


    public static MessageProducer getProducer(final SessionType sessionType, String topic) {
        ProducerBean producerBean = new ProducerBean(topic, sessionType);
        return producerCache.getUnchecked(producerBean);
    }

    public static void shutdownProducer(String topic, SessionType sessionType) {
        ProducerBean producerBean = new ProducerBean(topic, sessionType);
        MessageProducer producer = producerCache.getUnchecked(producerBean);
        try {
            producer.shutdown();
        } catch (MetaClientException e) {
            logger.error("Shutdown producer '{}' failed", topic);
        } finally {
            producerCache.invalidate(producerBean);
        }
    }

}
