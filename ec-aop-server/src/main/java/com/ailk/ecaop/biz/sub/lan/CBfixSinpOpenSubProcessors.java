package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("CBfixSinpOpenSubProcessors")
public class CBfixSinpOpenSubProcessors extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.masb.sfss.orderSubParametersMapping" };

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];

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
        msg.put("sendTypeCode", "0");
        String[] copyStr = { "feeCategory", "feeId", "feeDes", "origFee", "reliefResult", "reliefFee", "realFee" };
        List<Map> subOrderSubReq = new ArrayList<Map>();
        List<Map> feeInfoList = (List<Map>) msg.get("feeInfo");
        System.out.println("+++++++++++++++++++" + feeInfoList + "+++++++++++++++++");
        List<Map> feeInfo = new ArrayList<Map>();
        Map tempMap = new HashMap();
        tempMap.put("subProvinceOrderId", provOrderId);
        tempMap.put("subOrderId", provOrderId);
        if (null != feeInfoList && !feeInfoList.isEmpty()) {
            for (Map feeMap : feeInfoList) {
                Map tempFeeMap = new HashMap();
                if (feeMap.isEmpty()) {
                    continue;
                }
                String calculateId = GetSeqUtil.getSeqFromCb();
                MapUtils.arrayPut(tempFeeMap, feeMap, copyStr);
                tempFeeMap.put("calculateTag", "N");
                tempFeeMap.put("reliefResult", "");
                tempFeeMap.put("isPay", "1");
                tempFeeMap.put("payTag", "0");
                tempFeeMap.put("calculateId", calculateId);
                tempFeeMap.put("calculateDate", GetDateUtils.getDate());
                tempFeeMap.put("payId", calculateId);
                feeInfo.add(tempFeeMap);
            }
        }
        System.out.println("===================" + feeInfo + "=================");
        if (null != feeInfo || !feeInfo.isEmpty()) {
            tempMap.put("feeInfo", feeInfo);
        }
        subOrderSubReq.add(tempMap);
        List<Map> payInfoList = new ArrayList<Map>();
        Map payInfo = (Map) msg.get("payInfo");
        payInfo.put("payType", ChangeCodeUtils.changePayType(payInfo.get("payType")));
        payInfo.put("payMoney", msg.get("origTotalFee"));
        payInfo.put("subProvinceOrderId", provOrderId);
        payInfo.put("remark", "1231654646");
        payInfoList.add(payInfo);
        msg.put("payInfo", payInfoList);
        msg.put("subOrderSubReq", subOrderSubReq);
        Map para = new HashMap();
        para.put("paraId", "StrDerateFee");
        para.put("paraValue", "false");
        msg.put("para", para);
        body.put("msg", msg);
        System.out.println("++++++++++++++" + msg + "++++++++++++++++");
        exchange.getIn().setBody(body);
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.masb.sfss.orderSubTemplate", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        retMap.put("provOrderId", provOrderId);
        exchange.getOut().setBody(retMap);

    }

    @Override
    public void applyParams(String[] params) {
        // TODO Auto-generated method stub
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }

    }
}
