package com.ailk.ecaop.biz.sub.same;

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
 * 宽带同装处理提交-固话
 */
@EcRocTag("phoneSameOpenSubCommit")
public class PhoneSameOpenSubCommitProcessors extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = { "ecaop.masb.psos.phoneSameOpenSubCommitMapping",
            "ecaop.masb.psos.N6.phoneSameOpenSubCommitMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[2];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");
        String provOrderId = (String) msg.get("provOrderId");
        String orderNo = (String) msg.get("orderNo");
        if (StringUtils.isEmpty(orderNo)) {
            msg.put("orderNo", provOrderId);
        }
        // 参数处理
        dealInParams(exchange);

        // 调用正式提交接口
        exchange.setMethodCode("psos");
        LanUtils lan = new LanUtils();
        String opeSysType = (String) msg.get("opeSysType");
        String province = (String) msg.get("province");
        if ("2".equals(opeSysType)) { // CBSS
            lan.preData(pmp[0], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        }
        else {
            if ("17|18|11|76|91|97".contains(province)) {
                lan.preData(pmp[1], exchange);
                CallEngine.wsCall(exchange,
                        "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
            }
            else {
                lan.preData(pmp[0], exchange);
                CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.orderSub");
            }
        }
        lan.xml2Json("ecaop.masb.psos.phoneSameOpenSubTemplate", exchange);
        Map retMap = new HashMap();
        retMap.put("provOrderId", provOrderId);
        exchange.getOut().setBody(retMap);

    }

    /* 处理请求参数 */
    private void dealInParams(Exchange exchange) {
        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");

        Map payInfos = (Map) msg.get("payInfo");
        String origTotalFee = (String) msg.get("origTotalFee");

        if ("1".equals(payInfos.get("payMode"))) {
            msg.put("sendTypeCode", "1");
        }

        // subOrderSubReq
        List<Map> subOrderSubReq = new ArrayList<Map>();
        Map subOrderSub = new HashMap();
        subOrderSub.put("subProvinceOrderId", msg.get("provOrderId"));
        subOrderSub.put("subOrderId", msg.get("orderNo"));

        // feeInfo
        List<Map> feeInfos = (List<Map>) msg.get("feeInfo");
        List<Map> feeInfosData = new ArrayList<Map>();
        if (feeInfos != null && 0 != feeInfos.size()) {
            for (Map feeInfo : feeInfos) {
                Map feeInfoData = new HashMap();
                String origFee = (String) feeInfo.get("origFee");
                String realFee = (String) feeInfo.get("realFee");

                feeInfoData.put("feeCategory", feeInfo.get("feeCategory"));
                feeInfoData.put("feeId", feeInfo.get("feeId"));
                feeInfoData.put("feeDes", feeInfo.get("feeDes"));
                feeInfoData.put("operateType", "1");
                feeInfoData.put("origFee", origFee);
                feeInfoData.put("realFee", realFee);
                feeInfoData.put("payTag", "1");
                feeInfoData.put("calculateTag", "N");
                feeInfoData.put("calculateId", GetSeqUtil.getSeqFromCb());
                feeInfoData.put("calculateDate", GetDateUtils.getDate());
                feeInfoData.put("payId", GetSeqUtil.getSeqFromCb()); // 付费ID
                feeInfosData.add(feeInfoData);
            }
        }
        subOrderSub.put("feeInfo", feeInfosData);
        subOrderSubReq.add(subOrderSub);
        msg.put("subOrderSubReq", subOrderSubReq);

        // payInfo 取外围传的
        Map payInfo = (Map) msg.get("payInfo");
        payInfo.put("subProvinceOrderId", msg.get("provOrderId"));
        payInfo.put("payType", ChangeCodeUtils.changePayType(payInfo.get("payType")));
        payInfo.put("payMoney", origTotalFee);
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
