package com.ailk.ecaop.biz.sub;/**
 * Created by Liu JiaDi on 2016/5/23.
 */

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单激活接扣就是为了返回一个请求里面的订单号，就得写个方法，真坑的规范
 *
 * @auther Liu JiaDi
 * @create 2016_05_23_10:26
 */
@EcRocTag("isCbssActivateSo")
public class IsCbssActivateSo extends BaseAopProcessor {
    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String orderId = (String) msg.get("orderId");
        LanUtils lan = new LanUtils();
        lan.preData("ecaop.trades.mcoa.ParametersMapping", exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.cbss.services.cbssOrderCleSer");
        lan.xml2Json("ecaop.trades.mcoa.template", exchange);
        // 受理成功时，返回总部和省份订单
        Map outMap = new HashMap();
        outMap.put("orderId", orderId);
        exchange.getOut().setBody(outMap);
    }
}
