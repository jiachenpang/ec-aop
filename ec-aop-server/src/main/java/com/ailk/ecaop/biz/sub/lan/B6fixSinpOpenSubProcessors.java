package com.ailk.ecaop.biz.sub.lan;

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

@EcRocTag("B6fixSinpOpenSubProcessors")
public class B6fixSinpOpenSubProcessors extends BaseAopProcessor {

    private Map<String, Object> body;
    private Map map;

    @Override
    public void process(Exchange exchange) throws Exception {
        body = exchange.getIn().getBody(Map.class);
        map = (Map) body.get("msg");
        Object provOrderId = map.get("provOrderId");
        // 参数处理
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        dealInParams(exchange, provOrderId);
        String orderNo = (String) msg.get("orderNo");
        if (StringUtils.isEmpty(orderNo)) {
            msg.put("orderNo", provOrderId);
        }
        // 调全业务
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.masb.odsb.N6.ActivityAryParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
        lan.xml2Json("ecaop.masb.odsb.template", exchange);
        // 返回处理
        Map retMap = exchange.getOut().getBody(Map.class);
        retMap.put("provOrderId", provOrderId);
        exchange.getOut().setBody(retMap);

    }

    /*
     * 正式提交请求参数准备
     */
    private void dealInParams(Exchange exchange, Object provOrderId) throws Exception {
        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");
        List<Map> feeInfos = (List<Map>) msg.get("feeInfo");
        String origTotalFee = (String) msg.get("origTotalFee");
        // 准备参数
        msg.put("operationType", "01");
        msg.put("noteNo", "11111111111111");
        msg.put("noteType", "1");
        msg.put("noteFlag", "1");
        // subOrderSubReq参数-->流水+feeInfosubOrderSub
        List<Map> subOrderSubReq = new ArrayList<Map>();
        List<Map> fees = new ArrayList<Map>();
        Map subOrderSub = new HashMap();
        subOrderSub.put("subOrderId", msg.get("orderNo"));
        subOrderSub.put("subProvinceOrderId", provOrderId);

        // feeInfo信息
        if (feeInfos != null) {
            for (Map feeInfo1 : feeInfos) {
                Map feeInfo = new HashMap();
                String feeId = (String) feeInfo1.get("feeId");
                String feeDes = (String) feeInfo1.get("feeDes");
                String origFee = (String) feeInfo1.get("origFee");
                String reliefFee = (String) feeInfo1.get("reliefFee");
                String realFee = (String) feeInfo1.get("realFee");

                feeInfo.put("feeId", feeId);
                feeInfo.put("feeCategory", feeInfo1.get("feeCategory"));
                feeInfo.put("calculateTag", feeInfo1.get("calculateTag"));
                feeInfo.put("feeDes", feeDes);
                feeInfo.put("origFee", origFee);
                feeInfo.put("reliefFee", reliefFee);
                feeInfo.put("reliefResult", feeInfo1.get("reliefResult"));
                feeInfo.put("realFee", realFee);
                feeInfo.put("operateType", "1");
                feeInfo.put("payTag", "1");
                feeInfo.put("calculateId", GetSeqUtil.getSeqFromCb());
                String acceptDate = GetDateUtils.getDate();
                feeInfo.put("calculateDate", GetDateUtils.transDate(acceptDate, 14));
                feeInfo.put("calculateStaffId", "");
                // feeInfo.put("payId", "1915121948114082");
                fees.add(feeInfo);
            }
        }
        subOrderSub.put("feeInfo", fees);
        subOrderSubReq.add(subOrderSub);
        msg.put("subOrderSubReq", subOrderSubReq);
        // 另外两个
        msg.put("origTotalFee", origTotalFee);
        msg.put("cancleTotalFee", origTotalFee);

        // payInfo信息
        Map payInfo = (Map) msg.get("payInfo");
        payInfo.put("subProvinceOrderId", provOrderId);
        payInfo.put("payType", ChangeCodeUtils.changePayType(payInfo.get("payType")));
        payInfo.put("payMoney", origTotalFee);
        payInfo.put("remark", "");
        if ("91".equals(msg.get("province") + "")) {
            msg.put("sendTypeCode", payInfo.get("payMode"));// 辽宁提出，此值由省份传入，之前是写死下发0 ,zzc
        }
        else {
            msg.put("sendTypeCode", "0");
        }
        msg.put("payInfo", payInfo);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }
}
