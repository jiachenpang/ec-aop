package com.ailk.ecaop.biz.sim;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.OldChangeCardUtils;
import com.google.common.collect.Maps;

@EcRocTag("cbssChangeCardProcessor")
public class CbssOldChangeCardProcessors extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        // 调用三户接口
        String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType", "opeSysType" };
        Map threePartMap = MapUtils.asMap("getMode", "101001101010001001", "serialNumber", msg.get("serialNumber"),
                "tradeTypeCode", "9999");
        MapUtils.arrayPut(threePartMap, msg, copyArray);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trade.cbss.checkUserParametersMapping", threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.cbss.services.usrForNorthSer");
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        Map threePartRetMap = threePartExchange.getOut().getBody(Map.class);

        // 调用预提交接口
        Map preSubmitMap = Maps.newHashMap();
        MapUtils.arrayPut(preSubmitMap, msg, copyArray);
        String tradeId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, preSubmitMap), "seq_trade_id");
        preSubmitMap.put("ordersId", tradeId);
        preSubmitMap.put("operTypeCode", "0");
        preSubmitMap.put("serinalNamber", msg.get("serialNumber"));
        new OldChangeCardUtils().preSubmitMessage(preSubmitMap, threePartRetMap, msg, exchange.getAppCode());
        Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, preSubmitMap);
        lan.preData("ecaop.trades.sccc.cancelPre.paramtersmapping", preSubmitExchange);
        CallEngine.wsCall(preSubmitExchange, "ecaop.comm.conf.url.cbss.services.ordForNorthSer");
        lan.xml2Json("ecaop.trades.sccc.cancelPre.template", preSubmitExchange);

        // 调用正式提交接口
        Map preSubmitRetMap = preSubmitExchange.getOut().getBody(Map.class);
        Map submitMap = Maps.newHashMap();
        MapUtils.arrayPut(submitMap, msg, copyArray);
        new OldChangeCardUtils().preOrderSubMessage(submitMap, preSubmitRetMap, msg);
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        lan.preData("ecaop.trades.sccc.cancel.paramtersmapping", submitExchange);
        CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.cbss.services.orderSub");
        lan.xml2Json("ecaop.trades.sccc.cancel.template", submitExchange);
        Map submitRetMap = (Map) submitExchange.getOut().getBody();
        Message out = new DefaultMessage();
        out.setBody(MapUtils.asMap("essSubscribeId", tradeId, "para", submitRetMap.get("para")));
        exchange.setOut(out);
    }
}
