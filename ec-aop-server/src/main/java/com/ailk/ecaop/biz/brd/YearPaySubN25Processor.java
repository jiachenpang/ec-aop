package com.ailk.ecaop.biz.brd;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.biz.sub.lan.YearPayEcs4South25Sub;

@EcRocTag("yearPaySubN25")
public class YearPaySubN25Processor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.masb.spec.sProductChgParametersMapping",
    "ecaop.masb.odsb.ActivityAryParametersMapping" };
    protected final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];

    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map preSubmitReturnMap = new HashMap();
        YearPayEcs4South25Sub sub = new YearPayEcs4South25Sub(pmp);
        preSubmitReturnMap = sub.yearPayEcsCheck(exchange, msg);
        Message out = new DefaultMessage();
        Map outMap = new HashMap();
        // 宽带趸交流程，沿用现有流程
        if ("0".equals(preSubmitReturnMap.get("isNoChangeProduct"))) {
            preSubmitReturnMap.remove("isNoChangeProduct");
            outMap = preSubmitReturnMap;
        }
        else {
            outMap = sub.yearPayEcsSub(exchange, preSubmitReturnMap, msg);
        }
        out.setBody(outMap);
        exchange.setOut(out);
    }

    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
