package com.ailk.ecaop.biz.user;

import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.LanUtils;

/**
 * 缴费结果实时查询
 */
@EcRocTag("feeQueryInfo")
public class FeeQueryInfoProcessors extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");
        String province = (String) msg.get("province");
        msg.put("accessType", "01");
        msg.put("accProvince", province);
        msg.put("accCity", msg.get("city"));
        msg.put("code", ChangeCodeUtils.changeEparchy(msg));
        msg.put("userNumber", msg.get("number"));
        msg.put("activityCode", "T2000708");
//        String netType = (String) msg.get("netType");
//        if ("01,02,06".contains(netType)) { // 移动业务
//            msg.put("activityCode", "T2000708");
//        }
//        else if ("03,04,05".contains(netType)) { // 固网业务
//            msg.put("activityCode", "T2000710");
//        }
//        else {
//            throw new EcAopServerBizException("9999", "netType类型错误");
//        }

        LanUtils lan = new LanUtils();
        lan.preData("ecaop.params.N6qcpa.ParametersMapping", exchange);
        CallEngine.aopCall(exchange, "ecaop.comm.conf.url.cbss.services.PayMentFee"); // 地址待确认
        lan.xml2Json4ONS("ecaop.trades.ons.qcpa.template", exchange);
    }
}
