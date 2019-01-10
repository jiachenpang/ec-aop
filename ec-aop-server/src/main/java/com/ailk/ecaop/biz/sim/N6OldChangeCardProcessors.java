package com.ailk.ecaop.biz.sim;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.OldChangeCardUtils;
import com.google.common.collect.Maps;

/**
 * 北六3G异步补换卡接口
 */
@EcRocTag("n6ChangeCardProcessor")
public class N6OldChangeCardProcessors extends BaseAopProcessor implements ParamsAppliable {

    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[4];
    // 三户接口,调用北六预提交,调用北六正式提交
    private static final String[] PARAM_ARRAY = { "ecaop.trade.n6.checkUserParametersMapping",
            "ecaop.masb.sbac.N6.sglUniTradeParametersMapping", "ecaop.masb.odsb.N6.ActivityAryParametersMapping" };

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        if (!"11|17|18|76|91|97".contains(msg.get("province").toString())) {
            throw new EcAopServerBizException("9999", "路由分发失败");
        }
        // 调用三户接口
        String[] copyArray = { "operatorId", "province", "channelId", "city", "district", "channelType" };
        Map threePartMap = MapUtils.asMap("getMode", "00000", "serialNumber", msg.get("serialNumber"), "tradeTypeCode",
                "0141");
        MapUtils.arrayPut(threePartMap, msg, copyArray);
        Exchange threePartExchange = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[0], threePartExchange);
        CallEngine.wsCall(threePartExchange, "ecaop.comm.conf.url.UsrForNorthSer" + "." + msg.get("province"));
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchange);
        Map threePartRetMap = threePartExchange.getOut().getBody(Map.class);

        // 调用预提交接口
        Map preSubmitMap = Maps.newHashMap();
        MapUtils.arrayPut(preSubmitMap, msg, copyArray);
        String tradeId = GetSeqUtil.getSeqFromCb(ExchangeUtils.ofCopy(exchange, preSubmitMap), "seq_trade_id");
        preSubmitMap.put("ordersId", tradeId);
        preSubmitMap.put("operTypeCode", "0");
        preSubmitMap.put("serinalNamber", msg.get("serialNumber"));
        // FIXME CHANGE_1 放入北六异步补换卡的标识 by wangmc 20180724
        msg.put("isN6ChangeCard", "1");
        new OldChangeCardUtils().preSubmitMessage(preSubmitMap, threePartRetMap, msg, exchange.getAppCode());
        Exchange preSubmitExchange = ExchangeUtils.ofCopy(exchange, preSubmitMap);
        lan.preData(pmp[1], preSubmitExchange);
        CallEngine.wsCall(preSubmitExchange,
                "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
        lan.xml2Json("ecaop.trades.sccc.cancelPre.template", preSubmitExchange);

        // 调用正式提交接口
        Map preSubmitRetMap = preSubmitExchange.getOut().getBody(Map.class);
        Map submitMap = Maps.newHashMap();
        MapUtils.arrayPut(submitMap, msg, copyArray);
        msg.put("changePayType", "0");
        new OldChangeCardUtils().preOrderSubMessage(submitMap, preSubmitRetMap, msg);
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        lan.preData(pmp[2], submitExchange);
        // CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.OrderSub" + "." + msg.get("province"));
        // 正式提交也调用预提交使用的地址
        CallEngine.wsCall(submitExchange,
                "ecaop.comm.conf.url.n6ess.services.AOPOrdForNorthSer" + "." + msg.get("province"));
        lan.xml2Json("ecaop.trades.sccc.cancel.template", submitExchange);
        Map submitRetMap = (Map) submitExchange.getOut().getBody();
        Message out = new DefaultMessage();
        out.setBody(MapUtils.asMap("essSubscribeId", tradeId, "para", submitRetMap.get("para")));
        exchange.setOut(out);
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
