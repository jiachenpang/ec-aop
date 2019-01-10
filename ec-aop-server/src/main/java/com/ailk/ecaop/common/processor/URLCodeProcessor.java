package com.ailk.ecaop.common.processor;

import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

public abstract class URLCodeProcessor extends BaseAopProcessor implements ParamsAppliable {

    @Override
    public void process(Exchange exchange) throws Exception {

        if (exchange.getException() != null) {
            return;
        }
        String urlString = (String) exchange.getOut().getBody();
        String after = doCode(urlString);
        exchange.getOut().setBody(after);
    }

    public abstract String doCode(String con);

    @Override
    public void applyParams(String[] params) {

    }

}
