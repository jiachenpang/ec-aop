package com.ailk.ecaop.biz.number;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.alibaba.fastjson.JSON;

/**
 * 统一号码费用度量衡
 * 北六侧目前返回的费用为分，调整为厘
 * 
 * @author Steven
 */
@EcRocTag("commSerialNumerDLH")
public class CommSerialDLHProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        if (!"200".equals(String.valueOf(exchange.getProperty("HTTP_STATUSCODE")))) {
            return;
        }
        Object outRet = exchange.getOut().getBody();
        String appCode = exchange.getAppCode();
        Map out = new HashMap();
        if (outRet instanceof String) {
            out = JSON.parseObject(outRet.toString());
        }
        else {
            out = (Map) outRet;
        }
        List<Map> numInfoList = (List<Map>) out.get("numInfo");
        if (null == numInfoList || 0 == numInfoList.size()) {
            return;
        }
        for (Map num : numInfoList) {
            if ("mnsb|hapr".contains(appCode)) {
                num.put("advancePay", Integer.valueOf(num.get("advancePay").toString()) * 10 + "");
                num.put("lowCostPro", Integer.valueOf(num.get("lowCostPro").toString()) * 10 + "");
            }
        }
        out.put("numInfo", numInfoList);
        exchange.getOut().setBody(out);
    }
}
