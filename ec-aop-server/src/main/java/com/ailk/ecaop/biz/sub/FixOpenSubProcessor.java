package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.extractor.TransIdFromRedisValueExtractor;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("FixOpenSub")
public class FixOpenSubProcessor extends BaseAopProcessor {

    LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        Map msg = dealInParams(exchange);
        String provOrderId = (String) msg.get("provOrderId");

        lan.preData("ecaop.trades.scmc.cancel.paramtersmapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.scmc.cancel.template", exchange);
        // Map out = exchange4CB.getOut().getBody(Map.class);
        // if (!"0000".equals(out.get("code"))) {
        // out.put("code", "0001");
        // }

        Map retMap = new HashMap();
        retMap.put("provOrderId", provOrderId);
        exchange.getOut().setBody(retMap);
    }

    private Object creatTransIDO(Exchange exchange) {
        String str[] = { "@50"
        };
        TransIdFromRedisValueExtractor transId = new TransIdFromRedisValueExtractor();
        transId.applyParams(str);
        return transId.extract(exchange);
    }

    /* 处理请求参数 */
    private Map dealInParams(Exchange exchange) {
        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");

        Map payInfos = (Map) msg.get("payInfo");
        String origTotalFee = (String) msg.get("origTotalFee");
        if ("1".equals(payInfos.get("payMode"))) {
            msg.put("sendTypeCode", "1");
        }

        msg.put("provOrderId", msg.get("provOrderId"));
        msg.put("orderNo", msg.get("orderNo"));
        msg.put("origTotalFee", Integer.valueOf(origTotalFee));

        // subOrderSubReq
        List<Map> subOrderSubReq = new ArrayList<Map>();
        Map subOrderSub = new HashMap();
        subOrderSub.put("subProvinceOrderId", msg.get("provOrderId"));
        subOrderSub.put("subOrderId", msg.get("orderNo"));

        // feeInfo
        List<Map> feeInfos = (List<Map>) msg.get("feeInfo");
        List<Map> feeInfoList = new ArrayList<Map>();
        if (feeInfos != null && 0 != feeInfos.size()) {
            for (Map feeInfo : feeInfos) {
                Map retFee = new HashMap();
                retFee.put("feeCategory", feeInfo.get("feeCategory").toString());
                retFee.put("feeId", feeInfo.get("feeId").toString());
                retFee.put("feeDes", feeInfo.get("feeDes").toString());
                retFee.put("operateType", "1");
                retFee.put("payTag", "1");
                retFee.put("calculateId", GetSeqUtil.getSeqFromCb());
                retFee.put("payId", GetSeqUtil.getSeqFromCb());
                retFee.put("calculateDate", GetDateUtils.getDate());
                retFee.put("payTag", "1");
                retFee.put("origFee", feeInfo.get("origFee").toString());
                retFee.put("reliefFee", feeInfo.get("reliefFee").toString());
                retFee.put("realFee", feeInfo.get("realFee").toString());
                retFee.put("calculateTag", feeInfo.get("calculateTag").toString());
                feeInfoList.add(retFee);

            }
        }
        subOrderSub.put("feeInfo", feeInfoList);
        subOrderSubReq.add(subOrderSub);
        msg.put("subOrderSubReq", subOrderSubReq);

        // payInfo 取外围传的
        Map payInfo = (Map) msg.get("payInfo");
        List<Map> payInfo1 = new ArrayList<Map>();
        payInfo.put("subProvinceOrderId", msg.get("provOrderId"));
        payInfo.put("payMode", payInfo.get("payMode"));
        payInfo.put("payType", payInfo.get("payType"));
        payInfo1.add(payInfo);
        msg.put("payInfo", payInfo1);

        return msg;
    }
}
