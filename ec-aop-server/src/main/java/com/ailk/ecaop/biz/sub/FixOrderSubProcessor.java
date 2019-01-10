package com.ailk.ecaop.biz.sub;

/**
 * Created by Liu JiaDi on 2016/2/22.
 */

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

/**
 * 固话新装 加装提交
 * 
 * @auther Liu JiaDi
 * @create 2016_02_22_14:17
 */
@EcRocTag("fixOrderSub")
public class FixOrderSubProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Object provOrderId = msg.get("provOrderId");
        LanUtils lan = new LanUtils();
        String orderNo = (String) msg.get("orderNo");
        if (StringUtils.isEmpty(orderNo)) {
            msg.put("orderNo", provOrderId);
        }
        msg.put("sendTypeCode", "0");
        String origTotalFee = (String) msg.get("origTotalFee");
        msg.put("origTotalFee", Integer.valueOf(origTotalFee));
        List<Map> subOrderSubReq = new ArrayList<Map>();
        Map subOrderSub = new HashMap();
        subOrderSub.put("subProvinceOrderId", provOrderId);
        subOrderSub.put("subOrderId", msg.get("orderNo"));
        // feeInfo
        List<Map> feeInfos = (List<Map>) msg.get("feeInfo");
        if (feeInfos != null && 0 != feeInfos.size()) {
            for (Map feeInfo : feeInfos) {
                String origFee = (String) feeInfo.get("origFee");
                String realFee = (String) feeInfo.get("realFee");
                feeInfo.put("operateType", "1");
                feeInfo.put("origFee", Integer.valueOf(origFee));
                feeInfo.put("realFee", Integer.valueOf(realFee));
                feeInfo.put("payTag", "1");
                feeInfo.put("calculateTag", "N");
                feeInfo.put("calculateId", GetSeqUtil.getSeqFromCb());
                feeInfo.put("calculateDate", GetDateUtils.getDate());
                // payId有方法可得到
                // feeInfo.put("payId", "9215121944301781"); // 付费ID
            }
        }
        subOrderSub.put("feeInfo", feeInfos);
        subOrderSubReq.add(subOrderSub);
        msg.put("subOrderSubReq", subOrderSubReq);
        Map payInfo = (Map) msg.get("payInfo");
        payInfo.put("subProvinceOrderId", provOrderId);
        // payInfo.put("payType", ChangeCodeUtils.changePayType(payInfo.get("payType")));
        payInfo.put("payMoney", msg.get("origTotalFee"));
        // payInfo.put("subProvinceOrderId", provOrderId);
        msg.put("payInfo", payInfo);
        msg.remove("feeInfo");
        System.out.println("============" + msg);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        if ("11|17|18|76|91|97".contains(msg.get("province").toString())
                && ("1".equals(msg.get("opeSysType")) || msg.get("opeSysType") == null)) {
            payInfo.put("payType", ChangeCodeUtils.changePayType(payInfo.get("payType")));
            lan.preData("ecaop.trades.fasb.N6.ParametersMapping", exchange);
            CallEngine.wsCall(exchange,
                    "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
        }
        else {
            lan.preData("ecaop.trades.fasb.ParametersMapping", exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        }
        lan.xml2Json("ecaop.trades.fasb.template", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        retMap.put("provOrderId", provOrderId);
        exchange.getOut().setBody(retMap);
    }
}
