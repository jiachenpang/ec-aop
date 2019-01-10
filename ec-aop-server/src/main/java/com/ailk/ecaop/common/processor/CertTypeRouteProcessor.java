package com.ailk.ecaop.common.processor;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

@EcRocTag("certTypeRoute")
public class CertTypeRouteProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = (Map) exchange.getIn().getBody();
        Map msgObject = (Map) body.get("msg");
        String checkType = (String) msgObject.get("checkType");
        String certType = (String) msgObject.get("certType");

        if ("0".equals(checkType) && StringUtils.isNotEmpty(certType) && "07,13,14,15,17,18,21".contains(certType)) {
            exchange.setProperty("certTypeCode", "FBS");
        }
        else {
            exchange.setProperty("certTypeCode", "ONS");
        }

    }
}
