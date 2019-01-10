package com.ailk.ecaop.biz.user;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("qryRecvFee")
public class QryRecvFeeProcessors extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {

        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trades.qcpq.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.InvoiceForSer"); 
        lan.xml2Json("ecaop.trades.qcpq.template", exchange);
        // 处理返回
        Map body = (Map) exchange.getOut().getBody();
        List<Map> recvFeeRecInfo = (List) body.get("recvFeeRecInfo");
        Map retMap = new HashMap();
        retMap.put("payInfoList", recvFeeRecInfo);
        exchange.getOut().setBody(retMap);

    }
}
