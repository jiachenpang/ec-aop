package stevenTest.network;

import java.util.Map;
import java.util.concurrent.Callable;

import org.n3r.ecaop.core.Exchange;

public class FixPreSubmitThread implements Callable {

    private final Exchange exchange;

    public FixPreSubmitThread(Exchange exchange) {
        this.exchange = exchange;
    }

    @Override
    public Map call() throws Exception {
        // new LanUtils().preData("ecaop.param.mapping.test", exchange);
        // CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub.local");
        // new LanUtils().xml2Json4ONS("ecaop.template.3g.checkProvince", exchange);
        return exchange.getOut().getBody(Map.class);
    }

}
