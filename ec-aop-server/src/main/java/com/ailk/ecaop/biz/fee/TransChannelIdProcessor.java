package com.ailk.ecaop.biz.fee;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.alibaba.fastjson.JSON;

/**
 * 渠道编码转换,非15004的下发15399,目前仅赠款接口使用
 * @author wangmc 2016-12-21
 */
@EcRocTag("transChannelIdProcessor")
public class TransChannelIdProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = new HashMap();
        if (body.get("msg") instanceof String) {
            msg = JSON.parseObject(body.get("msg") + "");
        }
        else {
            msg = (Map) body.get("msg");
        }
        Object channelId = msg.get("channelId");
        channelId = "15004".equals(channelId) ? channelId : "15399";
        msg.put("channelId", channelId);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
    }

}
