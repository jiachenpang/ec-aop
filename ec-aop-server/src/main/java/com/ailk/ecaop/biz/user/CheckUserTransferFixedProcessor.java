package com.ailk.ecaop.biz.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.LanUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("checkUserTransferFixed")
public class CheckUserTransferFixedProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {

        LanUtils lan = new LanUtils();

        // 调用BSS

        try {
            lan.preData("ecaop.params.23t4.ParametersMapping", exchange);
            CallEngine.wsCall(exchange,
                    "ecaop.comm.conf.url.osn.services.UserTransferSer");
            lan.xml2Json("ecaop.trades.cbss.23To4.template", exchange);
            Map body = exchange.getOut().getBody(Map.class);
            List<Map> custInfos = (List) body.get("custInfo");
            if (custInfos != null && custInfos.size() != 0)
            {
                Map custInfo = custInfos.get(0);
                String certTypeCode = (String) custInfo.get("certTypeCode");
                // 若certTypecode不等于07.13.14.15.17.18时，不需要调集客，直接返回
                if (!("07,13,14,15,17,18,21".contains(certTypeCode))) {
                    List<Map> responseInfo = (List) body.get("requestInfo");

                    List<Map> responseInfos = new ArrayList<Map>();
                    for (Map responseIn : responseInfo) {
                        Map m = new HashMap();
                        m.put("respCode", responseIn.get("requestCode"));
                        m.put("detail", responseIn.get("requestDesc"));
                        responseInfos.add(m);
                    }
                    Map requestInfos = new HashMap();
                    requestInfos.put("responseInfo", responseInfos);
                    exchange.getOut().setBody(requestInfos);
                    return;
                }
            }
        }
        catch (EcAopServerBizException e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
        try {
            Map body = exchange.getOut().getBody(Map.class);
            List<Map> userInfos = (List) body.get("userInfo");

            Map userInfo = userInfos.get(0);
            String userId = (String) userInfo.get("userId");

            // 因为userid不是必返，若user为空，抛异常
            if (userId != null) {
                Map bodyIn = exchange.getIn().getBody((Map.class));
                Map msg = new HashMap();
                // 准备userId,放入in，然后调用集客
                if (bodyIn.get("msg") instanceof String) {
                    msg = JSON.parseObject((String) bodyIn.get("msg"));
                    msg.put("provinceUserId", userId);
                }
                bodyIn.put("msg", msg);
                exchange.getIn().setBody(bodyIn);

                lan.preData(
                        "ecaop.trade.cbss.CheckUserTransferParametersMapping",
                        exchange);
                CallEngine.wsCall(exchange,
                        "ecaop.comm.conf.url.osn.services.userChgCheck4GSer");
                lan.xml2Json("ecaop.trades.cbss.gpct.template", exchange);
                // 若code=0000或0001 表示该用户可以进行23G转4G
                String code = (String) ((Map) exchange.getOut().getBody())
                        .get("code");
                if ("0000".equals(code) || "0001".equals(code)) {
                    List<Map> responseInfo = (List) body.get("requestInfo");
                    List<Map> responseInfos = new ArrayList<Map>();
                    for (Map responseIn : responseInfo) {
                        Map m = new HashMap();
                        m.put("respCode", responseIn.get("requestCode"));
                        m.put("detail", responseIn.get("requestDesc"));
                        responseInfos.add(m);
                    }
                    Map requestInfos = new HashMap();
                    requestInfos.put("responseInfo", responseInfos);
                    exchange.getOut().setBody(requestInfos);

                }
            }

        }
        catch (EcAopServerBizException e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }

    }
}
