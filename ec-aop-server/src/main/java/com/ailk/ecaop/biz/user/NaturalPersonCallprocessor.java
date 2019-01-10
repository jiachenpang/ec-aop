package com.ailk.ecaop.biz.user;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.faceheader.NumFaceHeadHelper;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.NumCenterUtils;
import com.alibaba.fastjson.JSON;
import org.apache.commons.lang.StringUtils;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.conf.EcAopConfigLoader;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 自然人调用接口
 * token生成规则与号卡中心相同
 */
public class NaturalPersonCallprocessor extends BaseAopProcessor {

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");
        String certType = (String) msg.get("certType");
        String certName = null == msg.get("certName") ? (String) msg.get("customerName") : (String) msg.get("certName");
        String certNum = (String) msg.get("certNum");
        String province = (String) msg.get("province");
        /**取值和调自然人判断逻辑要在前面，因为后面走校验接口会对参数做变动*/

        boolean flag = false;//是否调自然人开关 false为不去，true为去；
        //校验类型：0：证件校验 1：号码校验    号码校验时不调一证五户
        if ("1".equals(msg.get("checkType"))) {
            return;
            // 资料有一个为空时，不调自然人
        } else if (StringUtils.isEmpty(certName) || StringUtils.isEmpty(certNum) || StringUtils.isEmpty(certType)) {
            return;
        }

        // AppCode校验开关
        // 客户资料校验-返回多个cmck
        if ("cmck".equals(exchange.getMethodCode())) {
            String cmckProList = EcAopConfigLoader.getStr("ecaop.global.param.cmck.natural.person."
                    + exchange.getAppCode() + ".province");
            if ((StringUtils.isNotEmpty(cmckProList) && cmckProList.contains(province)) ||
                    EcAopConfigLoader.getStr("ecaop.global.param.cmck.natural.person.province").contains(province)) {
                flag = true;
            }
        } else if ("cuck".equals(exchange.getMethodCode())) {
            String cuckProvList = EcAopConfigLoader.getStr("ecaop.global.param.cuck.natural.person."
                    + exchange.getAppCode() + ".province");
            if ((StringUtils.isNotEmpty(cuckProvList) && cuckProvList.contains(province)) ||
                    EcAopConfigLoader.getStr("ecaop.global.param.cuck.natural.person.province").contains(province)) {
                flag = true;
            }
        }
        String userAmount = "00";
        if (flag) {
            Map requestBody = new HashMap();
            Map requestTempBody = new HashMap();
            try {
                // 改为使用创建调用自然人中心的报文头方法 by wangmc 20171104
                requestBody.putAll(NumFaceHeadHelper.creatRealPersonHead());
            } catch (Exception e) {
                e.printStackTrace();
            }
            requestTempBody.put("CHECK_TYPE", "0");
            requestTempBody.put("CERT_TYPE", CertTypeChangeUtils.certTypeMall2Fbs(certType));
            requestTempBody.put("CERT_NAME", certName);
            requestTempBody.put("CERT_NUM", certNum);
            Map resultMap = new HashMap();
            resultMap.put("QRY_USER_NUMBER_REQ", requestTempBody);
            requestBody.put("UNI_BSS_BODY", resultMap);
            exchange.getIn().setBody(requestBody);
            CallEngine.numCenterCall(exchange, "ecaop.comm.conf.url.cbss.naturalCenter");
            new NumCenterUtils().dealReturnHead(exchange);
            userAmount = dealReturnbody(exchange);
        }
        exchange.setProperty("userAmount", userAmount);
    }

    private String dealReturnbody(Exchange exchange) {
        String userAmount = "0";
        Map outMap = (Map) JSON.parse(exchange.getOut().getBody().toString());
        Map bodyMap = (Map) outMap.get("UNI_BSS_BODY");
        Map resultMap = (Map) bodyMap.get("QRY_USER_NUMBER_RSP");
        if (IsEmptyUtils.isEmpty(bodyMap) || IsEmptyUtils.isEmpty(resultMap)) {
            throw new EcAopServerBizException("9999", "自然人中心返回结果为空");
        }
        if ("0000".equals(resultMap.get("RESP_CODE"))) {
            List<Map> resourcesRsp = (List<Map>) resultMap.get("RESP_INFO");
            if (null != resourcesRsp && resourcesRsp.size() > 0) {
                for (Map resourceInfo : resourcesRsp) {
                    userAmount = (String) resourceInfo.get("USER_AMOUNT");
                    if (StringUtils.isEmpty(userAmount)) {
                        userAmount = "0";
                    }
                }
            }
        } else {
            throw new EcAopServerBizException("9999", "自然人中心返回其他错误," + resultMap.get("RESP_DESC") + "");
        }
        return userAmount;
    }
}
