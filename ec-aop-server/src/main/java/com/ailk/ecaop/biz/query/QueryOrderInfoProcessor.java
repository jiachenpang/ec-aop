package com.ailk.ecaop.biz.query;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("QueryOrderInfo")
@SuppressWarnings({ "unchecked", "rawtypes" })
public class QueryOrderInfoProcessor extends BaseAopProcessor implements ParamsAppliable {

    private static final String[] PARAM_ARRAY = {"ecaop.qoiq.preSub.ParametersMapping"};
    LanUtils lan = new LanUtils();
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[1];

    @Override
    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], exchange);//ecaop.qoiq.preSub.ParametersMapping
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.qryTrade");
        lan.xml2Json("ecaop.cbss.qoiq.template", exchange);

        Map rspMap = exchange.getOut().getBody(Map.class);

        if (!IsEmptyUtils.isEmpty(rspMap)) {
            List<Map> tradeList = (List<Map>) rspMap.get("trade");
            if (null != tradeList && 0 != tradeList.size()) {
                Map realOut = tradeList.get(0);

                List<Map> feeInfos = (List<Map>) realOut.get("tradefeeSub");
                int oldFee = 0;
                if (null != feeInfos && 0 != feeInfos.size()) {
                    for (Map fee : feeInfos) {
                        oldFee = oldFee + Integer.valueOf((String) fee.get("oldFee"));
                    }
                }
                Map trade = new HashMap();
                // 新增TRADEFEE_SUB节点返回
                if (!IsEmptyUtils.isEmpty(feeInfos)) {
                    for (Map fee : feeInfos) {
                        fee.remove("months");// 因下游规范此字段备注删除，故不返回给外围
                    }
                    trade.put("tradefeeSub", feeInfos);
                }
                // 新增TRADEFEE_SUB节点返回
                trade.put("orderId", realOut.get("orderId"));
                trade.put("subOrderId", realOut.get("subOrderId"));
                trade.put("subScribeId", realOut.get("subScribeId"));
                trade.put("tradeId", realOut.get("tradeId"));
                trade.put("subscribeState", realOut.get("subscribeState"));
                trade.put("nextDealTag", realOut.get("nextDealTag"));
                trade.put("inModeCode", realOut.get("inModeCode"));
                trade.put("tradeTypeCode", realOut.get("tradeTypeCode"));
                trade.put("productId", realOut.get("productId"));
                trade.put("brandCode", realOut.get("brandCode"));
                trade.put("userId", realOut.get("userId"));
                trade.put("custId", realOut.get("custId"));
                trade.put("usecustId", realOut.get("usecustId"));
                trade.put("acctId", realOut.get("acctId"));
                trade.put("netTypeCode", realOut.get("netTypeCode"));
                trade.put("serialNumber", realOut.get("serialNumber"));
                trade.put("custName", realOut.get("custName"));
                trade.put("eparchyCode", realOut.get("eparchyCode"));
                trade.put("cityCode", realOut.get("cityCode"));
                trade.put("finishdate", realOut.get("finishdate"));
                trade.put("operFee", String.valueOf(oldFee));

                exchange.getOut().setBody(trade);
            }
            else {
                exchange.getOut().setBody(rspMap);
            }

        }
        else {
            throw new EcAopServerBizException("9999", "返回应答信息为空");
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[]{PARAM_ARRAY[i]});
        }
    }

}
