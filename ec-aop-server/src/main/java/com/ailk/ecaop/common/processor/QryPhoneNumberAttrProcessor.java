package com.ailk.ecaop.common.processor;

import java.util.HashMap;
import java.util.Map;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;

@EcRocTag("qryPhoneNumberArrt")
public class QryPhoneNumberAttrProcessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {

        Map body = (Map) exchange.getIn().getBody();
        Map msg = (Map) body.get("msg");
        Map REQ = new HashMap();
        REQ.put("STAFF_ID", "BELC3318");
        REQ.put("SERIAL_NUMBER", msg.get("number"));
        Map req = NumFaceHeadHelper.creatHead(exchange.getAppkey());
        req.put("UNI_BSS_BODY", MapUtils.asMap("CBSS_QRY_NUM_ROUTE_REQ", REQ));
        exchange.getIn().setBody(req);
        CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.microservice.cbssQryNumRoute");
        dealReturn(exchange, "CBSS_QRY_NUM_ROUTE_RSP", "号码路由服务");
    }

    public void dealReturn(Exchange exchange, String rspKey, String kind) {
        String rsp = exchange.getOut().getBody().toString();
        System.out.println("cb2.0能力共享平台返回toString：" + rsp);
        Map out = (Map) JSON.parse(rsp);
        Map UNI_BSS_HEAD = (Map) out.get("UNI_BSS_HEAD");
        if (null != UNI_BSS_HEAD) {
            String code = (String) UNI_BSS_HEAD.get("RESP_CODE");
            if (!"0000".equals(code) && !"00000".equals(code)) {
                throw new EcAopServerBizException("9999", UNI_BSS_HEAD.get("RESP_DESC") + "");
            }
        }
        else {
            throw new EcAopServerBizException("9999", "调cb2.0能力共享平台" + kind + "接口返回异常!");
        }
        Map UNI_BSS_BODY = (Map) out.get("UNI_BSS_BODY");
        if (null != UNI_BSS_BODY) {
            Map rspMap = (Map) UNI_BSS_BODY.get(rspKey);
            if ("0000".equals(rspMap.get("RESP_CODE"))) {
                // 处理返回参数
                Map outMap = new HashMap();
                String province = (String) rspMap.get("PROVINCE_CODE");
                String cityCode = (String) rspMap.get("CITY_CODE");
                String city = changeCity(cityCode);
                System.out.println("maly" + city);
                outMap.put("province", province);
                outMap.put("city", city.substring(2));
                exchange.getOut().setBody(outMap);
            }
            else {
                throw new EcAopServerBizException((String) rspMap.get("RESP_CODE"), "cb2.0能力共享平台" + kind + "接口返回："
                        + rspMap.get("RESP_DESC"));
            }
        }
        else {
            throw new EcAopServerBizException("9999", "调cb2.0能力共享平台" + kind + "接口返回异常!");
        }

    }

    /**
     * 微服务返回四位地市，AOP返回三位给外围系统
     * @param inMap
     * @return
     */
    public static String changeCity(String cityCode) {
        // 地市编码改为从redis配置中获取,配置在ec-aop.props by maly 180202
        Map cityCodeMap = (Map) JSON.parse(EcAopConfigLoader.getStr("ecaop.global.param.change.cityCode"));
        String city = (String) cityCodeMap.get(cityCode);
        return city;
    }

    public static void main(String[] args) {
        System.out.println(EcAopConfigLoader.getStr("ecaop.global.param.change.cityCode"));
    }
}
