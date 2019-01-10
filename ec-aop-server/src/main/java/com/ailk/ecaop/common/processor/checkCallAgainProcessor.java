package com.ailk.ecaop.common.processor;

import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.alibaba.fastjson.JSONObject;

/*
 * 号卡中心选号检查是不是回调了原流程
 */
@EcRocTag("checkCalllAgain")
public class checkCallAgainProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = (Map) exchange.getIn().getBody();
        Object msgObject = body.get("msg");
        Map map = null;
        if (msgObject instanceof String) {
            map = JSONObject.parseObject(msgObject.toString());
        }
        else {
            map = (Map) msgObject;
        }
        if (map != null) {
            // Map para = (((List<Map>) map.get("para")).get(0));
            List<Map> paraList = (List<Map>) map.get("para");
            // Object serType = map.get("serType");
            //
            // if ("2".equals(serType)) {// 是否预付费 预付费
            // exchange.setProperty("checkSerType", "0"); // 预付费不走号卡中心
            // }
            // else {
            // exchange.setProperty("checkSerType", "1");
            // }
            if (paraList != null && paraList.size() > 0) {
                Map para = paraList.get(0);

                if (para != null && "Yes".equals(para.get("paraValue"))) {
                    exchange.setProperty("callAgain", "0");
                }
                else {
                    exchange.setProperty("callAgain", "1");
                }
            }
            else {
                exchange.setProperty("callAgain", "1");
            }
        }
    }
}
