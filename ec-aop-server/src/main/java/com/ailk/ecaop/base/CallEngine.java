package com.ailk.ecaop.base;

import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.AopCall;
import org.n3r.ecaop.core.processor.NumCenterAopCall;
import org.n3r.ecaop.core.processor.WsCall;

/**
 * 
 * @author Lionel
 * 
 */
public class CallEngine {

    private Exchange exchange;

    private CallEngine(Exchange exchange) {
        this.exchange = exchange;
    }

    public static CallEngine getEngine(Exchange exchange) {
        return new CallEngine(exchange);
    }

    public void wsCall(String urlKey) throws Exception {

        WsCall call = new WsCall();
        call.applyParams(str2Arr(urlKey));
        call.process(exchange);
    }

    public static void wsCall(Exchange exchange, String urlKey) throws Exception {
        CallEngine.getEngine(exchange).wsCall(urlKey);
    }

    public void aopCall(String urlKey) throws Exception {

        AopCall call = new AopCall();
        call.applyParams(str2Arr(urlKey));
        call.process(exchange);
    }

    public void numCenterCall(String urlKey) throws Exception {

        NumCenterAopCall call = new NumCenterAopCall();
        call.applyParams(str2Arr(urlKey));
        call.process(exchange);
    }

    public static void aopCall(Exchange exchange, String urlKey) throws Exception {
        CallEngine.getEngine(exchange).aopCall(urlKey);
    }

    public static void numCenterCall(Exchange exchange, String urlKey) throws Exception {
        CallEngine.getEngine(exchange).numCenterCall(urlKey);
    }

    private String[] str2Arr(String str) {
        String[] in = new String[1];
        in[0] = str;
        return in;
    }

}
