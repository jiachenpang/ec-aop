package com.ailk.ecaop.biz.sub;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;

/**
 * 用于BSS成卡补换卡提交
 * 
 * @auther maly
 * @create 2017_12_18_11:28
 */
@EcRocTag("N6ChangedCardSub")
public class N6ChangedCardSubProcessors extends BaseAopProcessor {

    private final Logger log = LoggerFactory
            .getLogger(N6ChangedCardSubProcessors.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        msg.putAll(preOrderSubMessage(exchange));
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trades.tscs.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.OrderSub." + msg.get("province"));
        lan.xml2Json("ecaop.trades.mpsb.template", exchange);
        Map ordersId = new HashMap();
        ordersId.put("bssOrderId", msg.get("ordersId"));
        exchange.getOut().setBody(ordersId);
        log.debug(exchange.getOut().getBody().toString());
    }

    private Map preOrderSubMessage(Exchange exchange) {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        List<Map> feeInfolist = (List<Map>) msg.get("feeInfo");
        Map subOrderSubReq = new HashMap();
        List<Map> subOrderSubReqlist = new ArrayList<Map>();
        List<Map> fees = new ArrayList<Map>();
        if (null != feeInfolist && feeInfolist.size() > 0) {
            for (Map feeInfo : feeInfolist) {
                String calculateId = GetSeqUtil.getSeqFromCb();
                feeInfo.put("feeId", feeInfo.get("feeId"));
                feeInfo.put("feeCategory", feeInfo.get("feeMode"));
                feeInfo.put("feeDes", feeInfo.get("feeTypeName"));
                feeInfo.put("origFee", Integer.valueOf((String) feeInfo.get("fee")));
                feeInfo.put("realFee", Integer.valueOf((String) feeInfo.get("oldFee")));
                feeInfo.put("operateType", "1");
                feeInfo.put("calculateTag", "N");
                feeInfo.put("isPay", "1");
                feeInfo.put("payTag", "1");
                feeInfo.put("calculateId", calculateId);
                feeInfo.put("calculateDate", GetDateUtils.getDate());
                feeInfo.put("payId", calculateId);
                fees.add(feeInfo);
            }
            subOrderSubReq.put("feeInfo", fees);
        }
        List<Map> payInfoList = new ArrayList<Map>();
        Map payInfo = (Map) msg.get("payInfo");
        payInfo.put("payType", ChangeCodeUtils.changePayTypeForN6ESS(payInfo.get("payType")));
        payInfo.put("payMoney", msg.get("origTotalFee"));
        payInfo.put("subProvinceOrderId", msg.get("ordersId"));
        payInfoList.add(payInfo);
        msg.put("payInfo", payInfoList);
        //        ((Map) msg.get("payInfo")).put("payType", "0");
        //        ((Map) msg.get("payInfo")).put("subProvinceOrderId", msg.get("ordersId"));

        subOrderSubReq.put("subProvinceOrderId", msg.get("ordersId"));
        subOrderSubReq.put("subProvinceOrderId", msg.get("ordersId"));
        subOrderSubReqlist.add(subOrderSubReq);
        msg.put("subOrderSubReq", subOrderSubReqlist);
        msg.put("provOrderId", msg.get("ordersId"));
        msg.put("orderNo", msg.get("ordersId"));
        msg.put("operationType", "01");
        msg.put("sendTypeCode", "0");
        msg.put("noteNo", "11111111111111");
        msg.put("noteType", "1");
        msg.put("noteFlag", "1");
        msg.put("origTotalFee", msg.get("origTotalFee"));
        msg.put("cancleTotalFee", "0");
        return msg;

    }
}
