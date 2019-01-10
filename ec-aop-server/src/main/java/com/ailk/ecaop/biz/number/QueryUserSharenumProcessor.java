package com.ailk.ecaop.biz.number;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("queryUserSharenumProcessor")
public class QueryUserSharenumProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData("method.tzcx.params.mapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.WoOrderSer");
        lan.xml2Json("ecaop.query.tzcx.template", exchange);

        Map body = (Map) exchange.getOut().getBody();
        Map guaranteeInfo = new HashMap();
        List para = (List) body.get("para");
        body.remove("para");
        List guaranteeInfos = new ArrayList();
        guaranteeInfo.putAll(body);
        guaranteeInfos.add(guaranteeInfo);

        Map m = new HashMap();
        m.put("guaranteeInfo", guaranteeInfos);
        m.put("para", para);
        exchange.getOut().setBody(m);
    }
}
