package com.ailk.ecaop.biz.cust;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

/**
 * 18位身份证转为01
 * 
 * @author
 */

@EcRocTag("CertTypeChg")
public class CertTypeChgProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String certType = (String) msg.get("certType");
        if ("02".equals(certType)) {
            msg.put("type", "01");
        }
    }
}
