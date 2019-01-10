package com.ailk.ecaop.common.utils;

import java.util.Map;
import java.util.concurrent.Callable;

import org.n3r.ecaop.core.Exchange;

import com.ailk.ecaop.base.CallEngine;

public class QryCommInfoThread implements Callable {

    private final Exchange exchange;

    public QryCommInfoThread(Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public Map call() throws Exception {
        new LanUtils().preData("ecaop.trades.cuck.qryCommInfo.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.CommInfoForNorthSer");
        new LanUtils().xml2Json("ecaop.trades.cuck.qryCommInfo.checkOwetemplate", exchange);
        return exchange.getOut().getBody(Map.class);
    }
}
