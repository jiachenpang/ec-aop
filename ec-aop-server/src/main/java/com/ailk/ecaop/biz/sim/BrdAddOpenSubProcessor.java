package com.ailk.ecaop.biz.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
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

/**
 * 宽带加装提交
 * 
 * @auther Zhao Zhengchang
 * @create 2016_03_21
 */
@EcRocTag("brdAddOpenSubpro")
public class BrdAddOpenSubProcessor extends BaseAopProcessor implements ParamsAppliable {

    // 代码优化
    private static final String[] PARAM_ARRAY = { "ecaop.trades.sbaos.cancel.paramtersmapping",
            "ecaop.trades.sbaos.cancel.N6.paramtersmapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];

    @Override
    public void process(Exchange exchange) throws Exception {
        // 参数处理
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Object provOrderId = msg.get("provOrderId");
        dealInParams(exchange, provOrderId);
        String province = (String) msg.get("province");
        LanUtils lan = new LanUtils();
        String orderNo = (String) msg.get("orderNo");
        if (StringUtils.isEmpty(orderNo)) {
            msg.put("orderNo", provOrderId);
        }
        if ("2".equals(msg.get("opeSysType"))) { // 办理业务系统： 1：ESS 2：CBSS
            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        }
        else {
            if ("11|17|18|76|91|97".contains(msg.get("province").toString())) {
                lan.preData(pmp[1], exchange);
                CallEngine.wsCall(exchange,
                        "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
            }
            else {
                lan.preData(pmp[0], exchange);
                CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
            }
        }
        lan.xml2Json("ecaop.trades.sbaos.cancel.template", exchange);
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
        msg.put("sendTypeCode", "0");
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
        msg.put("payInfo", payInfo);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
