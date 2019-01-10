package com.ailk.ecaop.biz.user;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

/**
 * @author maxy3
 * @version 创建时间：2017-8-23 下午4:49:12
 *          组合业务资料查询
 */
@EcRocTag("qryMultUserInfo")

public class QryMultUserInfoProcessor extends BaseAopProcessor implements ParamsAppliable {

	@Override
	public void process(Exchange exchange) throws Exception {
		Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        
        msg.put("groupUserInfo", msg);
        body.put("msg", msg);
        exchange.getIn().setBody(body);
		
	}

	@Override
	public void applyParams(String[] params) {
		// TODO Auto-generated method stub
		
	}
	

}
