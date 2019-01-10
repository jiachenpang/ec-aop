package org.n3r.metaq.logback;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.n3r.config.Config;
import org.n3r.core.lang.RByte;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.spi.DeferredProcessingAware;
import ch.qos.logback.core.status.ErrorStatus;

import com.taobao.metamorphosis.Message;
import com.taobao.metamorphosis.client.MetaClientConfig;
import com.taobao.metamorphosis.client.extension.AsyncMessageSessionFactory;
import com.taobao.metamorphosis.client.extension.AsyncMetaMessageSessionFactory;
import com.taobao.metamorphosis.client.extension.producer.AsyncMessageProducer;
import com.taobao.metamorphosis.exception.MetaClientException;
import com.taobao.metamorphosis.utils.ZkUtils;

public class MetaQAppender<E> extends UnsynchronizedAppenderBase<E> {
    protected String topic;
    protected AsyncMessageSessionFactory messageSessionFactory;
    protected volatile AsyncMessageProducer producer;
    protected ZkUtils.ZKConfig zkConfig = new ZkUtils.ZKConfig();

    private void initMeta() {
        if (this.producer != null) return;
        synchronized (this) {
            if (this.producer != null) return;
            MetaClientConfig metaClientConfig = new MetaClientConfig();
            metaClientConfig.setZkConfig(this.zkConfig);
            try {
                messageSessionFactory = new AsyncMetaMessageSessionFactory(metaClientConfig);
                this.producer = messageSessionFactory.createAsyncProducer();
            } catch (MetaClientException e) {
                addStatus(new ErrorStatus("Init meta producer failed" + this.getName(), this, e));
            }
        }
    }

    @Override
    protected void append(E eventObject) {
        if (!isStarted()) {
            return;
        }
        subAppend(eventObject);
    }

    private void subAppend(E event) {
        try {
            if (event instanceof DeferredProcessingAware) {
                ((DeferredProcessingAware) event).prepareForDeferredProcessing();
            }

            String message = ((ILoggingEvent) event).getMessage();
            if (message == null) return;

            sendMessage(message);

        } catch (IOException ioe) {
            this.started = false;
            addStatus(new ErrorStatus("IO failure in appender", this, ioe));
        }
    }

    private void sendMessage(String message) throws IOException {
        initMeta();
        if (this.producer != null) {
            this.producer.publish(this.topic);
            this.producer.asyncSendMessage(new Message(this.topic, RByte.toBytes(message)));
        }
        else {
            throw new IOException("Null producer");
        }
    }

    @Override
    public void stop() {
        if (!isStarted()) {
            return;
        }
        this.started = false;
        if (this.producer == null)
            return;
        try {
            this.producer.shutdown();
            this.messageSessionFactory.shutdown();
        } catch (MetaClientException e) {
        }
    }

    public String getTopic() {
        return this.topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public void setZkConnect(String zkConnect) {
        if (StringUtils.isEmpty(zkConnect))
            this.zkConfig.zkConnect = Config.getStr("ecaop.log.metaq.zkconnect", "127.0.0.1:2181");
        else
            this.zkConfig.zkConnect = zkConnect;
    }

}
