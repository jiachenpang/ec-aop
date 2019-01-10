package com.ailk.ecaop.biz.query;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

@EcRocTag("RealPersonPhotoQry")
public class RealPersonPhotoQryProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String sysCode = "2";
        Map msgMap = new HashMap();
        msgMap.put("OrderID", msg.get("orderId"));
        msgMap.put("Para1", msg.get("para"));
        msgMap.put("SysCode", sysCode);
        msg.put("REQ_STR", msgMap);
        Map bodyMap = new HashMap();
        bodyMap.put("msg", msg);
        exchange.getIn().setBody(bodyMap);

    }

}
