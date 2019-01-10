package com.ailk.ecaop.common.processor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.DefaultExchange;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import stevenTest.network.FixPreSubmitThread;

import com.ailk.ecaop.common.utils.MapUtils;

@EcRocTag("testThread")
public class TestThreadProcess extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        List province = (List) ((Map) body.get("msg")).get("provinceList");
        ArrayList result = new ArrayList();
        Exchange[] exchangeList = new Exchange[province.size()];
        Future[] f = new Future[province.size()];
        ExecutorService pool = Executors.newFixedThreadPool(province.size());
        Callable[] c = new Callable[province.size()];
        for (int i = 0; i < province.size(); i++) {
            exchangeList[i] = ofCopy(exchange, MapUtils.asMap("province", province.get(i)));
        }
        for (int i = 0; i < province.size(); i++) {
            c[i] = new FixPreSubmitThread(exchangeList[i]);
            f[i] = pool.submit(c[i]);
        }
        for (int i = 0; i < province.size(); i++) {
            result.add(f[i].get());
        }
        pool.shutdown();
        Message out = new DefaultMessage();
        out.setBody(MapUtils.asMap("result", result));
        exchange.setOut(out);
    }

    private Exchange ofCopy(Exchange exchange, Map msg) {
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
        message.setBody(MapUtils.asMap("msg", msg));
        retExchange.setIn(message);
        return retExchange;
    }
}
