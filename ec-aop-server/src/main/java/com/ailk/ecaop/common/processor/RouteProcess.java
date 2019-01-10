package com.ailk.ecaop.common.processor;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.alibaba.fastjson.JSONObject;

@EcRocTag("route")
public class RouteProcess extends BaseAopProcessor {

    @SuppressWarnings("serial")
    static Map n6province = new HashMap() {

        {
            put("11", "11");
            put("17", "17");
            put("18", "18");
            put("76", "76");
            put("91", "91");
            put("97", "97");
        }
    };

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
        String province = (String) map.get("province");
        if ("qcsq|nboc|snqy|qcsc".contains(exchange.getMethodCode())) {// 涉及号码查询预占时,不考虑受理系统
            map.remove("opeSysType");
        }
        if ("2".equals(map.get("opeSysType"))) {
            exchange.setProperty("opeSysType", "2");
        }
        else {
            exchange.setProperty("opeSysType", "1");
        }

        // 一号多卡主附卡开户，opeSysType值为0,1,2
        System.out.println("20170213method" + exchange.getMethodCode());
        if ("rnoa".equals(exchange.getMethodCode())) {
            if ("0".equals(map.get("opeSysType"))) {
                exchange.setProperty("opeSysType", "0");
            }
        }

        if ("2".equals(map.get("opeSysType")) || n6province.containsKey(province))
        {
            exchange.setProperty("routecode", "N6");
        }
        else {
            exchange.setProperty("routecode", "N25");
        }
    }
}
