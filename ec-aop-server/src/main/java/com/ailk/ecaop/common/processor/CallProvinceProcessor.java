package com.ailk.ecaop.common.processor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.TransReqParamsMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("callProvince")
public class CallProvinceProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = exchange.getIn().getBody(Map.class);
        boolean isString = body.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) body.get("msg"));
        }
        else {
            msg = (Map) body.get("msg");
        }
        String province = String.valueOf(msg.get("province"));
        try {
            TransReqParamsMappingProcessor trpmp = new TransReqParamsMappingProcessor();
            trpmp.process(exchange);
            CallEngine.aopCall(exchange, "ecaop.comm.conf.url.province.aop" + "." + province);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", e.getMessage());
        }
        if (null == exchange.getOut()) {
            throw new EcAopServerBizException("9999", "调用省份固网订单查询未返回数据！");
        } else {
            String str = exchange.getOut().getBody().toString();
            Map resMap = JSON.parseObject(str);
            if (resMap.containsKey("code")) {
                String code = (String) resMap.get("code");
                String detail = (String) resMap.get("detail");
                throw new EcAopServerBizException(code, detail);
            }

            List<Map> orderInfo = (List<Map>) resMap.get("orderInfo");
            if (orderInfo != null && orderInfo.size() > 0) {
                for (Map orders : orderInfo) {
                    String tag = (String) orders.get("markingTag");
                    if (null == tag) {
                        orders.put("markingTag", "0");
                    }
                    if (orders.get("feeInfo") instanceof Map) {
                        Map feeInfo = (Map) orders.get("feeInfo");
                        if (feeInfo != null && feeInfo.size() > 0) {
                            feeInfo.put("payType", changePayType(feeInfo.get("payType")));
                            List fee = new ArrayList();
                            fee.add(feeInfo);
                            orders.put("feeInfo", fee);
                        }
                    }
                    else {
                        List<Map> feeInfo = (List<Map>) orders.get("feeInfo");
                        if (feeInfo != null && feeInfo.size() > 0) {
                            for (Map feeMap : feeInfo) {
                                feeMap.put("payType", changePayType(feeMap.get("payType")));
                            }
                            orders.put("feeInfo", feeInfo);
                        }
                    }

                    List<Map> broadBandTemplate = (List<Map>) orders.get("broadBandTemplate");
                    if (broadBandTemplate != null && broadBandTemplate.size() > 0) {
                        for (Map broadBandTemplateMap : broadBandTemplate) {
                            Map fee = (Map) broadBandTemplateMap.get("feeInfo");
                            if (fee != null && fee.size() > 0) {
                                fee.put("payType", changePayType(fee.get("payType")));
                                broadBandTemplateMap.put("feeInfo", fee);
                            }

                        }
                        orders.put("broadBandTemplate", broadBandTemplate);
                    }
                }
                exchange.getOut().setBody(resMap);
            }
        }

    }

    public static Object changePayType(Object payType) {
        Map changeMap = MapUtils.asMap("40", "D", "41", "E", "42", "F", "43", "H", "44", "I");
        Object retStr = changeMap.get(payType);
        return null == retStr ? payType : retStr;
    }
}
