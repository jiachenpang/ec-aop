package com.ailk.ecaop.biz.sub.gdjk;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.ailk.ecaop.common.utils.ReleaseResUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("OpenSubProcessor")
public class OpenSubProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map tempBody = new HashMap(body);
        // 增加号卡中心逻辑
        Map msg = null;
        if (body.get("msg") instanceof Map) {
            msg = (Map) body.get("msg");
        }
        else {
            msg = JSON.parseObject(body.get("msg").toString());
        }
        String provOrderId = (String) msg.get("provOrderId");
        Map inMap = new HashMap();
        inMap.put("SUBSCRIBE_ID", provOrderId);
        List result = NumCenterUtils.qryNumSwitchByProvOrderId(inMap); // 查库获取是否走号卡中心
        msg.put("numSwitch", "1");
        if (!IsEmptyUtils.isEmpty(result)) { // 号卡中心流程
            msg.put("numSwitch", "0");
        }
        msg.put("accessType", "01");
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        LanUtils lan = new LanUtils();
        try {
            lan.preData("ecaop.gdjk.opsb.GdjkParamtersmapping", exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
            lan.xml2Json4ONS("ecaop.gdjk.opsb.template", exchange);
        }
        catch (Exception e) {
            exchange.getIn().setBody(tempBody);
            ReleaseResUtils rr = new ReleaseResUtils();
            rr.releaseTerminal(exchange);
            throw e;
        }
    }
}
