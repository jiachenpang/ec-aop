package com.ailk.ecaop.biz.sub.same;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("brdSameOpenSub")
public class BrdSameOpenSubProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        // TODO Auto-generated method stub
        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) exchange.getIn().getBody(Map.class).get("msg"));
        }
        else {
            msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        }
        LanUtils lan = new LanUtils();
        String provOrderId = String.valueOf(msg.get("provOrderId"));
        String orderNo = (String) msg.get("orderNo");
        if (StringUtils.isEmpty(orderNo)) {
            msg.put("orderNo", provOrderId);
        }
        msg.put("provOrderId", provOrderId);

        msg.put("noteNo", msg.get("invoiceNo"));
        msg.put("sendTypeCode", "0");
        String[] copyStr = { "feeCategory", "feeId", "feeDes", "origFee", "reliefFee", "reliefResult", "realFee" };
        List<Map> subOrderSubReq = new ArrayList<Map>();
        List<Map> feeInfoList = (List<Map>) msg.get("feeInfo");
        List<Map> feeInfo = new ArrayList<Map>();
        Map tempMap = new HashMap();
        tempMap.put("subProvinceOrderId", provOrderId);
        tempMap.put("subOrderId", provOrderId);
        for (Map feeMap : feeInfoList) {
            Map tempFeeMap = new HashMap();
            if (feeMap.isEmpty()) {
                continue;
            }
            String calculateId = GetSeqUtil.getSeqFromCb();
            MapUtils.arrayPut(tempFeeMap, feeMap, copyStr);
            tempFeeMap.put("calculateTag", "N");
            tempFeeMap.put("isPay", "1");
            tempFeeMap.put("payTag", "1");
            tempFeeMap.put("calculateId", calculateId);
            tempFeeMap.put("calculateDate", GetDateUtils.getDate());
            tempFeeMap.put("payId", calculateId);
            feeInfo.add(tempFeeMap);
        }
        if (null != feeInfo || !feeInfo.isEmpty()) {
            tempMap.put("feeInfo", feeInfo);
        }
        subOrderSubReq.add(tempMap);
        List<Map> payInfoList = new ArrayList<Map>();
        Map payInfo = (Map) msg.get("payInfo");
        payInfo.put("payType", ChangeCodeUtils.changePayType(payInfo.get("payType")));
        payInfo.put("payMoney", msg.get("origTotalFee"));
        payInfo.put("subProvinceOrderId", provOrderId);
        payInfoList.add(payInfo);
        msg.put("payInfo", payInfoList);
        msg.put("subOrderSubReq", subOrderSubReq);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        String opeSysType = (String) msg.get("opeSysType");
        String province = (String) msg.get("province");
        lan.preData("ecaop.trades.sbso.ParametersMapping", exchange);
        if ("2".equals(opeSysType)) { // CBSS
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        }
        else {
            if ("17|18|11|76|91|97".contains(province)) {
                CallEngine.wsCall(exchange, "ecaop.comm.conf.url.OrderSub" + "." + msg.get("province"));
            }
            else {
                CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
            }
        }
        lan.xml2Json("ecaop.trades.sbso.template", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        retMap.put("provOrderId", provOrderId);
        exchange.getOut().setBody(retMap);

    }
}
