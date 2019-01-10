package com.ailk.ecaop.biz.sub.olduser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

@EcRocTag("OlduOpenSubProcessor")
public class OlduOpenSubProcessor extends BaseAopProcessor {

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
        msg.put("orderNo", provOrderId);
        msg.put("noteNo", msg.get("invoiceNo"));
        msg.put("sendTypeCode", "0");
        String[] copyStr = {"feeCategory", "feeId", "feeDes", "origFee", "reliefFee", "reliefResult", "realFee"};
        List<Map> subOrderSubReq = new ArrayList<Map>();
        //增加是否为空判断       modify by wangrj3
        List<Map> feeInfoList = msg.get("feeInfo") != null ? (List<Map>) msg.get("feeInfo") : new ArrayList<Map>();
        List<Map> feeInfo = new ArrayList<Map>();
        Map tempMap = new HashMap();
        tempMap.put("subProvinceOrderId", provOrderId);
        tempMap.put("subOrderId", provOrderId);

        //判断feeInfoList是否为null，避免msg中没有feeInfo节点而在下面使用时报空指针问题     modify by wangrj3
        if (null != feeInfoList || !feeInfoList.isEmpty()) {
            for (Map feeMap : feeInfoList) {
                Map tempFeeMap = new HashMap();
                if (feeMap.isEmpty()) {
                    continue;
                }
                String calculateId = GetSeqUtil.getSeqFromCb();
                MapUtils.arrayPut(tempFeeMap, feeMap, copyStr);
                System.out.println("==========tempFeeMap" + tempFeeMap);
                tempFeeMap.put("calculateTag", "N");
                tempFeeMap.put("isPay", "1");
                tempFeeMap.put("payTag", "1");
                tempFeeMap.put("calculateId", calculateId);
                tempFeeMap.put("calculateDate", GetDateUtils.getDate());
                tempFeeMap.put("payId", calculateId);
                feeInfo.add(tempFeeMap);
            }
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
        System.out.println("==========subOrderSubReq" + subOrderSubReq);
        body.put("msg", msg);
        exchange.getIn().setBody(body);

        //请求地址区分北六和南25，进行判断请求不同的地址      add by wangrj3
        if(null != msg.get("opeSysType") && msg.get("opeSysType").equals("2")) {//南25只允许做4G的
            lan.preData("ecaop.trades.mpsb.ParametersMapping", exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");// modify by wangrj3
        }else{//3G只允许北六
            lan.preData("ecaop.trades.ofcs.N6.ParametersMapping", exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));// modify by wangrj3
        }
        lan.xml2Json("ecaop.trades.mpsb.template", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        retMap.put("provOrderId", provOrderId);
        exchange.getOut().setBody(retMap);

    }

}
