package com.ailk.ecaop.common.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.google.common.collect.Maps;

@EcRocTag("ChangeFeeType")
public final class ChangeFeeTypeUtils extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        List<Map> feeInfo = (List<Map>) msg.get("feeInfo");
        if (null == feeInfo || 0 == feeInfo.size()) {
            return;
        }
        int realFee = 0;
        int origFee = 0;
        int reliefFee = 0;
        List<Map> feeInfos = new ArrayList<Map>();
        Map<String, String> advanceFeeMap = Maps.newHashMap();
        for (Map fee : feeInfo) {
            if ("4".equals(fee.get("feeCategory"))) {
                fee.put("feeCategory", "2");
            }
            if ("100000".equals(fee.get("feeId")) || "99".equals(fee.get("feeId"))) // 在提交的时候预写的预存
            {
                realFee += Integer.valueOf(fee.get("realFee").toString());
                reliefFee += Integer.valueOf(fee.get("realFee").toString());
                origFee += Integer.valueOf(fee.get("origFee").toString());
                advanceFeeMap.put("reliefResult", (String) fee.get("reliefResult"));
                advanceFeeMap.put("feeCategory", (String) fee.get("feeCategory"));
                advanceFeeMap.put("feeDes", (String) fee.get("feeDes"));
            }
            else {
                feeInfos.add(fee);
            }
        }
        if ((realFee + origFee + reliefFee) != 0) {
            advanceFeeMap.put("origFee", String.valueOf(origFee));
            advanceFeeMap.put("reliefFee", String.valueOf(reliefFee));
            advanceFeeMap.put("realFee", String.valueOf(realFee));
            advanceFeeMap.put("feeId", "100000");
            feeInfos.add(advanceFeeMap);
        }
        msg.put("feeInfo", feeInfos);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }
}
