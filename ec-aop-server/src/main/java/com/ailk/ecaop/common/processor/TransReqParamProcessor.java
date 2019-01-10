package com.ailk.ecaop.common.processor;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("TransReqParam")
public class TransReqParamProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) {
        Map msg = new HashMap();
        Map<String, Object> body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        msg.put("city", ChangeCodeUtils.changeEparchy(msg));
        if (null != msg.get("certType")) {
            msg.put("certType", CertTypeChangeUtils.certTypeMall2Cbss(msg.get("certType").toString()));
        }
        if (null != msg.get("usePsptType")) {
            msg.put("usePsptType", CertTypeChangeUtils.certTypeMall2Cbss(msg.get("usePsptType").toString()));
        }
        body.put("msg", msg);
        exchange.getIn().setBody(body);

    }

}
