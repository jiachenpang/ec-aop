package com.ailk.ecaop.biz.fee;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.LanUtils;

@EcRocTag("actfeeRevProcessor")
public class ActfeeRevProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");
        Map payInfo = (Map) msg.get("payInfo");
        // 转换支付方式
        String payFeeMode = (String) ChangeCodeUtils.changePayType4CB(payInfo.get("payFeeMode"));
        payInfo.put("payFeeMode", payFeeMode);
        // para里面也放入支付方式,cbss要求
        List<Map> para = new ArrayList<Map>();
        Map item = new HashMap();
        item.put("paraId", "PAY_FEE_MODE");
        item.put("paraValue", payFeeMode);
        para.add(item);
        msg.put("PARA", para);
        System.out.println("actfeeMsg" + msg);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trades.hdjf.submitfee.paramtersmapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.PayCleSer");
        lan.xml2Json("ecaop.trades.hdjf.submitfee.template", exchange);
    }
}
