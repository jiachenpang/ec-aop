package com.ailk.ecaop.biz.sub;

import java.util.HashMap;
import java.util.Map;
import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;
/**
 * 订单回滚
 *
 * @auther Xu ZhiWei
 * @create 2016_05_10_14:17
 */
@EcRocTag("cancelSub")
public class CancelSubProcessors extends BaseAopProcessor {

	@Override
	public void process(Exchange exchange) throws Exception {
		Map msg = dealInParams(exchange);
		String provOrderId = (String) msg.get("provOrderId");
		String essOrderId = (String) msg.get("ordersId");
		LanUtils lan = new LanUtils();
		lan.preData("ecaop.trades.sccc.cancel.paramtersmapping", exchange);
		CallEngine.wsCall(exchange,"ecaop.comm.conf.url.cbss.services.orderSub");
		lan.xml2Json("ecaop.trades.sccc.cancel.template", exchange);
		// 受理成功时，返回总部和省份订单		
		Map retMap = new HashMap();
        retMap.put("provOrderId", provOrderId);
        retMap.put("essOrderId", essOrderId);
        exchange.getOut().setBody(retMap);

	}

	/* 处理请求参数 */
	private Map dealInParams(Exchange exchange) {
		Map body = (Map) exchange.getIn().getBody();
		Map msg = (Map) body.get("msg");
		msg.put("provOrderId", msg.get("provOrderId"));
		msg.put("orderNo", msg.get("ordersId"));
		msg.put("operationType", "02");
		msg.put("noteType", "1");
		msg.put("origTotalFee", "0");
		msg.put("cancleTotalFee", "0");
		return msg;
	}
}