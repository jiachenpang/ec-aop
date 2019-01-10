package com.ailk.ecaop.biz.query;

import java.util.List;
import java.util.Map;

import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;

public class AssureCheckProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.trades.qacc.ParametersMapping" };
    private final ParametersMappingProcessor[] PMP = new ParametersMappingProcessor[PARAM_ARRAY.length];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.put("certType", CertTypeChangeUtils.certTypeMall2Cbss(msg.get("certType").toString()));
        LanUtils lan = new LanUtils();
        lan.preData(PMP[0], exchange);
        CallEngine.wsCall(exchange, "");
        lan.xml2Json("ecaop.trades.qacc.template", exchange);
        List<Map> assureInfo = (List<Map>) (exchange.getOut().getBody(Map.class).get("assureInfo"));
        if (IsEmptyUtils.isEmpty(assureInfo)) {
            return;
        }
        exchange.getOut().setBody(assureInfo.get(0));
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            PMP[i] = new ParametersMappingProcessor();
            PMP[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
