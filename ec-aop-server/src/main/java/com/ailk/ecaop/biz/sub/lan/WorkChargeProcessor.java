package com.ailk.ecaop.biz.sub.lan;

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

@EcRocTag("workCharge")
@SuppressWarnings(value = { "unchecked", "rawtypes" })
public class WorkChargeProcessor extends BaseAopProcessor implements ParamsAppliable {
    private final LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = { "ecaop.trades.socs.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Map chgConstrucOrderReq = (Map) msg.get("chgConstrucOrderReq");
        if (IsEmptyUtils.isEmpty(chgConstrucOrderReq)) {
            throw new EcAopServerBizException("9999", "chgConstrucOrderReq为必填!");
        }
        msg.putAll(chgConstrucOrderReq);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        try {
            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.ConstructionOrderForAopSer");
            lan.xml2Json4ONS("ecaop.trades.socs.template", exchange);
        } catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用chgConstructionOrder接口失败！" + e.getMessage());
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
