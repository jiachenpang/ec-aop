package org.n3r.metaq;

import org.n3r.core.lang.RClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson.JSON;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class MetaQUtils {
    private static Logger logger = LoggerFactory.getLogger(MetaQUtils.class);

    private static final LoadingCache<String, Class<?>> classCache = CacheBuilder.newBuilder().build(
            new CacheLoader<String, Class<?>>() {
                @Override
                public Class<?> load(String key) {
                    return RClass.loadClass(key);
                }
            });

    public static String toMetaQMessage(String topic, Object msg, int expSeconds) {
         MetaQMessage metaqMessage = new MetaQMessage();
         metaqMessage.setTopic(topic);
         metaqMessage.setExp(expSeconds);
         metaqMessage.setJsonMsg(JSON.toJSONString(msg));
         metaqMessage.setClazz(msg.getClass().getName());
        return JSON.toJSONString(metaqMessage);
    }

    public static MetaQMessage parseMetaQMessage(String message) {
        MetaQMessage metaqMsg = null;
        try {
            metaqMsg = JSON.parseObject(message, MetaQMessage.class);
        }
        catch (Exception ex) {
            logger.warn("parse metaq message exception", ex);
            return null;
        }

        long costs = System.currentTimeMillis() - metaqMsg.getSts();

        if (metaqMsg.getExp() > 0 && costs > metaqMsg.getExp() * 1000) {
            logger.info("recved expired message:{}", metaqMsg);
            return null; // Ignore expired Message
        }

        logger.info("recved message:{}", metaqMsg);
        String clazz = metaqMsg.getClazz();
        if (clazz == null) return metaqMsg;

        Class<?> cls = classCache.getUnchecked(clazz);

        Object msgObject = null;
        try {
            msgObject = JSON.parseObject(metaqMsg.getJsonMsg(), cls);
        }
        catch (Exception ex) {
            logger.warn("parse metaq message exception", ex);
        }
        metaqMsg.setMsg(msgObject);

        return metaqMsg;
    }

}
