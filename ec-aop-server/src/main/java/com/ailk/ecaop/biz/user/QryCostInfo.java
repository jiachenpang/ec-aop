package com.ailk.ecaop.biz.user;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.LanUtils;

/**
 * 余额查询
 */
@EcRocTag("qryCostInfo")
public class QryCostInfo extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");
        msg.put("accProvince", msg.get("province"));
        msg.put("accCity", msg.get("city"));
        String areaCode = ChangeCodeUtils.changeEparchy(msg);
        msg.put("code", areaCode);
        msg.put("userNumber", msg.get("number"));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.params.qcacq.ParametersMapping", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.cbss.services.PayMentFee");
        lan.xml2Json4ONS("ecaop.trades.cbss.qcacq.template", exchange);

        // 处理返回
        Map retMap = exchange.getOut().getBody(Map.class);
        String realTimeFee = (String) retMap.get("realTimeFee");
        String realTimeBalance = (String) retMap.get("realTimeBalance");
        retMap.put("realTimeFee", fee(realTimeFee));
        retMap.put("realTimeBalance", fee(realTimeBalance));
        exchange.getOut().setBody(retMap);
    }

    // 处理费用
    private static int fee(String fee) {
        return Integer.parseInt(fee) / 10;
    }
}
