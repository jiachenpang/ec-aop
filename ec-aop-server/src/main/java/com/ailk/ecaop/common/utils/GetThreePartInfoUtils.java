package com.ailk.ecaop.common.utils;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.alibaba.fastjson.JSON;

public class GetThreePartInfoUtils implements ParamsAppliable {

    public GetThreePartInfoUtils() {
        applyParams(PARAM_ARRAY);
    }

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];
    private static final String[] PARAM_ARRAY = {"ecaop.trade.cbss.checkUserParametersMapping"};
    LanUtils lan = new LanUtils();

    public Map process(Exchange exchange) throws Exception {
        try {
            preParam4ThreePart(exchange.getIn().getBody(Map.class), exchange);
            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
            lan.xml2Json("ecaop.trades.cbss.threePart.template", exchange);
        }
        catch (EcAopServerBizException e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
        Map retThreePartMap = new HashMap();
        Object body = exchange.getOut().getBody();
        if (body instanceof String) {
            retThreePartMap = JSON.parseObject((String) body);
        }
        else {
            retThreePartMap = exchange.getOut().getBody(Map.class);
        }
        return retThreePartMap;
    }

    private void preParam4ThreePart(Map body, Exchange exchange) {
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        msg.put("tradeTypeCode", "9999");
        msg.put("getMode", "111001000000101311110000000000");
        String serialNumber = (String) msg.get("serialNumber");
        Object areaCode = msg.get("areaCode");
        if (null != areaCode && !"".equals(areaCode)) {
            msg.put("serialNumber", areaCode + serialNumber);
            // 河南增加处理分支，外围 传入号码会有区号
            if ("hapr".equals(exchange.getAppCode())) {
                msg.put("serialNumber", serialNumber);
            }
        }
        body.put("msg", msg);

    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }

}
