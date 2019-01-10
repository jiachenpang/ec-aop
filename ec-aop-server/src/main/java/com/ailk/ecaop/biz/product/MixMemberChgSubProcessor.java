package com.ailk.ecaop.biz.product;

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

/**
 * 融合变更提交接口
 * @author wangmc
 * @date 2018-10-24
 */
@EcRocTag("mixMemberChgSubProcessor")
public class MixMemberChgSubProcessor extends BaseAopProcessor implements ParamsAppliable {

    LanUtils lan = new LanUtils();
    //
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];
    private static final String[] PARAM_ARRAY = { "ecaop.trades.sccc.cancel.paramtersmapping" };

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        // 虚拟成员预提交订单号
        Object provOrderId = msg.get("bssOrderId");
        msg.put("provOrderId", provOrderId);
        LanUtils lan = new LanUtils();
        msg.put("orderNo", provOrderId);
        msg.put("noteNo", msg.get("invoiceNo"));
        msg.put("sendTypeCode", "0");
        List<Map> subOrderInfo = (List<Map>) msg.get("subOrderInfo");
        List<Map> subOrderSubReq = new ArrayList<Map>();
        String[] copyStr = { "feeCategory", "feeId", "feeDes", "origFee", "reliefFee", "reliefResult", "realFee" };
        for (Map subOrder : subOrderInfo) {
            Map tempMap = new HashMap();
            // 子成员订单号
            tempMap.put("subProvinceOrderId", subOrder.get("provOrderId"));
            tempMap.put("subOrderId", subOrder.get("provOrderId"));
            List<Map> fee = (List<Map>) subOrder.get("feeInfo");
            int payMoney = 0;
            if (null != fee && 0 != fee.size()) {
                List<Map> fList = new ArrayList<Map>();
                for (Map f : fee) {
                    if (f.isEmpty()) {
                        continue;
                    }
                    if ("99".equals(f.get("feeId"))) {
                        f.put("feeId", "100000");
                    }
                    if ("4".equals(f.get("feeCategory"))) {
                        f.put("feeCategory", "2");
                    }
                    payMoney += Integer.parseInt(f.get("realFee").toString());
                    fList.add(dealFee(f, copyStr));
                }
                if (0 != fList.size()) {
                    tempMap.put("feeInfo", fList);
                }
            }
            tempMap.put("payMoney", payMoney);
            subOrderSubReq.add(tempMap);
        }
        Map payInfoMap = (Map) msg.get("payInfo");
        Object payType = ChangeCodeUtils.changePayType(payInfoMap.get("payType"));
        List<Map> payInfo = new ArrayList<Map>();
        for (Map sub : subOrderSubReq) {
            Map patInfoTemp = new HashMap();
            patInfoTemp.put("payMoney", sub.get("payMoney"));
            patInfoTemp.put("subProvinceOrderId", sub.get("subOrderId"));
            patInfoTemp.put("payType", payType);
            payInfo.add(patInfoTemp);
        }

        msg.put("payInfo", payInfo);
        msg.put("subOrderSubReq", subOrderSubReq);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
        lan.preData(pmp[0], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.sccc.cancel.template", exchange);
        Map retMap = exchange.getOut().getBody(Map.class);
        retMap.put("provOrderId", provOrderId);
    }

    /**
     * 处理费用
     * @param fee
     * @param copyStr
     * @return
     */
    private Map dealFee(Map fee, String[] copyStr) {
        Map temp = new HashMap();
        MapUtils.arrayPut(temp, fee, copyStr);
        temp.put("calculateTag", "N");
        temp.put("payTag", "1");
        temp.put("calculateId", GetSeqUtil.getSeqFromCb());
        temp.put("calculateDate", GetDateUtils.getDate());
        return temp;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
