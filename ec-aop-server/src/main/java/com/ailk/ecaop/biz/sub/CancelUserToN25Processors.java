package com.ailk.ecaop.biz.sub;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("cancelUserToN25")
public class CancelUserToN25Processors extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trades.sccs.paramtersmapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];

    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.CustCleSer");
        lan.xml2Json("ecaop.trades.sccs.template", exchange);

        Map rspMap = exchange.getOut().getBody(Map.class);
        if (!IsEmptyUtils.isEmpty(rspMap)) {
            exchange.getOut().setBody(rspMap);
        }
        else {
            throw new EcAopServerBizException("9999", "返回应答信息为空");
        }
    }

    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
