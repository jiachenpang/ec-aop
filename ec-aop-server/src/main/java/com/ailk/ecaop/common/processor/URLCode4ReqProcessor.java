package com.ailk.ecaop.common.processor;

import java.util.Map;

import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

public abstract class URLCode4ReqProcessor extends BaseAopProcessor implements ParamsAppliable {

    @Override
    public void process(Exchange exchange) throws Exception {

        if (exchange.getException() != null) {
            return;
        }
        Map body = exchange.getIn().getBody(Map.class);
        String urlString = body.get("msg").toString();
        String after = doCode(urlString);
        body.put("msg", after);
        exchange.getIn().setBody(body);
    }

    public abstract String doCode(String con);

    @Override
    public void applyParams(String[] params) {

    }

}
