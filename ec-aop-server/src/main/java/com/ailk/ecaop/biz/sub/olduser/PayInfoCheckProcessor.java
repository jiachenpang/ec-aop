package com.ailk.ecaop.biz.sub.olduser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.request.EcAopRequestParamException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.alibaba.fastjson.JSON;

@EcRocTag("PayInfoCheckProcessor")
public class PayInfoCheckProcessor extends BaseAopProcessor {

    private final String CASH_TYPE = "10";

    @Override
    public void process(Exchange exchange) throws Exception {
        Map<String, Object> body = exchange.getIn().getBody(Map.class);
        Map map = (Map) body.get("msg");
        if(map.get("payInfo") instanceof Map){
        boolean isString = map.get("payInfo") instanceof String;
        Map payInfoMap = new HashMap();
        if (isString) {
            payInfoMap = JSON.parseObject((String) map.get("payInfo"));
        }
        else {
            payInfoMap = (Map) map.get("payInfo");
        }
        if (!CASH_TYPE.equals(payInfoMap.get("payType"))) {
            if (null == payInfoMap.get("payOrg") || null == payInfoMap.get("payNum")) {
                throw new EcAopRequestParamException("当支付方式不为现金支付时，支付机构名称及支付账号都不能为空！");
            }
          }
        }else{
        List<Map> payInfos = (List<Map>) map.get("payInfo");
        for(Map payInfoMap : payInfos){
            if (!CASH_TYPE.equals(payInfoMap.get("payType"))) {
                if (null == payInfoMap.get("payOrg") || null == payInfoMap.get("payNum")) {
                    throw new EcAopRequestParamException("当支付方式不为现金支付时，支付机构名称及支付账号都不能为空！");
                }
              }
            }
        }
    }

}
