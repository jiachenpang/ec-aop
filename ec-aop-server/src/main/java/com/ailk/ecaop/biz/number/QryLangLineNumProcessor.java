package com.ailk.ecaop.biz.number;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("qryLangLineNum")
public class QryLangLineNumProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        String methodCode = exchange.getMethodCode();
        exchange.setMethodCode("qlln");
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.masb.qlln.qryLangLineNumParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.numberser");
        lan.xml2Json("ecaop.masb.qlln.qryLangLineNumTemplate", exchange);
        exchange.setMethodCode(methodCode);
    }
}
