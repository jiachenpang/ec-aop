package com.ailk.ecaop.biz.sub.lan;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("soleBusiAccnt")
public class SoleBusiAccnt extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        String methodCode = exchange.getMethodCode();
        exchange.setMethodCode("sbac");
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.masb.sbac.soleBusiAccntParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.instser");
        lan.xml2Json("ecaop.masb.sbac.soleBusiAccntTemplate", exchange);
        exchange.setMethodCode(methodCode);
    }
}
