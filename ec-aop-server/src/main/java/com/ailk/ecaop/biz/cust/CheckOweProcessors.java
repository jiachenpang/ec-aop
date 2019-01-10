package com.ailk.ecaop.biz.cust;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("checkOwe")
public class CheckOweProcessors extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        if (200 != Integer.valueOf(exchange.getProperty(Exchange.HTTP_STATUSCODE).toString())) {
            return;
        }
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        if (!"2".equals(msg.get("opeSysType"))) {
            return;
        }
        // 只有使用号码查询客户资料时,才会调用欠费校验接口
        if (null == msg.get("serialNumber") || "".equals(msg.get("serialNumber"))) {
            return;
        }
        String[] copyArray = { "operatorId", "province", "city", "district", "channelId", "channelType",
                "serviceClassCode" };
        if (null == msg.get("areaCode")) {
            msg.put("areaCode", "");
        }
        Map oweMap = MapUtils.asMap("queryType", "1", "queryMode", "1");
        oweMap.put("serialNumber", msg.get("areaCode") + msg.get("serialNumber").toString());
        MapUtils.arrayPut(oweMap, msg, copyArray);
        Exchange oweExchange = ExchangeUtils.ofCopy(exchange, oweMap);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trades.cuck.checkOwe.ParametersMapping", oweExchange);
        CallEngine.wsCall(oweExchange, "ecaop.comm.conf.url.cbss.services.PaymentSer");
        lan.xml2JsonNoError("ecaop.trades.cuck.checkOwetemplate", oweExchange);
        List<Map> oweFeeInfo = (ArrayList<Map>) oweExchange.getOut().getBody(Map.class).get("oweFeeInfo");
        if (null == oweFeeInfo || 0 == oweFeeInfo.size()) {
            return;
        }
        Map outMap = JSON.parseObject(exchange.getOut().getBody(String.class));
        for (Map owe : oweFeeInfo) {
            if (Integer.valueOf(owe.get("minPayFee").toString()) > 0) {
                outMap.put("arrearageFlag", "1");
                List<Map> arrearageMess = new ArrayList<Map>();
                arrearageMess.add(MapUtils.asMap("serialNumber",
                        msg.get("areaCode").toString() + msg.get("serialNumber"),
                        "arrearageFee", owe.get("minPayFee")));
                outMap.put("arrearageMess", arrearageMess);
            }
        }
        exchange.getOut().setBody(outMap);
    }
}
