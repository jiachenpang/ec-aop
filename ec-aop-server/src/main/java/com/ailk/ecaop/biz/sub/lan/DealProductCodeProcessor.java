package com.ailk.ecaop.biz.sub.lan;

/**
 * 流程处理：当商城有且只传一个产品code时候我们下发该节点，如果没有传或者是多个code就移除该节点
 */
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.alibaba.fastjson.JSONArray;

@EcRocTag("dealProductCode")
public class DealProductCodeProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map msg = (Map) exchange.getIn().getBody(Map.class).get("msg");
        if (null == msg.get("productCode")) {
            msg.put("productCodes", new JSONArray());
            return;
        }
        JSONArray product = (JSONArray) msg.get("productCode");
        msg.put("productCodes", product);
        if (product.size() != 1) {
            msg.remove("productCode");
        }

    }

}
