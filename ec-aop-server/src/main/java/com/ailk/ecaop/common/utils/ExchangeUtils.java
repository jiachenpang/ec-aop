package com.ailk.ecaop.common.utils;

import org.n3r.ecaop.core.DefaultExchange;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.impl.DefaultMessage;

import com.alibaba.fastjson.JSON;

public class ExchangeUtils {

    public static Exchange ofCopy(Exchange exchange, Object msg) {
        Exchange retExchange = new DefaultExchange();
        retExchange.setAppCode(exchange.getAppCode());
        retExchange.setAppkey(exchange.getAppkey());
        retExchange.setBizkey(exchange.getBizkey());
        retExchange.setMethod(exchange.getMethod());
        retExchange.setMethodCode(exchange.getMethodCode());
        retExchange.setProvince(exchange.getProvince());
        retExchange.setTimestamp(exchange.getTimestamp());
        retExchange.setProperty("routecode", exchange.getProperty("routecode"));
        retExchange.setProperty("opeSysType", exchange.getProperty("opeSysType"));
        retExchange.setProperty(Exchange.REQLOG, exchange.getProperty(Exchange.REQLOG));
        retExchange.setProperty("isTest", exchange.getProperty("isTest"));
        retExchange.setProperty("bipCode", exchange.getProperty("bipCode"));
        retExchange.setProperty("activityCode", exchange.getProperty("activityCode"));
        Message message = new DefaultMessage();
        // 复制请求内容到新的exchange中,以便mock上使用 by wangmc 20180306
        if ("1".equals(exchange.getProperty("isTest"))) {
            message.setHeaders(exchange.getIn().getHeaders());
        }
        message.setBody(MapUtils.asMap("msg", JSON.toJSON(msg)));
        retExchange.setIn(message);
        return retExchange;
    }

    public static Exchange ofCopy(Object msg, Exchange exchange) {

        Exchange retExchange = new DefaultExchange();
        retExchange.setAppCode(exchange.getAppCode());
        retExchange.setAppkey(exchange.getAppkey());
        retExchange.setBizkey(exchange.getBizkey());
        retExchange.setMethod(exchange.getMethod());
        retExchange.setMethodCode(exchange.getMethodCode());
        retExchange.setProvince(exchange.getProvince());
        retExchange.setTimestamp(exchange.getTimestamp());
        retExchange.setProperty("routecode", exchange.getProperty("routecode"));
        retExchange.setProperty("opeSysType", exchange.getProperty("opeSysType"));
        retExchange.setProperty(Exchange.REQLOG, exchange.getProperty(Exchange.REQLOG));
        Message message = new DefaultMessage();
        message.setBody(JSON.toJSON(msg));
        retExchange.setIn(message);
        return retExchange;
    }

    /* 号卡中心回调AOP */

    public static Exchange ofCopy(Exchange exchange, String callback) {
        Exchange retExchange = new DefaultExchange();
        retExchange.setAppCode(exchange.getAppCode());
        retExchange.setAppkey(exchange.getAppkey());
        retExchange.setBizkey(exchange.getBizkey());

        retExchange.setMethod(exchange.getMethod());
        retExchange.setMethodCode(exchange.getMethodCode());
        retExchange.setProvince(exchange.getProvince());
        retExchange.setTimestamp(exchange.getTimestamp());
        retExchange.setProperty("routecode", exchange.getProperty("routecode"));
        retExchange.setProperty("opeSysType", exchange.getProperty("opeSysType"));
        retExchange.setProperty(Exchange.REQLOG, exchange.getProperty(Exchange.REQLOG));
        Message message = new DefaultMessage();
        message.setBody(callback);
        retExchange.setIn(message);
        return retExchange;
    }

}
