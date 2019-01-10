package com.ailk.ecaop.biz.brdband;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("brdbandCheck")
public class BrdBandCheckProcessor extends BaseAopProcessor {

    LanUtils lan = new LanUtils();

    @Override
    public void process(Exchange exchange) throws Exception {
        // TODO Auto-generated method stub

        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        lan.preData("ecaop.trade.bss.checkCodePasswdParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.Brdband");
        lan.xml2JsonNoError("ecaop.trade.cbss.checkCodePasswdTemplate", exchange);
        Map out = exchange.getOut().getBody(Map.class);
        String code = (String) out.get("code");
        if (!"0000".equals(code)) {
            throw new EcAopServerBizException(code, (String) out.get("detail"));
        }
        Exchange exchange0 = ExchangeUtils.ofCopy(exchange, msg);
        lan.preData("ecaop.trade.bss.checkCodePasswdParametersMapping", exchange0);
        CallEngine.wsCall(exchange0, "ecaop.comm.conf.url.services.Number4GSer");
        lan.xml2JsonNoError("ecaop.trade.cbss.checkCodePasswdTemplate", exchange0);
        Map out1 = exchange0.getOut().getBody(Map.class);
        String code0 = (String) out1.get("code");
        if (!"0000".equals(code0)) {
            throw new EcAopServerBizException(code0, (String) out1.get("detail"));
        }
        Message message = new DefaultMessage();
        message = exchange0.getOut();
        message.setBody(out1);
        exchange.setOut(message);

    }

}
