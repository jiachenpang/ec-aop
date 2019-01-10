package com.ailk.ecaop.biz.cust;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

@EcRocTag("FormatDateAndDealException4Gzt")
public class FormatDateAndDealException4Gzt extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {

        Map in = exchange.getIn().getBody(Map.class);
        Map reqMap = JSON.parseObject((String) in.get("Message"));
        Map svcIn = (Map) reqMap.get("svcCont");

        String checkCustName = svcIn.get("checkCustName").toString().replaceAll("　", "").replaceAll(" ", "").trim();
        String checkCustId = svcIn.get("checkCustId").toString();
        JSONObject out = (JSONObject) JSON.parse(exchange.getOut().getBody(String.class));
        boolean isString = out.get("rspmsg") instanceof String;
        Map rspMap = isString == true ? JSON.parseObject((String) out.get("rsmmsg")) : (Map) out.get("rspmsg");
        if (null == rspMap) {
            throw new EcAopServerBizException("9999", "AOP调用公安系统异常");
        }
        Map messageMap = (Map) rspMap.get("Message");
        if (null == messageMap) {
            throw new EcAopServerBizException("9999", "调用国政通系统异常.");
        }
        Map svcOut = (Map) messageMap.get("svcCont");
        if (null == svcOut) {
            throw new EcAopServerBizException("9999", "调用国政通系统异常,返回报文为空.");
        }
        String respCode = (String) svcOut.get("respCode");

        if (!"0000".equals(respCode)) {
            throw new EcAopServerBizException(respCode, (String) svcOut.get("respDesc"));
        }
        svcOut.put("checkCustName", checkCustName);
        svcOut.put("checkCustId", checkCustId);

        FormatDate(svcOut);
        exchange.getOut().setBody(JSON.toJSONString(out));
    }

    // 更改日期格式
    private void FormatDate(Map reqMap) throws Exception {
      String certTypeR = (String) reqMap.get("checkType");
        if (null != certTypeR && !"".equals(certTypeR) && "4B".equals(certTypeR)) {
            reqMap.put("checkType", "02");
        }
        else {
            reqMap.put("checkType", "01");
        }
        Object valid = reqMap.get("validityPeriod");
        Object birth = reqMap.get("birthday");
        if (null != valid && !"".equals(valid)) {
            reqMap.put("validityPeriod", dealDate(valid.toString()));
        }
        if (null != birth && !"".equals(birth)) {
            reqMap.put("birthday", dealDate(birth.toString()));
        }
    }

    // 时间格式优化
    private String dealDate(String dateStr) {
        dateStr = dateStr.replace(".", "");
        dateStr = dateStr.replace("-", "");
        dateStr = dateStr.replace("/", "");
        dateStr = dateStr.replace(" ", "");
        return dateStr;
    }

    public static void main(String[] args) throws Exception {
        FormatDateAndDealException4Gzt sAndDealException4Gzt = new FormatDateAndDealException4Gzt();
        Map aMap = new HashMap();
        aMap.put("validityPeriod", "1987.3.3");
        sAndDealException4Gzt.FormatDate(aMap);
        System.out.println(aMap.get("validityPeriod"));
    }

}
