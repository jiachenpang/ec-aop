package com.ailk.ecaop.biz.sub.lan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

@EcRocTag("getNeededKeyandValue")
public class GetNeededKeyandValueProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map dataMap = exchange.getOut().getBody(Map.class);
        if (dataMap.containsKey("code")) {
            return;
        }
        Map responseMap = (Map) dataMap.get("RESPONSE");
        Map orderInfoMap = (Map) ((ArrayList) responseMap.get("ORDER_INFO")).get(0);

        Map returnmapMap = new HashMap();
        String statuCodeString = (String) orderInfoMap.get("statusCode");
        returnmapMap.put("statusCode", statuCodeString);
        returnmapMap.put("feeInfo", orderInfoMap.get("feeInfo"));
        // 商城必须节点但是全业务没有返回
        if (StringUtils.isEmpty((String) orderInfoMap.get("orderStatusTime"))) {
            returnmapMap.clear();
            returnmapMap.put("code", "9999");
            returnmapMap.put("detail", "全业务没有返回商城所必须节点:" + "orderStatusTime");
            exchange.getOut().setBody(returnmapMap);
            return;
        }
        returnmapMap.put("orderStatusTime", orderInfoMap.get("orderStatusTime"));

        // 非必须节点
        if (!StringUtils.isEmpty((String) responseMap.get("totalFee"))) {
            returnmapMap.put("totalFee", responseMap.get("totalFee"));
        }
        if (!StringUtils.isEmpty((String) responseMap.get("cancleTotalFee"))) {
            returnmapMap.put("cancleTotalFee", responseMap.get("cancleTotalFee"));
        }
        if ("09".equals(statuCodeString) && StringUtils.isEmpty((String) orderInfoMap.get("backOrderReason"))) {
            returnmapMap.clear();
            returnmapMap.put("code", "9999");
            returnmapMap.put("detail", "订单状态为退单，全业务没有返回商城所必须节点:" + "backOrderReason");
            exchange.getOut().setBody(returnmapMap);
            return;
        }
        returnmapMap.put("backOrderReason", orderInfoMap.get("backOrderReason"));

        if ("05".equals(statuCodeString) && StringUtils.isEmpty((String) orderInfoMap.get("cleOrderReason"))) {
            returnmapMap.clear();
            returnmapMap.put("code", "9999");
            returnmapMap.put("detail", "订单状态为撤单，全业务没有返回商城所必须节点:" + "cleOrderReason");
            exchange.getOut().setBody(returnmapMap);
            return;

        }
        returnmapMap.put("cleOrderReason", orderInfoMap.get("cleOrderReason"));
        if (((ArrayList) responseMap.get("PARA")).size() != 0) {
            returnmapMap.put("para", responseMap.get("PARA"));
        }

        exchange.getOut().setBody(returnmapMap);
        // System.out.println(returnmapMap);
    }

}
