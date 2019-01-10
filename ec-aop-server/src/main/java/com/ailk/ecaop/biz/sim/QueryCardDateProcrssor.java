package com.ailk.ecaop.biz.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

@EcRocTag("QueryCardDateProcrssor")
public class QueryCardDateProcrssor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        String methodCode = exchange.getMethodCode();
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        Object userType = msg.get("userType");
        if ("0".equals(userType)) {
            userType = "00";
        }
        else if ("1".equals(userType)) {
            userType = "01";
        }
        msg.put("userType", userType);
        LanUtils lan = new LanUtils();
        if ("sooq".equals(methodCode)) {
            lan.preData("ecaop.ota.sooq.paramtersmapping", exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
            lan.xml2Json4qryCardData("ecaop.ota.sooq.template", exchange);
        }
        else if ("cdqy".equals(methodCode)) {
            // 写卡数据查询接口做补换卡业务时,下发卡号给省份 by wangmc 20171115
            if ("1".equals(msg.get("cardUseType")) && null != exchange.getProperty("oldSimCardId")) {
                msg.put("ReservPara", exchange.getProperty("oldSimCardId"));
            }
            // 白卡割接号卡中心开关,配置在ec-aop.props中,割接省份为1,在3GE代码中去掉查询地市的操作 wangmc 20171218
            String simCardSwitch = EcAopConfigLoader.getStr("ecaop.global.param.simcard.province");
            if (simCardSwitch.contains(String.valueOf(msg.get("province")))) {
                msg.put("simCardSwitch", "1");
            }
            lan.preData("ecaop.gdjk.cdqy.GdjkParamtersmapping", exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
            lan.xml2Json4qryCardData("ecaop.gdjk.cdqy.template", exchange);
        }
        else if ("cdaq".equals(methodCode)) {
            // 写卡数据查询-支持自动调拨接口做补换卡业务时,下发卡号给省份 by wangmc 20171115
            if ("1".equals(msg.get("cardUseType")) && null != exchange.getProperty("oldSimCardId")) {
                List<Map> para = new ArrayList<Map>();
                para.add(MapUtils.asMap("paraId", "oldSimCardId", "paraValue", exchange.getProperty("oldSimCardId")));
                msg.put("para", para);
            }
            lan.preData("ecaop.ota.cdaq.paramtersmapping", exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.esshttp.newsub");
            lan.xml2Json4qryCardData("ecaop.ota.cdaq.template", exchange);
        }
    }

}
